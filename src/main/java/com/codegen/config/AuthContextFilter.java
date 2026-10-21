package com.codegen.config;

import com.codegen.util.JwtUtil;
import com.codegen.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录上下文填充：从 Authorization 头解析出用户写入 {@link UserContext}。
 *
 * <p>只填充不拦截（鉴权仍由网关负责），这样审计字段填充、写盘历史里的操作人能拿到真人。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length());
            try {
                UserContext.setUserId(jwtUtil.userIdOf(token));
                UserContext.setUsername(jwtUtil.usernameOf(token));
            } catch (Exception ignored) {
                // token 无效时不填充上下文，交由后续接口自行返回 401
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
