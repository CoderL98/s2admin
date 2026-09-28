package com.s2admin.module.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.common.Result;
import com.s2admin.module.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 认证过滤器
 * 解析 Authorization: Bearer <token>,构建 SecurityContext
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserPermissionService permissionService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = tokenProvider.parseToken(token);
                if (!JwtTokenProvider.TYPE_ACCESS.equals(tokenProvider.getType(claims))
                        || tokenBlacklistService.isBlacklisted(token)) {
                    SecurityContextHolder.clearContext();
                } else {
                    Long userId = tokenProvider.getUserId(claims);
                    Set<String> permissions = permissionService.loadPermissions(userId);
                    Set<String> roles = permissionService.loadRoles(userId);
                    String sid = tokenProvider.getSid(claims);
                    if (!sessionService.isActive(userId, sid)) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    Integer status = permissionService.accountStatus(userId);
                    if (status == null || status != 0) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    LoginUser loginUser = new LoginUser(userId, claims.getSubject(), null,
                            roles, permissions);
                    loginUser.setMustChangePassword(permissionService.mustChangePassword(userId));
                    loginUser.setJti(claims.getId());
                    loginUser.setSid(sid);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    if (Boolean.TRUE.equals(loginUser.getMustChangePassword())
                            && !passwordChangeAllowed(request)) {
                        denyMustChangePassword(response);
                        return;
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean passwordChangeAllowed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (uri.startsWith("/api/auth/password") && "PUT".equalsIgnoreCase(method)) {
            return true;
        }
        if (uri.startsWith("/api/auth/info") || uri.startsWith("/api/auth/menus")
                || uri.startsWith("/api/auth/logout") || uri.startsWith("/api/auth/refresh")) {
            return true;
        }
        return false;
    }

    private void denyMustChangePassword(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.FORBIDDEN, "请先修改初始密码")));
    }
}
