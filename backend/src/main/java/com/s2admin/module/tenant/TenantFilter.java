package com.s2admin.module.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.common.Result;
import com.s2admin.module.common.ResultCode;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.repository.SysTenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 超级管理员默认看全部数据,请求头 X-Tenant-Id 可收窄到一个租户。
 * 其他用户锁定在自己的 tenantId 上。
 */
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final UserPermissionService permissionService;
    private final SysTenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            LoginUser user = SecurityUtils.getLoginUserOrNull();
            if (user != null) {
                boolean platform = isPlatform(user);
                if (platform) {
                    String header = request.getHeader("X-Tenant-Id");
                    if (StringUtils.hasText(header)) {
                        long id;
                        try {
                            id = Long.parseLong(header.trim());
                        } catch (NumberFormatException e) {
                            write(response, "X-Tenant-Id 不正确");
                            return;
                        }
                        boolean active = tenantRepository.findById(id)
                                .filter(item -> item.getStatus() == null || item.getStatus() == 0)
                                .isPresent();
                        if (!active) {
                            write(response, "租户不存在或已停用");
                            return;
                        }
                        TenantContext.set(id);
                    }
                } else {
                    Long own = permissionService.tenantId(user.getId());
                    TenantContext.set(own == null ? -1L : own);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isPlatform(LoginUser user) {
        if (user.getRoles() != null && user.getRoles().contains(UserPermissionService.SUPER_ADMIN)) {
            return true;
        }
        return user.getPermissions() != null && user.getPermissions().contains("*");
    }

    private void write(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ResultCode.BAD_REQUEST, message)));
    }
}
