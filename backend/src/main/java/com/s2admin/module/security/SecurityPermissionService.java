package com.s2admin.module.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 方法级权限表达式,供 {@code @PreAuthorize("@ss.hasPermission('code')")} 使用。
 * SUPER_ADMIN 与权限码 {@code *} 视为拥有全部权限。
 */
@Component("ss")
public class SecurityPermissionService {

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof LoginUser user)) {
            return false;
        }
        if (user.getPermissions() != null
                && (user.getPermissions().contains("*") || user.getPermissions().contains(permission))) {
            return true;
        }
        return user.getRoles() != null && user.getRoles().contains(UserPermissionService.SUPER_ADMIN);
    }
}
