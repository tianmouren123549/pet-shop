package com.gzu.petshop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 校验 {@code Authorization: Bearer &lt;jwt&gt;}；公开接口放行。密码不经过本过滤器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_ROLE = "jwtRole";
    public static final String ATTR_PRINCIPAL_ID = "jwtPrincipalId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅保护 /api/**；/uploads、错误页等不走本过滤器逻辑
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublic(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "请先登录或携带 Authorization: Bearer <token>"));
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "令牌为空"));
            return;
        }

        final Claims claims;
        try {
            claims = jwtService.parseClaims(token);
        } catch (ExpiredJwtException e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "令牌已过期，请重新登录"));
            return;
        } catch (JwtException e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "令牌无效"));
            return;
        }

        String role = claims.get(JwtService.CLAIM_ROLE, String.class);
        if (role == null || role.isBlank()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "令牌缺少角色信息"));
            return;
        }

        if (!roleAllowedForPath(role, path)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.error(403, "当前身份无权访问该接口"));
            return;
        }

        String sub = claims.getSubject();
        try {
            long principalId = Long.parseLong(sub);
            request.setAttribute(ATTR_PRINCIPAL_ID, principalId);
        } catch (NumberFormatException ignored) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "令牌主体无效"));
            return;
        }
        request.setAttribute(ATTR_ROLE, role);

        filterChain.doFilter(request, response);
    }

    private static boolean isPublic(String path, String method) {
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/api/merchant/auth/")) {
            return true;
        }
        if (path.startsWith("/api/admin/auth/")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            if (path.startsWith("/api/products")) {
                return true;
            }
            if (path.startsWith("/api/categories")) {
                return true;
            }
            if (path.startsWith("/api/reviews/product/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 管理端接口须 SUPER/OPERATOR；商家端须 MERCHANT；其余受保护接口须 USER。
     */
    private static boolean roleAllowedForPath(String role, String path) {
        if (path.startsWith("/api/admin/") && !path.startsWith("/api/admin/auth/")) {
            return "SUPER".equals(role) || "OPERATOR".equals(role);
        }
        if (path.startsWith("/api/merchant/") && !path.startsWith("/api/merchant/auth/")) {
            return "MERCHANT".equals(role);
        }
        return "USER".equals(role);
    }

    private void writeJson(HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
