package com.s2admin.module.auth.service;

import com.s2admin.module.auth.vo.CaptchaVO;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码(SVG),答案存 Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String PREFIX = "s2admin:captcha:";
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheStore cacheStore;
    private final ConfigService configService;

    public boolean isEnabled() {
        return configService.getBoolean("sys.account.captchaEnabled", true);
    }

    public CaptchaVO create() {
        CaptchaVO vo = new CaptchaVO();
        boolean enabled = isEnabled();
        vo.setEnabled(enabled);
        if (!enabled) {
            return vo;
        }
        String code = randomCode(4);
        String key = UUID.randomUUID().toString().replace("-", "");
        int minutes = Math.max(1, configService.getInt("sys.account.captchaExpiration", 5));
        try {
            cacheStore.set(PREFIX + key, code.toLowerCase(), Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("写入验证码失败: {}", e.getMessage());
            throw new BusinessException("验证码服务暂不可用");
        }
        vo.setCaptchaKey(key);
        vo.setImage("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
                svg(code).getBytes(StandardCharsets.UTF_8)));
        return vo;
    }

    public void verify(String key, String code) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new BusinessException("请输入验证码");
        }
        String stored;
        try {
            stored = cacheStore.getAndDelete(PREFIX + key);
        } catch (Exception e) {
            log.warn("读取验证码失败: {}", e.getMessage());
            throw new BusinessException("验证码服务暂不可用");
        }
        if (stored == null || !stored.equalsIgnoreCase(code.trim())) {
            throw new BusinessException("验证码错误或已过期");
        }
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    private String svg(String code) {
        int w = 120;
        int h = 40;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(w)
                .append("' height='").append(h).append("' viewBox='0 0 ").append(w).append(' ').append(h).append("'>");
        sb.append("<rect width='100%' height='100%' fill='#f4f4f5'/>");
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1='").append(RANDOM.nextInt(w)).append("' y1='").append(RANDOM.nextInt(h))
                    .append("' x2='").append(RANDOM.nextInt(w)).append("' y2='").append(RANDOM.nextInt(h))
                    .append("' stroke='#d4d4d8' stroke-width='1'/>");
        }
        for (int i = 0; i < code.length(); i++) {
            int x = 16 + i * 24;
            int y = 26 + RANDOM.nextInt(5);
            int rotate = RANDOM.nextInt(30) - 15;
            sb.append("<text x='").append(x).append("' y='").append(y)
                    .append("' font-size='22' font-family='monospace' fill='#18181b' transform='rotate(")
                    .append(rotate).append(' ').append(x).append(' ').append(y).append(")'>")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }
}
