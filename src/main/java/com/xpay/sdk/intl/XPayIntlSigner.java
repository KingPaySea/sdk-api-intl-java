package com.xpay.sdk.intl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 国际版 OpenAPI 请求签名工具。
 * <p>
 * 工具严格按开发者平台五行协议，对 HTTP 方法、实际请求 URL、时间戳、Nonce 和原始 Body 计算
 * HMAC-SHA256。方法不保存密钥、不规范化 Query、不改写 Body，也不访问网络、缓存或数据库；非法输入
 * 一律 fail-close 抛出异常，避免生成与网关解释不一致的签名。
 * </p>
 */
public final class XPayIntlSigner {

    private XPayIntlSigner() {
    }

    /**
     * 生成请求 {@code Signature} Header。
     * <p>
     * 本方法无事务、缓存、异步任务或共享可变状态，可并发调用。请求 URL 必须是实际发送的 path 加原始
     * Query，例如 {@code /intl/v1/payment/order/query?merchantOrderNo=DEMO_PAY_202606010001}；
     * 签名过程中不会排序 Query。余额查询不传筛选时，实际请求 URL 为 {@code /intl/v1/balance/query}。
     * </p>
     *
     * @param apiSecret 平台下发的 API Secret，不得记录日志
     * @param requestMethod 实际 HTTP 方法，例如 GET 或 POST
     * @param requestUrl 实际请求 path 与原始 Query，不包含域名
     * @param timestamp Unix 秒级时间戳字符串
     * @param nonce 同一 API Key 下本次请求唯一的随机串
     * @param rawBody 实际发送的 UTF-8 原始 Body；GET 传空字符串
     * @return {@code v1=} 加 64 位小写十六进制 HMAC-SHA256
     * @throws XPayIntlException 密钥为空、签名字段含换行或加密算法不可用时抛出
     */
    public static String sign(String apiSecret,
                              String requestMethod,
                              String requestUrl,
                              String timestamp,
                              String nonce,
                              String rawBody) {
        return "v1=" + hmacSha256Hex(apiSecret,
                buildSignPayload(requestMethod, requestUrl, timestamp, nonce, rawBody));
    }

    /**
     * 构造五行请求签名原文。
     * <p>
     * 每一行后都追加一个 {@code \n}，包括最后一行。方法保持请求 URL、Query 顺序和 Body 内容原样；
     * 任一签名元数据包含 CR/LF 时 fail-close，防止行边界歧义。
     * </p>
     *
     * @param requestMethod 实际 HTTP 方法
     * @param requestUrl 实际请求 path 与原始 Query
     * @param timestamp Unix 秒级时间戳字符串
     * @param nonce 本次请求随机串
     * @param rawBody 实际发送的原始 Body；空 Body 可传空字符串
     * @return 固定五行且末尾带换行的签名原文
     * @throws XPayIntlException URL 非相对请求目标或字段存在换行歧义时抛出
     */
    public static String buildSignPayload(String requestMethod,
                                          String requestUrl,
                                          String timestamp,
                                          String nonce,
                                          String rawBody) {
        String method = safeLine(requestMethod, "requestMethod").toUpperCase(Locale.ROOT);
        String target = validateRequestUrl(requestUrl);
        return method + "\n"
                + target + "\n"
                + safeLine(timestamp, "timestamp") + "\n"
                + safeLine(nonce, "nonce") + "\n"
                + value(rawBody) + "\n";
    }

    /**
     * 计算字符串原文的 HMAC-SHA256 十六进制值。
     * <p>
     * 字符串按 UTF-8 编码；方法无网络、事务、缓存或异步副作用，可并发调用。密钥为空或算法异常时
     * fail-close 抛出异常。
     * </p>
     *
     * @param secret HMAC 密钥
     * @param payload 待签名字符串；{@code null} 按空字符串处理
     * @return 64 位小写十六进制值，不含 {@code v1=} 前缀
     * @throws XPayIntlException 密钥为空或加密算法不可用时抛出
     */
    public static String hmacSha256Hex(String secret, String payload) {
        return hmacSha256Hex(secret, value(payload).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算原始字节的 HMAC-SHA256 十六进制值。
     * <p>
     * 该重载供 API 响应和 Webhook 对精确 Body 字节验签使用，不进行字符集转换或 JSON 解析；可安全并发
     * 调用。密钥为空或算法异常时 fail-close 抛出异常。
     * </p>
     *
     * @param secret HMAC 密钥
     * @param payload 待签名原始字节；{@code null} 按空字节数组处理
     * @return 64 位小写十六进制值，不含 {@code v1=} 前缀
     * @throws XPayIntlException 密钥为空或加密算法不可用时抛出
     */
    public static String hmacSha256Hex(String secret, byte[] payload) {
        if (secret == null || secret.length() == 0) {
            throw new XPayIntlException("secret must not be empty");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(payload == null ? new byte[0] : payload));
        } catch (Exception ex) {
            throw new XPayIntlException("hmac calculation failed", ex);
        }
    }

    /**
     * 以常量时间比较两个签名字符串。
     * <p>
     * 方法只比较 UTF-8 字节，不访问网络、缓存或数据库；任一值为空时直接返回 {@code false}，用于验签
     * fail-close。
     * </p>
     *
     * @param left 待比较签名
     * @param right 期望签名
     * @return 两个非空签名字节完全相同时返回 {@code true}
     */
    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] buildThreeLinePayload(String timestamp, String nonce, byte[] rawBody) {
        String normalizedTimestamp = safeLine(timestamp, "timestamp");
        String normalizedNonce = safeLine(nonce, "nonce");
        byte[] prefix = (normalizedTimestamp + "\n" + normalizedNonce + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        ByteArrayOutputStream output = new ByteArrayOutputStream(prefix.length + body.length + 1);
        output.write(prefix, 0, prefix.length);
        output.write(body, 0, body.length);
        output.write('\n');
        return output.toByteArray();
    }

    static boolean isTimestampWithinWindow(String timestamp, long toleranceSeconds) {
        if (toleranceSeconds <= 0L) {
            return false;
        }
        try {
            long signedAt = Long.parseLong(safeLine(timestamp, "timestamp"));
            long now = System.currentTimeMillis() / 1000L;
            return isTimestampWithinWindow(now, signedAt, toleranceSeconds);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static boolean isTimestampWithinWindow(long now, long signedAt, long toleranceSeconds) {
        if (toleranceSeconds <= 0L) {
            return false;
        }
        try {
            long delta = Math.subtractExact(now, signedAt);
            return delta != Long.MIN_VALUE && Math.abs(delta) <= toleranceSeconds;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private static String validateRequestUrl(String requestUrl) {
        String value = safeLine(requestUrl, "requestUrl");
        if (!value.startsWith("/") || value.indexOf("://") >= 0 || value.indexOf('#') >= 0) {
            throw new XPayIntlException("requestUrl must be a relative path without fragment");
        }
        return value;
    }

    private static String safeLine(String value, String field) {
        if (value == null || value.length() == 0) {
            throw new XPayIntlException(field + " must not be empty");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new XPayIntlException(field + " must not contain CR or LF");
        }
        return value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String toHex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = alphabet[value >>> 4];
            result[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }
}
