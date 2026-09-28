package com.s2admin.module.auth.service;

import com.s2admin.module.auth.form.ChangePasswordForm;
import com.s2admin.module.auth.form.ForgotPasswordForm;
import com.s2admin.module.auth.form.LoginForm;
import com.s2admin.module.auth.form.ProfileForm;
import com.s2admin.module.auth.form.ResetPasswordForm;
import com.s2admin.module.auth.vo.LoginVO;
import com.s2admin.module.auth.vo.SessionVO;
import com.s2admin.module.auth.vo.UserInfoVO;
import com.s2admin.module.common.ResultCode;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.ClientIpResolver;
import com.s2admin.module.common.util.PasswordPolicy;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.security.JwtTokenProvider;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.RateLimitService;
import com.s2admin.module.security.SessionService;
import com.s2admin.module.security.TokenBlacklistService;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.service.ConfigService;
import com.s2admin.module.system.service.MenuService;
import com.s2admin.module.system.vo.MenuVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证服务:登录 / 登出 / 刷新 / 用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserPermissionService permissionService;
    private final TokenBlacklistService tokenBlacklistService;
    private final MenuService menuService;
    private final CaptchaService captchaService;
    private final ConfigService configService;
    private final CacheStore cacheStore;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final MailCodeService mailCodeService;
    private final SessionService sessionService;
    private final LoginAccountService loginAccountService;

    public LoginVO login(LoginForm form, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        rateLimitService.assertNotLocked("s2admin:login:ip-lock:" + ip, "当前 IP 登录失败次数过多,请稍后再试");
        rateLimitService.assertAllowed(
                "s2admin:login:ip-req:" + ip,
                30,
                Duration.ofMinutes(1),
                "登录请求过于频繁,请稍后再试");
        captchaService.verify(form.getCaptchaKey(), form.getCaptcha());
        SysUser user = loginAccountService.findForLogin(form.getUsername());
        if (user == null || !passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            onLoginFailed(user, form.getUsername(), ip);
            throw new BadCredentialsException("用户名或密码错误");
        }
        Long lockUntil = user.getLockUntil();
        if (lockUntil != null && System.currentTimeMillis() < lockUntil) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被锁定");
        }
        if (lockUntil != null && loginAccountService.releaseExpiredAutoLock(user.getId())) {
            user.setStatus(0);
            user.setLockUntil(null);
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被锁定");
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        clearFailCount(form.getUsername());
        rateLimitService.unlock("s2admin:login:ip-fail:" + ip);
        return issueLogin(user, request, Boolean.TRUE.equals(form.getRememberMe()));
    }

    /** 密码登录与第三方登录共用的发证入口。调用前必须已确认账号可登录。 */
    public LoginVO issueLogin(SysUser user, HttpServletRequest request, boolean rememberMe) {
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, statusMessage(user.getStatus()));
        }
        Set<String> roles = user.getRoles().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 0)
                .map(SysRole::getCode)
                .collect(Collectors.toSet());
        String sid = UUID.randomUUID().toString();
        String ip = request == null ? "" : clientIpResolver.resolve(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        LoginVO vo = new LoginVO();
        vo.setToken(tokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles, sid));
        vo.setRefreshToken(tokenProvider.generateRefreshToken(user.getId(), user.getUsername(), rememberMe, sid));
        vo.setExpiresIn(tokenProvider.getAccessExpiration() / 1000);
        vo.setUser(buildUserInfo(user));
        sessionService.register(user.getId(), sid, ip, ua);
        return vo;
    }

    public void logout(HttpServletRequest request, String refreshToken) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String access = header.substring(7);
            tokenBlacklistService.blacklist(access);
            try {
                Claims accessClaims = tokenProvider.parseToken(access);
                sessionService.remove(tokenProvider.getUserId(accessClaims), tokenProvider.getSid(accessClaims));
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(refreshToken)) {
            tokenBlacklistService.blacklist(refreshToken);
        }
    }

    @Transactional(readOnly = true)
    public LoginVO refresh(String refreshToken, HttpServletRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parseToken(refreshToken);
        } catch (Exception e) {
            throw BusinessException.unauthorized("refreshToken 无效或已过期");
        }
        if (!JwtTokenProvider.TYPE_REFRESH.equals(tokenProvider.getType(claims))) {
            throw BusinessException.unauthorized("refreshToken 类型不正确");
        }
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw BusinessException.unauthorized("refreshToken 已失效,请重新登录");
        }
        Long userId = tokenProvider.getUserId(claims);
        String sid = tokenProvider.getSid(claims);
        if (!sessionService.isActive(userId, sid)) {
            throw BusinessException.unauthorized("登录已在其他设备下线,请重新登录");
        }
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在或已被删除"));
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, statusMessage(user.getStatus()));
        }
        String jti = claims.getId();
        if (jti != null && claims.getExpiration() != null) {
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttl <= 0 || !cacheStore.setIfAbsent("s2admin:jwt:refresh-used:" + jti, "1",
                    java.time.Duration.ofMillis(Math.max(ttl, 1)))) {
                throw BusinessException.unauthorized("refreshToken 已失效,请重新登录");
            }
        }
        tokenBlacklistService.blacklist(refreshToken);
        Set<String> roles = user.getRoles().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 0)
                .map(SysRole::getCode)
                .collect(Collectors.toSet());
        boolean rememberMe = tokenProvider.isRememberMe(claims);
        if (!StringUtils.hasText(sid)) {
            sid = UUID.randomUUID().toString();
        }
        LoginVO vo = new LoginVO();
        String access = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles, sid);
        vo.setToken(access);
        vo.setRefreshToken(tokenProvider.generateRefreshToken(user.getId(), user.getUsername(), rememberMe, sid));
        vo.setExpiresIn(tokenProvider.getAccessExpiration() / 1000);
        vo.setUser(buildUserInfo(user));
        String ip = request == null ? "" : clientIpResolver.resolve(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        sessionService.register(user.getId(), sid, ip, ua);
        return vo;
    }

    @Transactional(readOnly = true)
    public UserInfoVO getUserInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在或已被删除"));
        return buildUserInfo(user);
    }

    @Transactional(readOnly = true)
    public List<MenuVO> getUserMenus() {
        return menuService.getUserMenus(SecurityUtils.getUserId());
    }

    @Transactional
    public UserInfoVO updateProfile(ProfileForm form) {
        SysUser user = userRepository.findById(SecurityUtils.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在或已被删除"));
        if (StringUtils.hasText(form.getEmail()) && userRepository.existsByEmailIgnoreCase(form.getEmail())
                && (user.getEmail() == null || !form.getEmail().equalsIgnoreCase(user.getEmail()))) {
            throw new BusinessException("邮箱已被使用");
        }
        if (StringUtils.hasText(form.getPhone()) && userRepository.existsByPhone(form.getPhone())
                && !form.getPhone().equals(user.getPhone())) {
            throw new BusinessException("手机号已被使用");
        }
        user.setNickname(form.getNickname());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setAvatar(form.getAvatar());
        user.setProvince(form.getProvince());
        user.setCity(form.getCity());
        user.setDistrict(form.getDistrict());
        userRepository.save(user);
        return buildUserInfo(user);
    }

    @Transactional
    public void changePassword(ChangePasswordForm form) {
        SysUser user = userRepository.findById(SecurityUtils.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在或已被删除"));
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (form.getOldPassword().equals(form.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        PasswordPolicy.validate(form.getNewPassword());
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        user.setPwdReset(0);
        userRepository.save(user);
        permissionService.evict(user.getId());
        tokenBlacklistService.invalidateUser(user.getId());
    }

    public Map<String, String> forgotPassword(ForgotPasswordForm form) {
        captchaService.verify(form.getCaptchaKey(), form.getCaptcha());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", "若账号存在且已绑定邮箱,验证码将在有效期内可用");
        SysUser user = resolveAccount(form.getAccount());
        if (user != null && StringUtils.hasText(user.getEmail())) {
            String code = mailCodeService.sendResetCode(user.getUsername(), user.getEmail());
            if (code != null) {
                result.put("mockCode", code);
            }
        }
        return result;
    }

    @Transactional
    public void resetPassword(ResetPasswordForm form) {
        SysUser user = resolveAccount(form.getAccount());
        String verifyKey = user != null
                ? user.getUsername()
                : "__missing__:" + form.getAccount().trim().toLowerCase();
        mailCodeService.verify(verifyKey, form.getCode());
        PasswordPolicy.validate(form.getNewPassword());
        if (user == null) {
            throw new BusinessException("验证码错误或已过期");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        user.setPwdReset(0);
        userRepository.save(user);
        permissionService.evict(user.getId());
        tokenBlacklistService.invalidateUser(user.getId());
    }

    public List<SessionVO> mySessions() {
        LoginUser user = SecurityUtils.getLoginUser();
        String current = user.getSid();
        return sessionService.list(user.getId()).stream().map(d -> {
            SessionVO vo = new SessionVO();
            vo.setSid(d.sid());
            vo.setIat(d.iat());
            vo.setIp(d.ip());
            vo.setUa(d.ua());
            vo.setCurrent(current != null && current.equals(d.sid()));
            return vo;
        }).toList();
    }

    public void kickSession(String sid) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (StringUtils.hasText(sid) && sid.equals(user.getSid())) {
            throw new BusinessException("不能下线当前设备,请使用退出登录");
        }
        sessionService.remove(user.getId(), sid);
    }

    public void kickOtherSessions() {
        LoginUser user = SecurityUtils.getLoginUser();
        sessionService.keepOnly(user.getId(), user.getSid());
    }

    private UserInfoVO buildUserInfo(SysUser user) {
        List<String> roles = user.getRoles().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 0)
                .map(SysRole::getCode)
                .sorted()
                .toList();
        List<String> permissions = permissionService.loadPermissions(user.getId()).stream().sorted().toList();
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setProvince(user.getProvince());
        vo.setCity(user.getCity());
        vo.setDistrict(user.getDistrict());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        vo.setMustChangePassword(Integer.valueOf(1).equals(user.getPwdReset()));
        vo.setTenantId(user.getTenantId());
        return vo;
    }

    private SysUser resolveAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String value = account.trim();
        return userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmailIgnoreCase(value))
                .or(() -> userRepository.findByPhone(value))
                .orElse(null);
    }

    private void onLoginFailed(SysUser user, String username, String ip) {
        int threshold = Math.max(1, configService.getInt("sys.account.lockThreshold", 5));
        int duration = Math.max(1, configService.getInt("sys.account.lockDuration", 30));
        int ipWindow = Math.max(1, configService.getInt("sys.account.ipFailWindow", 5));
        int ipLock = Math.max(1, configService.getInt("sys.account.ipLockDuration", 15));
        try {
            String key = "s2admin:login:fail:" + username.toLowerCase();
            long count = cacheStore.increment(key);
            cacheStore.expire(key, Duration.ofMinutes(duration));
            if (count >= threshold && user != null) {
                loginAccountService.armAutoLock(user.getId(), Duration.ofMinutes(duration));
                tokenBlacklistService.invalidateUser(user.getId());
            }
        } catch (Exception e) {
            log.debug("记录登录失败次数失败: {}", e.getMessage());
        }
        if (StringUtils.hasText(ip)) {
            long ipFails = rateLimitService.increment("s2admin:login:ip-fail:" + ip, Duration.ofMinutes(ipWindow));
            if (ipFails >= threshold) {
                rateLimitService.lock("s2admin:login:ip-lock:" + ip, Duration.ofMinutes(ipLock));
            }
        }
    }

    private void clearFailCount(String username) {
        try {
            cacheStore.delete("s2admin:login:fail:" + username.toLowerCase());
        } catch (Exception ignored) {
        }
    }

    private String statusMessage(Integer status) {
        return switch (status) {
            case 1 -> "账号已被禁用";
            case 2 -> "账号已被锁定";
            case 3 -> "账号已过期";
            default -> "账号状态异常";
        };
    }

}