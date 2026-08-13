package com.xpay.sdk.intl;

import okhttp3.OkHttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * 国际版开放接口 SDK 的不可变配置。
 * <p>
 * 配置只保存平台下发的 HTTPS 接口域名、API Key、API Secret、响应验签时间窗口和可复用 HTTP 客户端。
 * {@code /intl/v1} 版本路径由客户端内部统一拼接，避免商户配置时把环境域名和接口版本混在一起。
 * SDK 强制关闭连接重试和重定向，防止资金请求重放或签名 Header 跨地址转发；密钥仅用于内存签名，
 * 不写入日志、文件、缓存或网络请求体。
 * </p>
 */
public class XPayIntlConfig {

    /** 普通 API 响应和 Webhook 的默认时间戳容忍窗口，单位为秒。 */
    public static final long DEFAULT_SIGNATURE_TOLERANCE_SECONDS = 300L;

    /** 平台下发的 HTTPS 接口域名；不包含 {@code /intl/v1}、Query 或 Fragment，允许测试端口。 */
    private final String baseUrl;

    /** 绑定商户和权限的API Key；当前调用来源由平台运营侧安全组控制。 */
    private final String apiKey;

    /** 仅用于请求签名和响应验签的 API Secret。 */
    private final String apiSecret;

    /** 普通响应签名时间戳允许与本机时间偏差的最大秒数。 */
    private final long responseSignatureToleranceSeconds;

    /** 关闭自动连接重试和重定向、供多线程复用的 HTTP 客户端。 */
    private final OkHttpClient httpClient;

    /**
     * 使用 SDK 默认 HTTP 客户端创建配置。
     * <p>
     * 失败策略：任一凭据为空、Base URL 不是严格的 HTTPS 根地址时立即抛出参数异常；本构造方法不访问网络、
     * 数据库或缓存，也不启动异步任务。
     * </p>
     *
     * @param baseUrl 平台下发的 HTTPS 接口域名，例如 {@code https://api.example.com}
     * @param apiKey 商户 API Key，用于 {@code Api-Key} Header
     * @param apiSecret 商户 API Secret，仅用于本地 HMAC-SHA256
     * @throws IllegalArgumentException Base URL 非法或任一凭据为空时抛出
     */
    public XPayIntlConfig(String baseUrl, String apiKey, String apiSecret) {
        this(baseUrl, apiKey, apiSecret, DEFAULT_SIGNATURE_TOLERANCE_SECONDS, null);
    }

    /**
     * 使用调用方提供的 HTTP 客户端创建配置。
     * <p>
     * SDK 会复制调用方客户端并强制关闭连接自动重试、HTTP 重定向和 HTTPS 重定向，避免 POST 重放或把
     * API Key 与按原 URL 计算的签名发送到其他地址。配置对象不可变，可被多个请求线程共享；调用方仍负责
     * 原客户端共享连接池、Dispatcher 等资源的生命周期。
     * </p>
     *
     * @param baseUrl 平台下发的 HTTPS 接口域名，例如 {@code https://api.example.com}
     * @param apiKey 商户 API Key，用于 {@code Api-Key} Header
     * @param apiSecret 商户 API Secret，仅用于本地 HMAC-SHA256
     * @param httpClient HTTP 客户端；传 {@code null} 时使用 SDK 默认客户端
     * @throws IllegalArgumentException Base URL 非法或任一凭据为空时抛出
     */
    public XPayIntlConfig(String baseUrl, String apiKey, String apiSecret, OkHttpClient httpClient) {
        this(baseUrl, apiKey, apiSecret, DEFAULT_SIGNATURE_TOLERANCE_SECONDS, httpClient);
    }

    /**
     * 使用自定义响应验签窗口和 HTTP 客户端创建配置。
     * <p>
     * 失败策略：验签窗口必须大于零；所有校验在对象构造阶段 fail-close 完成。该方法无事务、缓存、
     * 异步任务或网络副作用。
     * </p>
     *
     * @param baseUrl 平台下发的 HTTPS 接口域名，例如 {@code https://api.example.com}
     * @param apiKey 商户 API Key，用于 {@code Api-Key} Header
     * @param apiSecret 商户 API Secret，仅用于本地 HMAC-SHA256
     * @param responseSignatureToleranceSeconds 响应签名时间戳容忍窗口，必须大于零
     * @param httpClient HTTP 客户端；传 {@code null} 时使用 SDK 默认客户端
     * @throws IllegalArgumentException Base URL、凭据或响应验签窗口不合法时抛出
     */
    public XPayIntlConfig(String baseUrl,
                          String apiKey,
                          String apiSecret,
                          long responseSignatureToleranceSeconds,
                          OkHttpClient httpClient) {
        this.baseUrl = normalizeBaseUrl(required(baseUrl, "baseUrl"));
        this.apiKey = required(apiKey, "apiKey");
        this.apiSecret = required(apiSecret, "apiSecret");
        if (responseSignatureToleranceSeconds <= 0L) {
            throw new IllegalArgumentException("responseSignatureToleranceSeconds must be greater than zero");
        }
        this.responseSignatureToleranceSeconds = responseSignatureToleranceSeconds;
        this.httpClient = secureHttpClient(httpClient);
    }

    /**
     * 获取国际版 API Base URL。
     *
     * @return 不以斜杠结尾且不包含接口 Path、Query 或 Fragment 的 HTTPS 接口域名
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取 API Key。
     *
     * @return 商户 API Key；调用方不得写入日志或返回前端
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取 API Secret。
     *
     * @return 仅供 SDK 内存签名使用的 API Secret；调用方不得记录或传输明文
     */
    public String getApiSecret() {
        return apiSecret;
    }

    /**
     * 获取普通响应签名时间戳容忍窗口。
     *
     * @return 大于零的秒数；SDK 在解析响应 JSON 前使用该值 fail-close 验签
     */
    public long getResponseSignatureToleranceSeconds() {
        return responseSignatureToleranceSeconds;
    }

    /**
     * 获取可复用 HTTP 客户端。
     *
     * @return SDK 请求使用的 OkHttpClient 副本；连接重试和重定向固定关闭
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    private static OkHttpClient secureHttpClient(OkHttpClient httpClient) {
        OkHttpClient source = httpClient == null ? defaultHttpClient() : httpClient;
        return source.newBuilder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("baseUrl must be a valid HTTPS API domain without path", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getRawPath() != null && uri.getRawPath().length() > 0)) {
            throw new IllegalArgumentException("baseUrl must be an HTTPS API domain without path, query or fragment");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value.trim();
    }
}
