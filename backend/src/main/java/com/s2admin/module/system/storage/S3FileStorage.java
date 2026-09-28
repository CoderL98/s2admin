package com.s2admin.module.system.storage;

import com.s2admin.module.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * S3 / MinIO 兼容存储。使用 JDK HttpClient 做 SigV4,不额外引入 AWS SDK。
 * 默认 path-style,方便 MinIO。未配齐密钥时调用才失败,不影响 local 模式启动。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "s2admin.storage", name = "type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${s2admin.storage.s3.endpoint:}")
    private String endpoint;
    @Value("${s2admin.storage.s3.access-key:}")
    private String accessKey;
    @Value("${s2admin.storage.s3.secret-key:}")
    private String secretKey;
    @Value("${s2admin.storage.s3.bucket:s2admin}")
    private String bucket;
    @Value("${s2admin.storage.s3.region:us-east-1}")
    private String region;
    @Value("${s2admin.storage.s3.path-style:true}")
    private boolean pathStyle;
    @Value("${s2admin.storage.s3.public-base-url:}")
    private String publicBaseUrl;

    @Override
    public String type() {
        return "s3";
    }

    @Override
    public Stored store(MultipartFile file, String storedName) {
        String key = safeKey(storedName);
        try {
            byte[] body = file.getBytes();
            String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
            exchange("PUT", key, body, contentType);
            String url = StringUtils.hasText(publicBaseUrl)
                    ? publicBaseUrl.replaceAll("/+$", "") + "/" + key
                    : "/api/system/file/" + key;
            return new Stored(key, url);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("对象存储写入失败: {}", e.toString());
            throw new BusinessException("对象存储写入失败");
        }
    }

    @Override
    public Resource load(String storedName) {
        String key = safeKey(storedName);
        byte[] body = exchange("GET", key, new byte[0], null);
        return new ByteArrayResource(body);
    }

    @Override
    public void delete(String storedName) {
        String key = safeKey(storedName);
        try {
            exchange("DELETE", key, new byte[0], null);
        } catch (BusinessException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return;
            }
            throw e;
        }
    }

    private byte[] exchange(String method, String key, byte[] body, String contentType) {
        assertReady();
        byte[] payload = "GET".equals(method) || "DELETE".equals(method) ? new byte[0] : body;
        String hash = payload.length == 0 ? EMPTY_HASH : sha256Hex(payload);
        String amzDate = AMZ.format(Instant.now());
        String dateStamp = amzDate.substring(0, 8);
        URI endpointUri = endpoint();
        String host = hostHeader(endpointUri, pathStyle ? null : bucket);
        String canonicalUri = canonicalUri(endpointUri, key);
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-content-sha256", hash);
        headers.put("x-amz-date", amzDate);
        if (StringUtils.hasText(contentType)) {
            headers.put("content-type", contentType);
        }
        String canonicalHeaders = canonicalHeaders(headers);
        String signedHeaders = String.join(";", headers.keySet());
        String canonical = method + "\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hash;
        String scope = dateStamp + "/" + region.trim() + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = signingKey(secretKey, dateStamp, region.trim());
        String signature = hex(hmac(signingKey, toSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey.trim() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
        String url = endpointUri.getScheme() + "://" + host + canonicalUri;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-amz-date", amzDate)
                    .header("x-amz-content-sha256", hash)
                    .header("Authorization", authorization);
            if (StringUtils.hasText(contentType)) {
                builder.header("Content-Type", contentType);
            }
            HttpRequest request = switch (method) {
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                case "DELETE" -> builder.DELETE().build();
                default -> builder.GET().build();
            };
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 404) {
                throw new BusinessException("对象存储对象不存在(404)");
            }
            if (status / 100 != 2) {
                log.warn("对象存储响应 status={}", status);
                throw new BusinessException("对象存储请求失败(" + status + ")");
            }
            return response.body() == null ? new byte[0] : response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("对象存储请求异常: {}", e.toString());
            throw new BusinessException("对象存储请求失败");
        }
    }

    private void assertReady() {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)
                || !StringUtils.hasText(bucket)) {
            throw new BusinessException("对象存储未配置完整,请设置 S3_ENDPOINT、S3_ACCESS_KEY、S3_SECRET_KEY、S3_BUCKET");
        }
    }

    private URI endpoint() {
        String raw = endpoint.trim();
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "http://" + raw;
        }
        return URI.create(raw);
    }

    private String hostHeader(URI endpointUri, String bucketPrefix) {
        String host = endpointUri.getHost();
        int port = endpointUri.getPort();
        boolean defaultPort = port < 0 || ("https".equalsIgnoreCase(endpointUri.getScheme()) && port == 443)
                || ("http".equalsIgnoreCase(endpointUri.getScheme()) && port == 80);
        String withPort = defaultPort ? host : host + ":" + port;
        if (bucketPrefix == null) {
            return withPort;
        }
        return bucketPrefix + "." + withPort;
    }

    private String canonicalUri(URI endpointUri, String key) {
        String base = endpointUri.getPath() == null ? "" : endpointUri.getPath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String encodedKey = uriEncode(key, false);
        if (pathStyle) {
            return base + "/" + uriEncode(bucket.trim(), false) + "/" + encodedKey;
        }
        return base + "/" + encodedKey;
    }

    private static String canonicalHeaders(TreeMap<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue().trim()).append('\n');
        }
        return sb.toString();
    }

    private static byte[] signingKey(String secret, String dateStamp, String region) {
        byte[] kDate = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, "s3");
        return hmac(kService, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算签名", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算摘要", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static String uriEncode(String value, boolean encodeSlash) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte item : bytes) {
            int c = item & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || (!encodeSlash && c == '/')) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return sb.toString();
    }

    private static String safeKey(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("..")
                || storedName.contains("/") || storedName.contains("\\")) {
            throw BusinessException.notFound("文件不存在");
        }
        return storedName;
    }

    @SuppressWarnings("unused")
    private static List<String> signedHeaderNames(TreeMap<String, String> headers) {
        return new ArrayList<>(headers.keySet());
    }
}
