package com.s2admin.module.common.util;

import com.s2admin.module.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 显式设置密码时的强度校验:至少 8 位,含大小写字母、数字和特殊字符。
 * 系统默认初始密码不走此校验。
 */
public final class PasswordPolicy {

    public static final String MESSAGE = "密码至少 8 位,且须包含大小写字母、数字和特殊字符";

    private static final Pattern STRONG = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (!StringUtils.hasText(password) || !STRONG.matcher(password).matches()) {
            throw new BusinessException(MESSAGE);
        }
    }

    public static boolean isStrong(String password) {
        return StringUtils.hasText(password) && STRONG.matcher(password).matches();
    }

    /** 生成符合策略的随机口令(导入/未填密码时使用,不采用弱默认 123456)。 */
    public static String randomStrong() {
        SecureRandom random = new SecureRandom();
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        String special = "!@#$%^&*?";
        String all = lower + upper + digits + special;
        List<Character> chars = new ArrayList<>();
        chars.add(lower.charAt(random.nextInt(lower.length())));
        chars.add(upper.charAt(random.nextInt(upper.length())));
        chars.add(digits.charAt(random.nextInt(digits.length())));
        chars.add(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            chars.add(all.charAt(random.nextInt(all.length())));
        }
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder(chars.size());
        for (Character c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
