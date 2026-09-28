package com.s2admin.module.common.util;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.ResultCode;
import com.s2admin.module.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户,未登录时抛出 401 业务异常
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw BusinessException.unauthorized("未登录或登录已过期");
        }
        return loginUser;
    }

    public static Long getUserId() {
        return getLoginUser().getId();
    }

    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 获取当前登录用户,未登录返回 null
     */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    /**
     * 当前用户是否拥有指定权限码(SUPER_ADMIN / * 通配)
     */
    public static boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.getRoles() != null
                && loginUser.getRoles().contains(com.s2admin.module.security.UserPermissionService.SUPER_ADMIN)) {
            return true;
        }
        if (loginUser.getPermissions() == null) {
            return false;
        }
        return loginUser.getPermissions().contains("*")
                || loginUser.getPermissions().contains(permission);
    }

    public static int unauthorizedCode() {
        return ResultCode.UNAUTHORIZED;
    }
}
