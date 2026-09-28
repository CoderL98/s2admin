package com.s2admin.module.auth.controller;

import com.s2admin.module.auth.service.OAuthService;
import com.s2admin.module.auth.vo.LoginVO;
import com.s2admin.module.common.Result;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.service.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;
    private final LoginLogService loginLogService;

    @GetMapping("/providers")
    public Result<List<Map<String, String>>> providers() {
        return Result.success(oauthService.providers());
    }

    @GetMapping("/{provider}/authorize")
    public void authorize(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            send(response, oauthService.authorizeUrl(provider, request));
        } catch (Exception e) {
            send(response, oauthService.failureRedirect(oauthService.safeMessage(e)));
        }
    }

    @RequestMapping(value = "/{provider}/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public void callback(@PathVariable String provider,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        try {
            if (StringUtils.hasText(error) || !StringUtils.hasText(code)) {
                throw new BusinessException("已取消或未完成第三方授权");
            }
            LoginVO vo = oauthService.callback(provider, code, state, request);
            Long userId = vo.getUser() == null ? null : vo.getUser().getId();
            String username = vo.getUser() == null ? provider : vo.getUser().getUsername();
            loginLogService.record(userId, username, request, 0, provider + " 登录成功");
            send(response, oauthService.successRedirect(vo));
        } catch (Exception e) {
            String message = oauthService.safeMessage(e);
            loginLogService.record(null, provider, request, 1, message);
            send(response, oauthService.failureRedirect(message));
        }
    }

    private void send(HttpServletResponse response, String url) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            response.setStatus(400);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("第三方登录跳转未配置");
            return;
        }
        response.sendRedirect(url);
    }
}
