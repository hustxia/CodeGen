package com.codegen.common.log;

import com.codegen.common.context.UserContext;
import com.codegen.entity.SysOperLog;
import com.codegen.mapper.SysOperLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 操作日志切面：包住标了 {@link Log} 的方法，成功后异步落库，失败也会记录。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li>日志写失败绝不能影响主流程——否则加个日志反而让业务接口 500</li>
 *   <li>参数落库前按字段名做脱敏，password/token 这类绝不能进日志表</li>
 *   <li>超长内容截断，避免一条日志把 mediumtext 撑满</li>
 * </ul>
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /**
     * 单字段上限：够定位问题，又不至于把整表塞满
     */
    private static final int MAX_TEXT = 2000;

    /**
     * 敏感字段的「键 + 值」整对匹配。
     *
     * <p>必须连值一起吃掉：只匹配字段名再拼回去，会得到
     * {@code {"password":"****":"root123"}}——值还在，JSON 还坏了。
     * 值分两种：带引号的字符串（允许内部转义）与裸值（数字/布尔/null）。
     */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)\"(password|passwd|pwd|token|secret|authorization|credential)\"\\s*:\\s*"
                    + "(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s]+)");

    @Autowired
    private SysOperLogMapper operLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IpRegionResolver ipRegionResolver;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint point, Log operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String errorMsg = null;
        Object result = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            try {
                save(point, operLog, result, errorMsg, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 记录日志失败不能反向影响业务
                log.warn("操作日志写入失败：{}", e.getMessage());
            }
        }
    }

    private void save(ProceedingJoinPoint point, Log operLog, Object result,
                      String errorMsg, long costMs) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(operLog.title());
        entity.setStatus(errorMsg == null ? 1 : 0);
        entity.setErrorMsg(truncate(errorMsg));
        entity.setCostMs(costMs);
        entity.setOperTime(LocalDateTime.now());
        entity.setOperUserId(UserContext.getUserId());
        entity.setOperName(UserContext.getUsername());

        MethodSignature signature = (MethodSignature) point.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        entity.setMethod(className + "#" + signature.getName());

        HttpServletRequest request = currentRequest();
        String ip = null;
        if (request != null) {
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            ip = clientIp(request);
            entity.setOperIp(ip);
        }
        entity.setOperType(inferType(operLog.type(), request, signature.getName()));

        if (operLog.saveParam()) {
            entity.setOperParam(truncate(toJson(sanitize(point.getArgs()))));
        }
        if (operLog.saveResult() && result != null) {
            entity.setJsonResult(truncate(toJson(result)));
        }
        operLogMapper.insert(entity);

        // 省市由 IP 反查：审计时只看 IP 判断不了地域，但查询要走网络，
        // 必须异步回填，否则每条日志都让业务接口慢一次网络往返
        if (ip != null) {
            Long logId = entity.getId();
            ipRegionResolver.resolveAsync(ip, region -> {
                SysOperLog patch = new SysOperLog();
                patch.setId(logId);
                patch.setProvince(region.province());
                patch.setCity(region.city());
                operLogMapper.updateById(patch);
            });
        }
    }

    /**
     * 参数里可能有文件流、Servlet 对象，直接序列化会抛异常或写出无意义内容，
     * 这些统一替换成类型占位。
     */
    private List<Object> sanitize(Object[] args) {
        List<Object> list = new ArrayList<>();
        if (args == null) {
            return list;
        }
        for (Object arg : args) {
            if (arg instanceof MultipartFile file) {
                list.add("<file:" + file.getOriginalFilename() + ", " + file.getSize() + "B>");
            } else if (arg instanceof MultipartFile[] files) {
                list.add("<files:" + files.length + ">");
            } else if (arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                list.add("<servlet>");
            } else {
                list.add(arg);
            }
        }
        return list;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return maskSensitive(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 按字段名脱敏。只换值不换键：正常业务文本里出现 "password" 三个字不会被误伤。
     *
     * <p>独立成静态方法是为了能直接单测——脱敏失效是「看起来有、其实没有」，
     * 只在接口上肉眼验一遍很容易漏（见 audit A-01）。
     */
    static String maskSensitive(String json) {
        if (json == null) {
            return null;
        }
        return SENSITIVE.matcher(json).replaceAll(m -> "\"" + m.group(1) + "\":\"****\"");
    }

    /**
     * 未显式声明类型时按 HTTP 方法与方法名推断，避免调用方每处都写一遍。
     */
    private String inferType(String declared, HttpServletRequest request, String methodName) {
        if (declared != null && !declared.isBlank()) {
            return declared.toUpperCase(Locale.ROOT);
        }
        String m = methodName.toLowerCase(Locale.ROOT);
        if (m.contains("import")) {
            return "IMPORT";
        }
        if (m.contains("export")) {
            return "EXPORT";
        }
        if (request == null) {
            return "OTHER";
        }
        return switch (request.getMethod().toUpperCase(Locale.ROOT)) {
            case "POST" -> "INSERT";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "OTHER";
        };
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        for (String header : Arrays.asList("X-Forwarded-For", "X-Real-IP")) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // 多级代理时第一个才是客户端
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "...(truncated)" : text;
    }
}
