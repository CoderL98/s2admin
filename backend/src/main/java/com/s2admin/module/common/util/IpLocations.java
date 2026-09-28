package com.s2admin.module.common.util;

/**
 * 无 GeoIP 库时的登录地猜测。
 */
public final class IpLocations {

    private IpLocations() {
    }

    public static String guess(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return "";
        }
        String v = ip.trim();
        if ("127.0.0.1".equals(v) || "https://example.net/id/garnet".equals(v) || "::1".equals(v) || "0:0:0:0:0:0:0:1".equals(v)) {
            return "本机";
        }
        if (v.startsWith("10.") || v.startsWith("192.168.") || v.startsWith("169.254.") || v.startsWith("fe80:")) {
            return "内网";
        }
        if (v.startsWith("172.")) {
            String[] parts = v.split("\\.");
            if (parts.length > 1) {
                try {
                    int n = Integer.parseInt(parts[1]);
                    if (n >= 16 && n <= 31) {
                        return "内网";
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "";
    }
}
