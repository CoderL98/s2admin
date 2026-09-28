package com.s2admin.module.auth.service;

import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录读用户、自动锁定单独提交。
 * 不能放在登录事务里:失败抛错会把 status=2 回滚,而 SQLite 单连接也无法 REQUIRES_NEW。
 * 解锁时间写在用户表,避免进程重启后把临时锁定变成永久锁定。
 */
@Service
@RequiredArgsConstructor
public class LoginAccountService {

    private final SysUserRepository userRepository;
    private final UserPermissionService permissionService;

    @Transactional(readOnly = true)
    public SysUser findForLogin(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String value = account.trim();
        SysUser user = userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmailIgnoreCase(value))
                .or(() -> userRepository.findByPhone(value))
                .orElse(null);
        if (user != null && user.getRoles() != null) {
            for (SysRole role : user.getRoles()) {
                role.getCode();
                role.getStatus();
            }
        }
        return user;
    }

    @Transactional
    public void armAutoLock(Long userId, Duration duration) {
        if (userId == null) {
            return;
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            user.setStatus(2);
        }
        user.setLockUntil(System.currentTimeMillis() + Math.max(duration.toMillis(), 1));
        userRepository.save(user);
        permissionService.evict(userId);
    }

    /** 自动锁定期已过则清截止时间,并把因此锁定的账号恢复为正常。管理员锁定没有截止时间,不会被解开。 */
    @Transactional
    public boolean releaseExpiredAutoLock(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getLockUntil() == null) {
            return false;
        }
        if (System.currentTimeMillis() < user.getLockUntil()) {
            return false;
        }
        user.setLockUntil(null);
        if (Integer.valueOf(2).equals(user.getStatus())) {
            user.setStatus(0);
        }
        userRepository.save(user);
        permissionService.evict(userId);
        return true;
    }

    @Transactional
    public void clearAutoLock(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getLockUntil() != null) {
            user.setLockUntil(null);
            userRepository.save(user);
        }
    }
}
