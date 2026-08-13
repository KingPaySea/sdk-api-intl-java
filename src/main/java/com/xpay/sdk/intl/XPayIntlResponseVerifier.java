package com.xpay.sdk.intl;

import java.nio.charset.StandardCharsets;

/**
 * 国际版普通 API 响应验签工具。
 * <p>
 * 工具使用发起请求所对应的 API Secret，对响应 {@code Timestamp}、{@code Nonce} 和精确原始 Body
 * 组成的三行原文验签。验签失败统一返回 {@code false}，调用方必须 fail-close，不能据此响应更新订单终态。
 * </p>
 */
public final class XPayIntlResponseVerifier {

    private XPayIntlResponseVerifier() {
    }

    /**
     * 校验普通 API 响应签名。
     * <p>
     * 每行后均包含一个换行字节，包括 Body 后的最后一个换行。方法先检查时间窗口，再执行常量时间比较；
     * Header 缺失、格式错误、超窗或密钥错误均 fail-close 返回 {@code false}。方法无事务、缓存、网络或异步副作用，
     * 可并发调用。
     * </p>
     *
     * @param timestampHeader 响应 {@code Timestamp} Header
     * @param nonceHeader 响应 {@code Nonce} Header
     * @param signatureHeader 响应 {@code Signature} Header，格式为 {@code v1=} 加 64 位小写十六进制
     * @param rawBody 实际收到且未经 JSON 解析、格式化或重编码的响应 Body 字节
     * @param apiSecret 本次请求 API Key 对应的 API Secret
     * @param toleranceSeconds 与本机 Unix 时间允许的最大偏差秒数，必须大于零
     * @return 时间戳和签名均有效时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean verify(String timestampHeader,
                                 String nonceHeader,
                                 String signatureHeader,
                                 byte[] rawBody,
                                 String apiSecret,
                                 long toleranceSeconds) {
        if (!hasValidSignatureHeaders(timestampHeader, nonceHeader, signatureHeader)
                || apiSecret == null || apiSecret.length() == 0
                || !XPayIntlSigner.isTimestampWithinWindow(timestampHeader, toleranceSeconds)) {
            return false;
        }
        try {
            String expected = sign(timestampHeader, nonceHeader, rawBody, apiSecret);
            return XPayIntlSigner.constantTimeEquals(expected, signatureHeader);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 校验 UTF-8 文本形式的普通 API 响应签名。
     * <p>
     * 该重载便于未修改响应字符串的轻量接入；若框架可获取原始字节，应优先使用字节重载避免字符集转换。
     * 验签失败返回 {@code false}，不启动事务、缓存或异步任务。
     * </p>
     *
     * @param timestampHeader 响应 {@code Timestamp} Header
     * @param nonceHeader 响应 {@code Nonce} Header
     * @param signatureHeader 响应 {@code Signature} Header
     * @param rawBody 未改写的 UTF-8 响应 Body
     * @param apiSecret 本次请求 API Key 对应的 API Secret
     * @param toleranceSeconds 时间戳最大允许偏差秒数，必须大于零
     * @return 验签通过返回 {@code true}，否则返回 {@code false}
     */
    public static boolean verify(String timestampHeader,
                                 String nonceHeader,
                                 String signatureHeader,
                                 String rawBody,
                                 String apiSecret,
                                 long toleranceSeconds) {
        return verify(timestampHeader, nonceHeader, signatureHeader,
                rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8),
                apiSecret, toleranceSeconds);
    }

    /**
     * 生成普通 API 响应签名。
     * <p>
     * 该方法用于对接测试、测试向量和服务端互操作验证；生产商户通常只需调用 {@link #verify}。签名原文严格
     * 保留原始 Body 字节，方法无事务、缓存、网络或异步副作用。
     * </p>
     *
     * @param timestamp 响应 Unix 秒级时间戳
     * @param nonce 响应随机串
     * @param rawBody 原始响应 Body 字节
     * @param apiSecret API Secret
     * @return {@code v1=} 加 64 位小写十六进制签名
     * @throws XPayIntlException 参数含换行、密钥为空或加密算法异常时抛出
     */
    public static String sign(String timestamp, String nonce, byte[] rawBody, String apiSecret) {
        return "v1=" + XPayIntlSigner.hmacSha256Hex(apiSecret,
                XPayIntlSigner.buildThreeLinePayload(timestamp, nonce, rawBody));
    }

    private static boolean hasValidSignatureHeaders(String timestamp, String nonce, String signature) {
        return !isBlank(timestamp)
                && !isBlank(nonce)
                && signature != null
                && signature.matches("^v1=[a-f0-9]{64}$");
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }
}
