package com.xpay.sdk.intl;

/**
 * 国际版 OpenAPI SDK 异常。
 * <p>
 * SDK 在本地签名、JSON 序列化、HTTP 请求或 Webhook 验签失败时抛出该异常。异常信息不会包含 API Secret、
 * Webhook Secret 或完整请求 Header，避免调用方误写日志造成密钥泄露。
 * </p>
 */
public class XPayIntlException extends RuntimeException {

    /**
     * 创建 SDK 异常。
     *
     * @param message 商户侧可排查的错误摘要，不包含密钥和完整请求报文
     */
    public XPayIntlException(String message) {
        super(message);
    }

    /**
     * 创建带原因的 SDK 异常。
     *
     * @param message 商户侧可排查的错误摘要
     * @param cause 原始异常；调用方输出日志前应自行脱敏
     */
    public XPayIntlException(String message, Throwable cause) {
        super(message, cause);
    }
}
