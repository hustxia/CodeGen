package com.codegen.config;

import com.codegen.common.context.UserContext;
import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import com.codegen.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 登录上下文填充 + 鉴权拦截。
 *
 * <p>本服务是单体服务，前面没有网关兜底，因此鉴权必须在这里自己完成：
 * 除白名单外的请求若没有合法 token，直接返回 401。
 *
 * <p>白名单只放行"拿 token 之前必须能访问"的接口（登录、健康检查、接口文档），
 * 其余业务接口一律要求 Bearer token。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 无需 token 即可访问的路径前缀（与前端登录页、探活、接口文档相关） */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/health",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/error"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS 预检不带 Authorization，必须放行，否则浏览器跨域调用全部失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        boolean hasToken = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX);

        if (hasToken) {
            String token = authorization.substring(BEARER_PREFIX.length());
            try {
                UserContext.setUserId(jwtUtil.userIdOf(token));
                UserContext.setUsername(jwtUtil.usernameOf(token));
            } catch (Exception e) {
                // token 解析失败（过期 / 篡改 / 密钥变更）按未登录处理
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
            return;
        }

        // 无 token：白名单放行，其余 401
        if (isWhiteListed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        writeUnauthorized(response, "未登录或缺少访问令牌");
    }

    private boolean isWhiteListed(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (String prefix : WHITE_LIST) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一以 {@link Result} 结构返回 401，前端 axios 拦截器按 code=401 弹重新登录。
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED.getCode(), msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
