package com.s2admin.module.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s2admin.module.auth.vo.LoginVO;
import com.s2admin.module.common.cache.CacheStore;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.PasswordPolicy;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.security.RateLimitService;
import com.s2admin.module.system.entity.SysOauthAccount;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.repository.SysOauthAccountRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信 / Google / 微软 / Apple 授权码登录。
 * 未配置客户端时接口返回明确错误,不阻止应用启动。
 * Apple 可直接粘贴已签好的 client secret,也可提供 Team ID、Key ID 和 p8 私钥由服务现签。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final String STATE_PREFIX = "s2admin:oauth:state:";

    private final AuthService authService;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysOauthAccountRepository accountRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final CacheStore cacheStore;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final SecureRandom random = new SecureRandom();

    @Value("${s2admin.oauth.frontend-redirect:http://localhost:5173/login}")
    private String frontendRedirect;

    @Value("${s2admin.oauth.google.enabled:false}")
    private boolean googleEnabled;
    @Value("${s2admin.oauth.google.client-id:}")
    private String googleClientId;
    @Value("${s2admin.oauth.google.client-secret:}")
    private String googleSecret;
    @Value("${s2admin.oauth.google.redirect-uri:http://127.0.0.1:8080/api/auth/oauth/google/callback}")
    private String googleRedirect;

    @Value("${s2admin.oauth.microsoft.enabled:false}")
    private boolean microsoftEnabled;
    @Value("${s2admin.oauth.microsoft.client-id:}")
    private String microsoftClientId;
    @Value("${s2admin.oauth.microsoft.client-secret:}")
    private String microsoftSecret;
    @Value("${s2admin.oauth.microsoft.tenant:common}")
    private String microsoftTenant;
    @Value("${s2admin.oauth.microsoft.redirect-uri:http://127.0.0.1:8080/api/auth/oauth/microsoft/callback}")
    private String microsoftRedirect;

    @Value("${s2admin.oauth.wechat.enabled:false}")
    private boolean wechatEnabled;
    @Value("${s2admin.oauth.wechat.app-id:}")
    private String wechatAppId;
    @Value("${s2admin.oauth.wechat.app-secret:}")
    private String wechatSecret;
    @Value("${s2admin.oauth.wechat.redirect-uri:http://127.0.0.1:8080/api/auth/oauth/wechat/callback}")
    private String wechatRedirect;

    @Value("${s2admin.oauth.apple.enabled:false}")
    private boolean appleEnabled;
    @Value("${s2admin.oauth.apple.client-id:}")
    private String appleClientId;
    @Value("${s2admin.oauth.apple.client-secret:}")
    private String appleSecret;
    @Value("${s2admin.oauth.apple.team-id:}")
    private String appleTeamId;
    @Value("${s2admin.oauth.apple.key-id:}")
    private String appleKeyId;
    @Value("${s2admin.oauth.apple.private-key:}")
    private String applePrivateKey;
    @Value("${s2admin.oauth.apple.private-key-path:}")
    private String applePrivateKeyPath;
    @Value("${s2admin.oauth.apple.redirect-uri:http://127.0.0.1:8080/api/auth/oauth/apple/callback}")
    private String appleRedirect;

    private volatile String appleSecretCache;
    private volatile long appleSecretUntil;

    public List<Map<String, String>> providers() {
        List<Map<String, String>> list = new ArrayList<>();
        addIfReady(list, "wechat", "微信");
        addIfReady(list, "google", "Google");
        addIfReady(list, "microsoft", "微软");
        addIfReady(list, "apple", "Apple");
        return list;
    }

    public String authorizeUrl(String provider, HttpServletRequest request) {
        String id = normalize(provider);
        assertReady(id);
        rateLimit(request, "跳转第三方登录过于频繁,请稍后再试");
        String state = newState();
        cacheStore.set(STATE_PREFIX + state, id, Duration.ofMinutes(10));
        return switch (id) {
            case "google" -> "https://accounts.google.com/o/oauth2/v2/auth?" + query(Map.of(
                    "client_id", googleClientId,
                    "redirect_uri", googleRedirect,
                    "response_type", "code",
                    "scope", "openid email profile",
                    "state", state,
                    "prompt", "select_account"));
            case "microsoft" -> "https://login.microsoftonline.com/" + tenant() + "/oauth2/v2.0/authorize?" + query(Map.of(
                    "client_id", microsoftClientId,
                    "redirect_uri", microsoftRedirect,
                    "response_type", "code",
                    "response_mode", "query",
                    "scope", "openid email profile User.Read",
                    "state", state));
            case "wechat" -> "https://open.weixin.qq.com/connect/qrconnect?" + query(Map.of(
                    "appid", wechatAppId,
                    "redirect_uri", wechatRedirect,
                    "response_type", "code",
                    "scope", "snsapi_login",
                    "state", state)) + "#wechat_redirect";
            case "apple" -> "https://appleid.apple.com/auth/authorize?" + query(Map.of(
                    "client_id", appleClientId,
                    "redirect_uri", appleRedirect,
                    "response_type", "code",
                    "response_mode", "form_post",
                    "scope", "name email",
                    "state", state));
            default -> throw new BusinessException("不支持的登录方式");
        };
    }

    @Transactional
    public LoginVO callback(String provider, String code, String state, HttpServletRequest request) {
        String id = normalize(provider);
        assertReady(id);
        rateLimit(request, "第三方登录过于频繁,请稍后再试");
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("登录状态已失效,请重试");
        }
        String cached = cacheStore.getAndDelete(STATE_PREFIX + state.trim());
        if (!id.equals(cached)) {
            throw new BusinessException("登录状态已失效,请重试");
        }
        Profile profile = switch (id) {
            case "google" -> googleProfile(code);
            case "microsoft" -> microsoftProfile(code);
            case "wechat" -> wechatProfile(code);
            case "apple" -> appleProfile(code);
            default -> throw new BusinessException("不支持的登录方式");
        };
        if (!StringUtils.hasText(profile.openId())) {
            throw new BusinessException("第三方账号标识为空");
        }
        if (StringUtils.hasText(profile.email())) {
            profile = new Profile(profile.openId(), profile.unionId(), profile.email().toLowerCase(java.util.Locale.ROOT),
                    profile.nickname(), profile.avatar());
        }
        SysUser user = resolveUser(id, profile);
        return authService.issueLogin(user, request, true);
    }

    public String successRedirect(LoginVO vo) {
        return frontendBase() + separator() + "oauthToken=" + enc(vo.getToken()) + "&oauthRefresh=" + enc(vo.getRefreshToken());
    }

    public String failureRedirect(String message) {
        String text = StringUtils.hasText(message) ? message : "第三方登录失败";
        if (text.length() > 180) {
            text = text.substring(0, 180);
        }
        return frontendBase() + separator() + "oauthError=" + enc(text.replace('\n', ' ').replace('\r', ' '));
    }

    public String safeMessage(Exception e) {
        if (e instanceof BusinessException && StringUtils.hasText(e.getMessage())) {
            return e.getMessage();
        }
        log.warn("第三方登录失败: {}", e.toString());
        return "第三方登录失败";
    }

    private SysUser resolveUser(String provider, Profile profile) {
        SysOauthAccount link = accountRepository.findByProviderAndOpenId(provider, profile.openId()).orElse(null);
        if (link == null && StringUtils.hasText(profile.unionId())) {
            link = accountRepository.findByProviderAndUnionId(provider, profile.unionId()).orElse(null);
        }
        if (link != null) {
            SysUser linked = userRepository.findWithRolesById(link.getUserId()).orElse(null);
            if (linked == null) {
                link.setOpenId(UniqueFields.tombstone(link.getOpenId(), link.getId(), 128));
                link.setUnionId(null);
                accountRepository.save(link);
                accountRepository.delete(link);
            } else {
                return requireUsable(linked);
            }
        }
        SysUser user = null;
        if (StringUtils.hasText(profile.email())) {
            user = userRepository.findByEmailIgnoreCase(profile.email()).orElse(null);
            if (user != null) {
                user = userRepository.findWithRolesById(user.getId()).orElse(user);
            }
        }
        if (user == null) {
            user = createUser(provider, profile);
        } else {
            user = requireUsable(user);
        }
        SysOauthAccount account = new SysOauthAccount();
        account.setProvider(provider);
        account.setOpenId(profile.openId());
        account.setUnionId(blankToNull(profile.unionId()));
        account.setUserId(user.getId());
        account.setEmail(blankToNull(profile.email()));
        account.setNickname(cut(profile.nickname(), 50));
        account.setAvatar(cut(profile.avatar(), 500));
        accountRepository.save(account);
        return user;
    }

    private SysUser requireUsable(SysUser user) {
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException("绑定的账号不可用");
        }
        return user;
    }

    private SysUser createUser(String provider, Profile profile) {
        SysRole role = roleRepository.findByCode("USER").orElseThrow(() -> new BusinessException("未找到默认用户角色"));
        SysUser user = new SysUser();
        user.setUsername(uniqueUsername(provider, profile.openId()));
        user.setPassword(passwordEncoder.encode(PasswordPolicy.randomStrong()));
        user.setPwdReset(0);
        user.setStatus(0);
        user.setNickname(StringUtils.hasText(profile.nickname()) ? cut(profile.nickname(), 50) : user.getUsername());
        user.setEmail(blankToNull(profile.email()));
        user.setAvatar(cut(profile.avatar(), 500));
        user.setTenantId(tenantService.defaultId());
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private String uniqueUsername(String provider, String openId) {
        String raw = (provider + "_" + openId).replaceAll("[^A-Za-z0-9_]", "");
        if (raw.length() < 2) {
            raw = provider + "_user";
        }
        if (raw.length() > 40) {
            raw = raw.substring(0, 40);
        }
        String username = raw;
        for (int i = 0; i < 30 && userRepository.existsByUsername(username); i++) {
            String suffix = "_" + (i + 1);
            username = raw.substring(0, Math.min(raw.length(), 50 - suffix.length())) + suffix;
        }
        if (userRepository.existsByUsername(username)) {
            username = provider.substring(0, Math.min(provider.length(), 8)) + "_" + Long.toUnsignedString(random.nextLong(), 36);
        }
        return username.length() > 50 ? username.substring(0, 50) : username;
    }

    private Profile googleProfile(String code) {
        JsonNode token = postForm("https://oauth2.googleapis.com/token", Map.of(
                "code", code,
                "client_id", googleClientId,
                "client_secret", googleSecret,
                "redirect_uri", googleRedirect,
                "grant_type", "authorization_code"));
        String access = text(token, "access_token");
        if (!StringUtils.hasText(access)) {
            throw new BusinessException("Google 未返回访问令牌");
        }
        JsonNode user = getJson("https://openidconnect.googleapis.com/v1/userinfo", access);
        boolean verified = truthy(user, "email_verified");
        return new Profile(text(user, "sub"), null, verified ? text(user, "email") : null,
                firstText(user, "name", "email"), text(user, "picture"));
    }

    private Profile microsoftProfile(String code) {
        JsonNode token = postForm("https://login.microsoftonline.com/" + tenant() + "/oauth2/v2.0/token", Map.of(
                "code", code,
                "client_id", microsoftClientId,
                "client_secret", microsoftSecret,
                "redirect_uri", microsoftRedirect,
                "grant_type", "authorization_code",
                "scope", "openid email profile User.Read"));
        String access = text(token, "access_token");
        if (!StringUtils.hasText(access)) {
            throw new BusinessException("微软未返回访问令牌");
        }
        JsonNode user = getJson("https://graph.microsoft.com/v1.0/me", access);
        String email = firstText(user, "mail", "userPrincipalName");
        if (email != null && !email.contains("@")) {
            email = null;
        }
        return new Profile(text(user, "id"), null, email, firstText(user, "displayName", "mail"), null);
    }

    private Profile wechatProfile(String code) {
        JsonNode token = getJson("https://api.weixin.qq.com/sns/oauth2/access_token?" + query(Map.of(
                "appid", wechatAppId,
                "secret", wechatSecret,
                "code", code,
                "grant_type", "authorization_code")), null);
        assertWechat(token);
        String access = text(token, "access_token");
        String openId = text(token, "openid");
        if (!StringUtils.hasText(access) || !StringUtils.hasText(openId)) {
            throw new BusinessException("微信未返回访问令牌");
        }
        JsonNode user = getJson("https://api.weixin.qq.com/sns/userinfo?" + query(Map.of(
                "access_token", access,
                "openid", openId,
                "lang", "zh_CN")), null);
        assertWechat(user);
        return new Profile(firstText(user, "openid", "openId"), text(user, "unionid"), null,
                text(user, "nickname"), text(user, "headimgurl"));
    }

    private Profile appleProfile(String code) {
        JsonNode token = postForm("https://appleid.apple.com/auth/token", Map.of(
                "code", code,
                "client_id", appleClientId,
                "client_secret", appleClientSecret(),
                "redirect_uri", appleRedirect,
                "grant_type", "authorization_code"));
        String idToken = text(token, "id_token");
        if (!StringUtils.hasText(idToken)) {
            throw new BusinessException("Apple 未返回身份令牌");
        }
        JsonNode payload = decodeJwtPayload(idToken);
        String iss = text(payload, "iss");
        String aud = text(payload, "aud");
        if (iss != null && !iss.contains("appleid.apple.com")) {
            throw new BusinessException("Apple 身份令牌来源不正确");
        }
        if (aud != null && !aud.equals(appleClientId)) {
            throw new BusinessException("Apple 身份令牌受众不正确");
        }
        boolean verified = !payload.has("email_verified") || truthy(payload, "email_verified");
        return new Profile(text(payload, "sub"), null, verified ? text(payload, "email") : null,
                text(payload, "email"), null);
    }

    private String appleClientSecret() {
        if (StringUtils.hasText(appleSecret)) {
            return appleSecret.trim();
        }
        long now = System.currentTimeMillis();
        if (appleSecretCache != null && now < appleSecretUntil) {
            return appleSecretCache;
        }
        if (!StringUtils.hasText(appleTeamId) || !StringUtils.hasText(appleKeyId)) {
            throw new BusinessException("请配置 Apple client secret,或同时提供 team-id、key-id 与 p8 私钥");
        }
        try {
            String jwt = signAppleSecret(loadAppleKey());
            appleSecretCache = jwt;
            appleSecretUntil = now + Duration.ofHours(12).toMillis();
            return jwt;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("无法生成 Apple client secret");
        }
    }

    private PrivateKey loadAppleKey() throws Exception {
        String pem = applePrivateKey;
        if (!StringUtils.hasText(pem) && StringUtils.hasText(applePrivateKeyPath)) {
            pem = Files.readString(Path.of(applePrivateKeyPath.trim()));
        }
        if (!StringUtils.hasText(pem)) {
            throw new BusinessException("请配置 Apple client secret,或同时提供 team-id、key-id 与 p8 私钥");
        }
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] pkcs8 = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private String signAppleSecret(PrivateKey key) throws Exception {
        String header = b64url(("{\"alg\":\"ES256\",\"kid\":\"" + jsonEscape(appleKeyId.trim()) + "\"}").getBytes(StandardCharsets.UTF_8));
        long iat = Instant.now().getEpochSecond();
        long exp = iat + 86400L * 150;
        String payloadJson = "{\"iss\":\"" + jsonEscape(appleTeamId.trim()) + "\",\"iat\":" + iat + ",\"exp\":" + exp
                + ",\"aud\":\"https://appleid.apple.com\",\"sub\":\"" + jsonEscape(appleClientId.trim()) + "\"}";
        String payload = b64url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(input.getBytes(StandardCharsets.UTF_8));
        return input + "." + b64url(derToJose(signature.sign(), 32));
    }

    private void assertReady(String id) {
        if (!ready(id)) {
            throw new BusinessException(switch (id) {
                case "wechat" -> "微信登录未配置。请设置 OAUTH_WECHAT_ENABLED、OAUTH_WECHAT_APP_ID、OAUTH_WECHAT_APP_SECRET";
                case "google" -> "Google 登录未配置。请设置 OAUTH_GOOGLE_ENABLED、OAUTH_GOOGLE_CLIENT_ID、OAUTH_GOOGLE_CLIENT_SECRET";
                case "microsoft" -> "微软登录未配置。请设置 OAUTH_MICROSOFT_ENABLED、OAUTH_MICROSOFT_CLIENT_ID、OAUTH_MICROSOFT_CLIENT_SECRET";
                case "apple" -> "Apple 登录未配置。请设置 OAUTH_APPLE_ENABLED、OAUTH_APPLE_CLIENT_ID,以及 client secret 或 p8 私钥";
                default -> "不支持的登录方式";
            });
        }
    }

    private boolean ready(String id) {
        return switch (id) {
            case "google" -> googleEnabled && StringUtils.hasText(googleClientId) && StringUtils.hasText(googleSecret);
            case "microsoft" -> microsoftEnabled && StringUtils.hasText(microsoftClientId) && StringUtils.hasText(microsoftSecret);
            case "wechat" -> wechatEnabled && StringUtils.hasText(wechatAppId) && StringUtils.hasText(wechatSecret);
            case "apple" -> appleEnabled && StringUtils.hasText(appleClientId) && (StringUtils.hasText(appleSecret)
                    || (StringUtils.hasText(appleTeamId) && StringUtils.hasText(appleKeyId)
                    && (StringUtils.hasText(applePrivateKey) || StringUtils.hasText(applePrivateKeyPath))));
            default -> false;
        };
    }

    private void addIfReady(List<Map<String, String>> list, String id, String name) {
        if (!ready(id)) {
            return;
        }
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        list.add(item);
    }

    private JsonNode postForm(String url, Map<String, String> form) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(query(form)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("第三方令牌接口失败 status={}", response.statusCode());
                throw new BusinessException("第三方登录失败");
            }
            JsonNode node = objectMapper.readTree(response.body());
            if (node.hasNonNull("error")) {
                throw new BusinessException("第三方登录失败");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("第三方令牌接口异常: {}", e.toString());
            throw new BusinessException("第三方登录失败");
        }
    }

    private JsonNode getJson(String url, String bearer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
            if (StringUtils.hasText(bearer)) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("第三方用户信息失败 status={}", response.statusCode());
                throw new BusinessException("第三方登录失败");
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("第三方用户信息异常: {}", e.toString());
            throw new BusinessException("第三方登录失败");
        }
    }

    private void assertWechat(JsonNode node) {
        if (node.has("errcode") && node.get("errcode").asInt() != 0) {
            throw new BusinessException("微信登录失败");
        }
    }

    private JsonNode decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new BusinessException("身份令牌格式不正确");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(pad(parts[1]));
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException("身份令牌无法解析");
        }
    }

    private void rateLimit(HttpServletRequest request, String message) {
        String ip = request == null ? "unknown" : request.getRemoteAddr();
        rateLimitService.assertAllowed("s2admin:oauth:ip:" + ip, 30, Duration.ofMinutes(1), message);
    }

    private String newState() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String frontendBase() {
        String base = frontendRedirect == null ? "" : frontendRedirect.trim();
        if ((!base.startsWith("http://") && !base.startsWith("https://")) || base.contains("\n") || base.contains("\r")) {
            throw new BusinessException("OAUTH_FRONTEND_REDIRECT 配置不正确");
        }
        return base;
    }

    private String separator() {
        return frontendBase().contains("?") ? "&" : "?";
    }

    private String tenant() {
        String value = StringUtils.hasText(microsoftTenant) ? microsoftTenant.trim() : "common";
        if (!value.matches("^[A-Za-z0-9.-]{1,64}$")) {
            return "common";
        }
        return value;
    }

    private String normalize(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase();
        if ("weixin".equals(value)) {
            return "wechat";
        }
        return value;
    }

    private static String query(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean truthy(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return false;
        }
        JsonNode value = node.get(field);
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return "true".equalsIgnoreCase(value.asText());
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String cut(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }

    private static String b64url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static byte[] derToJose(byte[] der, int size) {
        if (der.length < 8 || der[0] != 0x30) {
            throw new IllegalArgumentException("ECDSA 签名格式不正确");
        }
        int offset = (der[1] & 0x80) == 0 ? 2 : 2 + (der[1] & 0x7f);
        if (der[offset] != 0x02) {
            throw new IllegalArgumentException("ECDSA 签名格式不正确");
        }
        int rLen = der[offset + 1] & 0xff;
        int rPos = offset + 2;
        int sMarker = rPos + rLen;
        if (der[sMarker] != 0x02) {
            throw new IllegalArgumentException("ECDSA 签名格式不正确");
        }
        int sLen = der[sMarker + 1] & 0xff;
        byte[] out = new byte[size * 2];
        copyComponent(der, rPos, rLen, out, 0, size);
        copyComponent(der, sMarker + 2, sLen, out, size, size);
        return out;
    }

    private static void copyComponent(byte[] src, int pos, int len, byte[] dest, int destPos, int size) {
        int start = pos;
        int end = pos + len;
        while (start < end - 1 && src[start] == 0) {
            start++;
        }
        int n = end - start;
        if (n > size) {
            throw new IllegalArgumentException("ECDSA 签名组件过长");
        }
        System.arraycopy(src, start, dest, destPos + size - n, n);
    }

    private record Profile(String openId, String unionId, String email, String nickname, String avatar) {
    }
}
