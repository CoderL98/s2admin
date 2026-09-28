package com.s2admin.module.auth.controller;

import com.s2admin.module.auth.form.ChangePasswordForm;
import com.s2admin.module.auth.form.ForgotPasswordForm;
import com.s2admin.module.auth.form.LoginForm;
import com.s2admin.module.auth.form.ProfileForm;
import com.s2admin.module.auth.form.RefreshForm;
import com.s2admin.module.auth.form.ResetPasswordForm;
import com.s2admin.module.auth.service.AuthService;
import com.s2admin.module.auth.service.CaptchaService;
import com.s2admin.module.auth.vo.CaptchaVO;
import com.s2admin.module.auth.vo.LoginVO;
import com.s2admin.module.auth.vo.UserInfoVO;
import com.s2admin.module.common.Result;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.ClientIpResolver;
import com.s2admin.module.security.RateLimitService;
import com.s2admin.module.system.service.FileService;
import com.s2admin.module.system.service.LoginLogService;
import com.s2admin.module.system.vo.FileVO;
import com.s2admin.module.system.vo.MenuVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final FileService fileService;
    private final com.s2admin.module.system.service.LogService logService;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha(HttpServletRequest request) {
        rateLimitService.assertAllowed(
                "s2admin:rl:captcha:" + clientIpResolver.resolve(request),
                20,
                Duration.ofMinutes(1),
                "验证码请求过于频繁,请稍后再试");
        return Result.success(captchaService.create());
    }

    @PostMapping("/forgot-password")
    public Result<java.util.Map<String, String>> forgotPassword(@RequestBody @Valid ForgotPasswordForm form,
                                                               HttpServletRequest request) {
        rateLimitService.assertAllowed(
                "s2admin:rl:forgot:" + clientIpResolver.resolve(request),
                5,
                Duration.ofMinutes(10),
                "重置请求过于频繁,请稍后再试");
        return Result.success(authService.forgotPassword(form));
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordForm form, HttpServletRequest request) {
        rateLimitService.assertAllowed(
                "s2admin:rl:reset:" + clientIpResolver.resolve(request),
                10,
                Duration.ofMinutes(10),
                "重置请求过于频繁,请稍后再试");
        authService.resetPassword(form);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginForm form, HttpServletRequest request) {
        // 登录日志在事务外写入:SQLite 单写者 + 池=1 下 REQUIRES_NEW 无法工作,
        // 且在 controller 层写入可避免失败时被登录事务回滚
        try {
            LoginVO vo = authService.login(form, request);
            Long userId = vo.getUser() == null ? null : vo.getUser().getId();
            loginLogService.record(userId, form.getUsername(), request, 0, "登录成功");
            return Result.success(vo);
        } catch (BusinessException e) {
            loginLogService.record(null, form.getUsername(), request, 1, e.getMessage());
            throw e;
        } catch (BadCredentialsException e) {
            loginLogService.record(null, form.getUsername(), request, 1, "用户名或密码错误");
            throw e;
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, @RequestBody(required = false) RefreshForm form) {
        authService.logout(request, form == null ? null : form.getRefreshToken());
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody @Valid RefreshForm form, HttpServletRequest request) {
        return Result.success(authService.refresh(form.getRefreshToken(), request));
    }

    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.success(authService.getUserInfo());
    }

    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        return Result.success(authService.getUserMenus());
    }

    @PutMapping("/profile")
    public Result<UserInfoVO> updateProfile(@RequestBody @Valid ProfileForm form) {
        return Result.success(authService.updateProfile(form));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordForm form) {
        authService.changePassword(form);
        return Result.success();
    }

    @GetMapping("/sessions")
    public Result<List<com.s2admin.module.auth.vo.SessionVO>> sessions() {
        return Result.success(authService.mySessions());
    }

    @DeleteMapping("/sessions/others")
    public Result<Void> kickOthers() {
        authService.kickOtherSessions();
        return Result.success();
    }

    @DeleteMapping("/sessions/{sid}")
    public Result<Void> kickSession(@PathVariable String sid) {
        authService.kickSession(sid);
        return Result.success();
    }

    @GetMapping("/my-login-logs")
    public Result<com.s2admin.module.common.PageResult<com.s2admin.module.system.entity.LoginLog>> myLoginLogs(
            com.s2admin.module.system.form.LogQuery query) {
        return Result.success(logService.myLoginLogs(query));
    }

    @GetMapping("/my-operations")
    public Result<com.s2admin.module.common.PageResult<com.s2admin.module.system.entity.OperationLog>> myOperations(
            com.s2admin.module.system.form.LogQuery query) {
        return Result.success(logService.myOperations(query));
    }

    @PostMapping("/avatar")
    public Result<FileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadAvatar(file));
    }
}
