package com.xpay.sdk.intl;

import java.nio.charset.StandardCharsets;

/**
 * 国际版 Webhook 请求验签工具。
 * <p>
 * 商户应先按 {@code Webhook-Secret-Id} 选择本地保存的 Webhook Secret，再使用投递 {@code Timestamp}、
 * {@code Nonce} 和原始 Body 的三行协议验签。工具不解析事件、不选择密钥、不保存业务状态；验签失败必须
 * fail-close，并在验签通过后由业务按 {@code eventId} 幂等消费。
 * </p>
 */
public final class XPayIntlWebhookVerifier {

    private XPayIntlWebhookVerifier() {
    }

    /**
     * 使用原始 Body 字节校验 Webhook 签名。
     * <p>
     * Header 缺失、时间戳超窗、签名格式错误或 Body 被修改时均返回 {@code false}，不会把密钥写入异常。
     * 方法无事务、缓存、网络或异步副作用，可由接收服务并发调用。
     * </p>
     *
     * @param timestampHeader Webhook {@code Timestamp} Header
     * @param nonceHeader Webhook {@code Nonce} Header
     * @param signatureHeader Webhook {@code Signature} Header
     * @param rawBody 实际收到且未经解析或重编码的 Body 字节
     * @param webhookSecret {@code Webhook-Secret-Id} 对应的 Webhook Secret
     * @param toleranceSeconds 与本机 Unix 时间允许的最大偏差秒数，必须大于零
     * @return 时间戳和签名均有效时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean verify(String timestampHeader,
                                 String nonceHeader,
                                 String signatureHeader,
                                 byte[] rawBody,
                                 String webhookSecret,
                                 long toleranceSeconds) {
        if (isBlank(timestampHeader) || isBlank(nonceHeader) || isBlank(webhookSecret)
                || signatureHeader == null || !signatureHeader.matches("^v1=[a-f0-9]{64}$")
                || !XPayIntlSigner.isTimestampWithinWindow(timestampHeader, toleranceSeconds)) {
            return false;
        }
        try {
            return XPayIntlSigner.constantTimeEquals(
                    sign(timestampHeader, nonceHeader, rawBody, webhookSecret), signatureHeader);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 使用未改写的 UTF-8 Body 文本校验 Webhook 签名。
     * <p>
     * 若框架能够提供原始字节，应优先使用字节重载；本方法不会解析 JSON，不访问数据库或缓存，验签失败
     * 统一返回 {@code false}。
     * </p>
     *
     * @param timestampHeader Webhook {@code Timestamp} Header
     * @param nonceHeader Webhook {@code Nonce} Header
     * @param signatureHeader Webhook {@code Signature} Header
     * @param rawBody 未改写的 UTF-8 Body 文本
     * @param webhookSecret {@code Webhook-Secret-Id} 对应的 Webhook Secret
     * @param toleranceSeconds 时间戳最大允许偏差秒数，必须大于零
     * @return 验签通过返回 {@code true}，否则返回 {@code false}
     */
    public static boolean verify(String timestampHeader,
                                 String nonceHeader,
                                 String signatureHeader,
                                 String rawBody,
                                 String webhookSecret,
                                 long toleranceSeconds) {
        return verify(timestampHeader, nonceHeader, signatureHeader,
                rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8),
                webhookSecret, toleranceSeconds);
    }

    /**
     * 生成 Webhook 签名。
     * <p>
     * 该方法用于本地测试和平台互操作向量；生产接收流程只需调用 {@link #verify}。原文固定为时间戳、Nonce、
     * 原始 Body 三行，每行后都带换行；方法无网络、事务、缓存或异步副作用。
     * </p>
     *
     * @param timestamp 投递 Unix 秒级时间戳
     * @param nonce 投递随机串
     * @param rawBody 原始 Webhook Body 字节
     * @param webhookSecret Webhook Secret
     * @return {@code v1=} 加 64 位小写十六进制签名
     * @throws XPayIntlException 参数含换行、密钥为空或加密算法异常时抛出
     */
    public static String sign(String timestamp, String nonce, byte[] rawBody, String webhookSecret) {
        return "v1=" + XPayIntlSigner.hmacSha256Hex(webhookSecret,
                XPayIntlSigner.buildThreeLinePayload(timestamp, nonce, rawBody));
    }

    /**
     * 生成 UTF-8 文本 Body 的 Webhook 签名。
     * <p>
     * 本重载便于本地示例和测试；不会解析或格式化 JSON，也不访问网络、数据库或缓存。
     * </p>
     *
     * @param timestamp 投递 Unix 秒级时间戳
     * @param nonce 投递随机串
     * @param rawBody 未改写的 UTF-8 Body 文本
     * @param webhookSecret Webhook Secret
     * @return {@code v1=} 加 64 位小写十六进制签名
     * @throws XPayIntlException 参数含换行、密钥为空或加密算法异常时抛出
     */
    public static String sign(String timestamp, String nonce, String rawBody, String webhookSecret) {
        return sign(timestamp, nonce,
                rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8), webhookSecret);
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }
}
