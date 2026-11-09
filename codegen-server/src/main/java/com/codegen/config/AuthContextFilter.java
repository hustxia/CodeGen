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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(AuthContextFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CORS 预检不带 Authorization，必须放行，否则浏览器跨域调用全部失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // V5.0：SSE（EventSource）无法自定义请求头，允许 access_token 查询参数兜底。
        // 只在 header 缺失时生效，优先级仍是 Authorization 头
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            String queryToken = request.getParameter("access_token");
            if (StringUtils.hasText(queryToken)) {
                authorization = BEARER_PREFIX + queryToken;
            }
        }
        boolean hasToken = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX);

        if (hasToken) {
            String token = authorization.substring(BEARER_PREFIX.length());
            Long userId;
            String username;
            try {
                userId = jwtUtil.userIdOf(token);
                username = jwtUtil.usernameOf(token);
            } catch (Exception e) {
                // token 解析失败（过期 / 篡改 / 密钥变更）按未登录处理
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
            // 角色/部门/权限范围从库里查：写进 token 会因改权限要等 token 过期才生效
            loadAuthority(userId);
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

    /**
     * 填充当前用户的角色与数据权限范围。
     *
     * <p>查库失败（表结构未升级、DB 抖动）时按「最小权限」降级：
     * 角色为空 → 数据权限落 self，不会因为查不到就变成能看全量。
     */
    private void loadAuthority(Long userId) {
        try {
            List<String> roles = jdbcTemplate.queryForList(
                    "SELECT r.code FROM sys_role r "
                            + "JOIN sys_user_role ur ON ur.role_id = r.id AND ur.deleted = 0 "
                            + "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 1",
                    String.class, userId);
            UserContext.setRoles(roles);
            // 多角色时取最宽的范围：all > dept > self
            List<String> scopes = jdbcTemplate.queryForList(
                    "SELECT r.data_scope FROM sys_role r "
                            + "JOIN sys_user_role ur ON ur.role_id = r.id AND ur.deleted = 0 "
                            + "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 1",
                    String.class, userId);
            UserContext.setDataScope(widest(scopes));
            List<Long> deptIds = jdbcTemplate.queryForList(
                    "SELECT dept_id FROM sys_user WHERE id = ? AND deleted = 0", Long.class, userId);
            UserContext.setDeptId(deptIds.isEmpty() ? null : deptIds.get(0));
        } catch (Exception e) {
            log.warn("加载用户权限信息失败，按最小权限降级：{}", e.getMessage());
            UserContext.setRoles(List.of());
            UserContext.setDataScope("self");
        }
    }

    private String widest(List<String> scopes) {
        if (scopes.contains("all")) {
            return "all";
        }
        if (scopes.contains("dept")) {
            return "dept";
        }
        return "self";
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
