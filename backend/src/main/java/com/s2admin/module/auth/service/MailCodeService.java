package com.s2admin.module.auth.service;

import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 忘记密码验证码。默认 mock(写日志并回传);生产应改为真实短信/邮件通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheStore cacheStore;

    @Value("${s2admin.mail.mock:true}")
    private boolean mock;

    public String sendResetCode(String account, String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        cacheStore.set("s2admin:reset:" + account.toLowerCase(), code, Duration.ofMinutes(10));
        log.info("密码重置验证码 account={} email={} mock={} code={}", account, email, mock, mock ? code : "******");
        return mock ? code : null;
    }

    public void verify(String account, String code) {
        String key = "s2admin:reset:" + account.toLowerCase();
        String stored = cacheStore.getAndDelete(key);
        if (stored == null || !stored.equals(code == null ? "" : code.trim())) {
            throw new BusinessException("验证码错误或已过期");
        }
    }
}
