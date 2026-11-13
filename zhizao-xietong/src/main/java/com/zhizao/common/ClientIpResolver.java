package com.zhizao.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户端 IP 解析：默认只信任 TCP 对端地址，仅在请求来自显式配置的可信代理时才读取
 * {@code X-Forwarded-For}。
 *
 * <p>{@code X-Forwarded-For} 是客户端可任意伪造的请求头，若无条件采信，审计表中的来源 IP
 * 就失去取证价值。因此默认配置为空表示不信任任何代理；部署在反向代理后时，
 * 通过 {@code app.http.trusted-proxies} 填入代理的固定 IP（逗号分隔），
 * 只有 TCP 对端命中该名单才解析转发头，且转发值必须是合法 IP 字面量。</p>
 *
 * @author Caspian
 */
@Component
public class ClientIpResolver {

    /** IPv6 文本地址最长长度（含 IPv4 映射形式）。 */
    private static final int MAX_IP_LENGTH = 45;

    private final Set<String> trustedProxies;

    /**
     * 构造解析器。
     *
     * @param trustedProxies 可信代理 IP，逗号分隔；留空表示不信任任何转发头
     */
    public ClientIpResolver(@Value("${app.http.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 解析请求来源 IP。
     *
     * <p>转发头按「从右往左」解析：最右侧是离本服务最近的一跳，跳过可信代理，
     * 第一个非可信地址才是真实客户端。这样即使客户端自己塞了伪造的
     * {@code X-Forwarded-For}，也无法覆盖代理追加在右侧的真实地址。</p>
     *
     * @param request HTTP 请求，可为 null
     * @return 解析出的 IP；请求为空时返回 null
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remote = request.getRemoteAddr();
        if (remote == null || trustedProxies.isEmpty() || !trustedProxies.contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!isIpLiteral(hop)) {
                return remote;
            }
            if (!trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remote;
    }

    /** 只接受 IPv4/IPv6 字面量，避免转发头里的任意字符串进入审计。 */
    private boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            return isIpv6Literal(value);
        }
        String[] octets = value.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !isDigits(octet) || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * 纯字符串校验 IPv6，不调用 {@code InetAddress.getByName}：
     * 后者对含冒号但非法的输入会尝试名字解析，可能阻塞请求线程。
     */
    private boolean isIpv6Literal(String value) {
        int colons = 0;
        int groups = 1;
        boolean compressed = false;
        boolean previousColon = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':') {
                if (previousColon) {
                    if (compressed) {
                        return false;
                    }
                    compressed = true;
                }
                colons++;
                groups++;
                previousColon = true;
            } else if (isHexDigit(c) || c == '.') {
                previousColon = false;
            } else {
                return false;
            }
        }
        if (colons < 2 || groups > 8) {
            return false;
        }
        if (value.charAt(0) == ':' && !compressed) {
            return false;
        }
        if (value.charAt(value.length() - 1) == ':' && !compressed) {
            return false;
        }
        return compressed || colons == 7;
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isDigits(String value) {
        return value.chars().allMatch(Character::isDigit);
    }
}
