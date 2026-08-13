package com.xpay.sdk.intl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 国际版 OpenAPI HTTP 响应。
 * <p>
 * SDK 保留 HTTP 状态码、Header 和原始 Body，并在构造前完成响应验签。只有接口无法识别 API Key 而返回
 * 无签名的非2xx前置错误时，SDK 返回 {@code signatureVerified=false} 的诊断响应；调用方不得据此更新业务状态。
 * </p>
 */
public class XPayIntlResponse {

    /** HTTP 状态码。 */
    public final int statusCode;

    /** 响应 Header，多值按 OkHttp 收到的内容保留；不会包含 SDK 本地密钥。 */
    public final Map<String, List<String>> headers;

    /** 未经 JSON 解析或重编码的响应 Body 字节；空响应为长度零数组，不允许为 {@code null}。 */
    public final byte[] body;

    /** 是否已使用本次请求 API Key 对应的 API Secret 完成响应签名校验。 */
    public final boolean signatureVerified;

    /**
     * 创建经过响应签名处理的 SDK 响应。
     * <p>
     * 构造方法只保存传输结果，不访问网络、缓存或数据库，不启动事务或异步任务。只有无法识别 API Key 的
     * 非2xx前置错误可传 {@code signatureVerified=false}，调用方必须把它作为不可信诊断信息处理。
     * </p>
     *
     * @param statusCode HTTP 状态码
     * @param headers 响应 Header；{@code null} 按不可变空 Map 处理
     * @param body 原始响应 Body；{@code null} 按空字节数组处理
     * @param signatureVerified 响应是否已经通过 HMAC-SHA256 验签
     */
    public XPayIntlResponse(int statusCode,
                            Map<String, List<String>> headers,
                            byte[] body,
                            boolean signatureVerified) {
        this.statusCode = statusCode;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
        this.body = body == null ? new byte[0] : body;
        this.signatureVerified = signatureVerified;
    }

    /**
     * 判断 HTTP 调用是否符合最终五接口成功合同。
     * <p>
     * 只有HTTP为200且响应签名已验证才代表接口调用成功；其它2xx不属于最终合同并fail-close。200仍不代表
     * 订单终态成功，调用方仍需读取 {@code status}、接收 Webhook 或按 {@code merchantOrderNo} 查单。
     * 该方法无事务、缓存或并发副作用。
     * </p>
     *
     * @return HTTP 状态码恰为200且响应验签通过时返回 {@code true}
     */
    public boolean isSuccessful() {
        return signatureVerified && statusCode == 200;
    }

    /**
     * 判断响应是否已通过签名校验。
     * <p>
     * 返回 {@code false} 的响应是网关或服务认证前生成的无签名非2xx错误，Body 只能用于诊断，不能驱动
     * 订单状态。该方法不访问网络、缓存或数据库。
     * </p>
     *
     * @return 普通响应签名有效时返回 {@code true}
     */
    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    /**
     * 以 UTF-8 读取响应 Body。
     * <p>
     * 本方法仅供验签后的 JSON 解析或诊断展示，不会重新执行验签、缓存或异步处理。
     * </p>
     *
     * @return UTF-8 响应文本；空响应返回空字符串
     */
    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
