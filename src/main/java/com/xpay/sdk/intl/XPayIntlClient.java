package com.xpay.sdk.intl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpay.sdk.intl.model.PaymentOrderCreateRequest;
import com.xpay.sdk.intl.model.PayoutCreateRequest;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * XPay 国际版 OpenAPI Java 客户端。
 * <p>
 * 客户端严格封装开发者平台最终发布的余额查询、收款创建/查单和代付创建/查单五个接口，并在平台 HTTPS
 * 接口域名后统一拼接 {@code /intl/v1} 版本路径，负责 lowerCamelCase JSON 序列化、五行请求签名、
 * 标准 Header 发送和普通响应验签。SDK 不保存交易状态、不自动重试创建请求、不推断产品或路由能力；
 * 已验签 HTTP 503 {@code ORDER_RESULT_UNKNOWN} 或网络结果不可验证时，商户必须先按原
 * {@code merchantOrderNo} 查单，有界查询后仅可用同一单号和完全一致的请求串行安全重试。
 * </p>
 */
public class XPayIntlClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String API_VERSION_PREFIX = "/intl/v1";

    private final XPayIntlConfig config;

    private final ObjectMapper objectMapper;

    /**
     * 创建可复用的国际版 API 客户端。
     * <p>
     * 客户端和底层 OkHttpClient 可被多个线程共享；构造过程不访问网络、数据库或缓存，不启动事务或异步任务。
     * 配置为空时立即 fail-close 抛出参数异常。
     * </p>
     *
     * @param config 已校验的 HTTPS 接口域名、API Key、API Secret 和响应验签配置
     * @throws IllegalArgumentException 配置为空时抛出
     */
    public XPayIntlClient(XPayIntlConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 查询商户全部可用国家币种账户余额。
     * <p>
     * 方法只执行签名 HTTPS GET，不开启本地事务、不缓存余额，也不自动重试；响应在返回前必须通过 API Secret
     * 验签。调用方不得把一次余额快照当作后续路由或资金可用性的永久保证。
     * </p>
     *
     * @return 已通过响应验签的原始 HTTP 响应，Body 为 {@code balances} 列表
     * @throws XPayIntlException 网络或响应验签失败时抛出；无签名非2xx前置错误作为不可信诊断响应返回
     */
    public XPayIntlResponse getBalances() {
        return getBalances(null, null);
    }

    /**
     * 按国家查询商户余额列表。
     * <p>
     * 本方法只把国家作为可选筛选拼入签名 requestUrl，不修改交易状态、不缓存结果、不自动重试。筛选无匹配时
     * 服务端返回空 {@code balances} 列表。
     * </p>
     *
     * @param country ISO 3166-1 alpha-2 国家编码，例如 PH
     * @return 已通过响应验签的原始 HTTP 响应，Body 为 {@code balances} 列表
     * @throws IllegalArgumentException country 为空时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public XPayIntlResponse getBalancesByCountry(String country) {
        return getBalances(required(country, "country"), null);
    }

    /**
     * 按币种查询商户余额列表。
     * <p>
     * 本方法只把币种作为可选筛选拼入签名 requestUrl，不修改交易状态、不缓存结果、不自动重试。筛选无匹配时
     * 服务端返回空 {@code balances} 列表。
     * </p>
     *
     * @param currency ISO 4217 币种编码，例如 PHP
     * @return 已通过响应验签的原始 HTTP 响应，Body 为 {@code balances} 列表
     * @throws IllegalArgumentException currency 为空时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public XPayIntlResponse getBalancesByCurrency(String currency) {
        return getBalances(null, required(currency, "currency"));
    }

    /**
     * 按可选国家和币种筛选查询商户余额列表。
     * <p>
     * country 与 currency 均可为空；都为空表示查询当前商户全部 ACTIVE 国家币种账户，只传其中之一表示按该维度
     * 过滤，同时传入表示查询精确国家币种账户。SDK 会按实际传参生成签名 requestUrl，不发送空Query参数。
     * </p>
     *
     * @param country 可选 ISO 3166-1 alpha-2 国家编码，例如 PH；为空表示不限国家
     * @param currency 可选 ISO 4217 币种编码，例如 PHP；为空表示不限币种
     * @return 已通过响应验签的原始 HTTP 响应，Body 为 {@code balances} 列表
     * @throws IllegalArgumentException country 或 currency 传入空白字符串时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出；无签名非2xx前置错误作为不可信诊断响应返回
     */
    public XPayIntlResponse getBalances(String country, String currency) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        putOptionalQuery(query, "country", country);
        putOptionalQuery(query, "currency", currency);
        return get("/balance/query", query);
    }

    /**
     * 创建国际版收款订单。
     * <p>
     * 服务端以 {@code merchantOrderNo} 实现创建幂等；SDK 不发送额外幂等 Header、不自动重试，也不参与平台交易
     * 事务。已验签 HTTP 503 {@code ORDER_RESULT_UNKNOWN} 不包含平台订单号；调用方必须先按原商户订单号查单，
     * 有界查询后仅可用同一单号和完全一致的请求串行重试。超时、断连或无法验证的响应也按该保守边界恢复。
     * </p>
     *
     * @param request lowerCamelCase 收款订单创建请求，不能为空
     * @return 已通过响应验签的原始 HTTP 响应；HTTP 200 仍需按 Body {@code status} 判断业务状态，503未知结果仅含错误体
     * @throws IllegalArgumentException request 为空时抛出
     * @throws XPayIntlException 网络、JSON 序列化或响应验签失败时抛出
     */
    public XPayIntlResponse createPaymentOrder(PaymentOrderCreateRequest request) {
        return post("/payment/order/create", requiredRequest(request, "request"));
    }

    /**
     * 按商户订单号查询国际版收款订单。
     * <p>
     * 本方法用于常规查单和创建请求未知结果恢复，不开启本地事务、不缓存结果且不自动重试；返回前执行响应
     * fail-close 验签。并发查询只读取平台当前状态，不替调用方解决本地状态更新并发。
     * </p>
     *
     * @param merchantOrderNo 创建时使用的商户订单号
     * @return 已通过响应验签的原始 HTTP 响应
     * @throws IllegalArgumentException merchantOrderNo 为空时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public XPayIntlResponse getPaymentOrderByMerchantOrderNo(String merchantOrderNo) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("merchantOrderNo", required(merchantOrderNo, "merchantOrderNo"));
        return get("/payment/order/query", query);
    }

    /**
     * 创建国际版代付订单。
     * <p>
     * 服务端以 {@code merchantOrderNo} 实现创建幂等；SDK 不发送额外幂等 Header、不自动重试，也不参与扣款、
     * 路由或渠道事务。已验签 HTTP 503 {@code ORDER_RESULT_UNKNOWN} 不包含平台订单号；必须先按原商户订单号查单，
     * 有界查询后仅可用同一单号和完全一致的请求串行重试，避免资金类重复提交。超时、断连或无法验证的响应也按该保守边界恢复。
     * </p>
     *
     * @param request lowerCamelCase 代付订单创建请求，包含字符串账号和金额对象，不能为空
     * @return 已通过响应验签的原始 HTTP 响应；HTTP 200 不代表代付终态成功，503未知结果仅含错误体
     * @throws IllegalArgumentException request 为空时抛出
     * @throws XPayIntlException 网络、JSON 序列化或响应验签失败时抛出
     */
    public XPayIntlResponse createPayout(PayoutCreateRequest request) {
        return post("/payout/order/create", requiredRequest(request, "request"));
    }

    /**
     * 按商户订单号查询国际版代付订单。
     * <p>
     * 本方法是资金类创建未知结果的必需恢复路径，不开启本地事务、不缓存结果且不自动重试；响应签名无效时
     * fail-close 抛出异常，调用方不得使用未验证 Body 更新资金状态。
     * </p>
     *
     * @param merchantOrderNo 创建时使用的商户订单号
     * @return 已通过响应验签的原始 HTTP 响应
     * @throws IllegalArgumentException merchantOrderNo 为空时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public XPayIntlResponse getPayoutByMerchantOrderNo(String merchantOrderNo) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("merchantOrderNo", required(merchantOrderNo, "merchantOrderNo"));
        return get("/payout/order/query", query);
    }

    private XPayIntlResponse post(String path, Object request) {
        return execute("POST", path, null, writeJson(request));
    }

    private XPayIntlResponse get(String path, Map<String, String> query) {
        return execute("GET", path, query, "");
    }

    private XPayIntlResponse execute(String method, String path, Map<String, String> query, String rawBody) {
        try {
            String normalizedPath = API_VERSION_PREFIX + normalizePath(path);
            String rawQuery = buildRawQuery(query);
            String url = config.getBaseUrl() + normalizedPath + (rawQuery.length() == 0 ? "" : "?" + rawQuery);
            URI uri = new URI(url);
            String requestUrl = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = XPayIntlSigner.sign(config.getApiSecret(), method, requestUrl,
                    timestamp, nonce, rawBody);

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Api-Key", config.getApiKey())
                    .header("Timestamp", timestamp)
                    .header("Nonce", nonce)
                    .header("Signature", signature);
            Request httpRequest;
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json");
                httpRequest = builder.post(RequestBody.create(rawBody.getBytes(StandardCharsets.UTF_8), JSON)).build();
            } else {
                httpRequest = builder.get().build();
            }
            return call(httpRequest);
        } catch (XPayIntlException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new XPayIntlException("xpay intl request failed", ex);
        }
    }

    private XPayIntlResponse call(Request request) throws Exception {
        Response response = config.getHttpClient().newCall(request).execute();
        try {
            byte[] body = response.body() == null ? new byte[0] : response.body().bytes();
            String timestamp = response.header("Timestamp");
            String nonce = response.header("Nonce");
            String signature = response.header("Signature");
            boolean anySignatureHeader = !isBlank(timestamp) || !isBlank(nonce) || !isBlank(signature);
            boolean allSignatureHeaders = !isBlank(timestamp) && !isBlank(nonce) && !isBlank(signature);
            if (!allSignatureHeaders) {
                if ((response.code() < 200 || response.code() >= 300) && !anySignatureHeader) {
                    return new XPayIntlResponse(response.code(), response.headers().toMultimap(), body, false);
                }
                throw new XPayIntlException("xpay intl response signature headers are missing");
            }
            if (!XPayIntlResponseVerifier.verify(timestamp, nonce, signature, body,
                    config.getApiSecret(), config.getResponseSignatureToleranceSeconds())) {
                throw new XPayIntlException("xpay intl response signature verification failed");
            }
            return new XPayIntlResponse(response.code(), response.headers().toMultimap(), body, true);
        } finally {
            response.close();
        }
    }

    private String writeJson(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw new XPayIntlException("json serialization failed", ex);
        }
    }

    private String buildRawQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(query.size() * 16);
        int index = 0;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (index > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            index++;
        }
        return builder.toString();
    }

    private static String normalizePath(String path) {
        String value = required(path, "path");
        if (!value.startsWith("/") || value.indexOf("://") >= 0 || value.indexOf('?') >= 0
                || value.indexOf('#') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("path must be a relative path without query or fragment");
        }
        return value;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(required(value, "queryValue"), "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception ex) {
            throw new XPayIntlException("url encoding failed", ex);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value.trim();
    }

    private static <T> T requiredRequest(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static void putOptionalQuery(Map<String, String> query, String field, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        query.put(field, trimmed);
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }
}
