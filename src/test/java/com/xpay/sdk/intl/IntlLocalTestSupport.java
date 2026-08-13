package com.xpay.sdk.intl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 国际版 SDK 手工联调工具的公共支持。
 * <p>
 * 本类只负责统一加载本地配置、执行环境门禁、输出完整 HTTP 响应，并区分已验签稳定拒绝与
 * HTTP 503 {@code ORDER_RESULT_UNKNOWN}/无法验证的未知结果。它不发送请求、不保存凭据、不自动重试创建请求，
 * 也不输出本地 API Secret 或请求签名。
 * </p>
 */
public final class IntlLocalTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ORDER_RESULT_UNKNOWN = "ORDER_RESULT_UNKNOWN";

    private IntlLocalTestSupport() {
    }

    /**
     * 加载本地联调配置并执行环境及写操作门禁。
     * <p>
     * 参数数量、配置文件或门禁不满足时立即失败，不回退默认凭据；本方法不发送请求、不启动事务、
     * 不访问缓存或异步线程。
     * </p>
     *
     * @param args 可选的本地 properties 文件路径，最多一个参数
     * @param operation 当前联调操作名称，仅用于错误提示和目标摘要
     * @param writeOperation 是否为创建类写操作；为 true 时要求显式写确认
     * @return 已通过当前操作门禁的本地配置
     * @throws IllegalArgumentException 参数数量不合法时抛出
     * @throws IllegalStateException 配置缺失或环境、写操作门禁不满足时抛出
     */
    public static Keys loadKeys(String[] args, String operation, boolean writeOperation) {
        if (args.length > 1) {
            throw new IllegalArgumentException("usage: " + operation
                    + " [path/to/intl-sdk-local.properties]");
        }
        Keys keys = Keys.load(args.length == 1 ? args[0] : null);
        keys.assertOperationAllowed(operation, writeOperation);
        System.out.println("Target: " + keys.targetSummary());
        return keys;
    }

    /**
     * 输出完整 HTTP 响应并要求响应为已验签 HTTP 200。
     * <p>
     * 响应不满足成功合同时立即失败，禁止调用方使用未验签结果更新交易或资金状态；本方法仅同步读取内存响应，
     * 不参与事务、缓存、异步任务或自动重试。
     * </p>
     *
     * @param operation 当前联调操作名称
     * @param response SDK 返回的原始响应封装
     * @throws IllegalStateException 响应不是已验签 HTTP 200 时抛出
     */
    public static void printSuccessful(String operation, XPayIntlResponse response) {
        printResponse(operation, response);
        if (!response.isSuccessful()) {
            throw new IllegalStateException(operation
                    + " response is not a verified HTTP 200 business response; do not use it as success");
        }
    }

    /**
     * 输出完整创建响应，并区分已验签拒绝与未知结果。
     * <p>
     * 只有已验签 HTTP 200 正常返回；已验签4xx按确定拒绝失败，HTTP 503
     * {@code ORDER_RESULT_UNKNOWN}、无法验证响应或其他不可确认结果则引导按原订单号查单。方法不自动重试，
     * 不建立事务、不写缓存或数据库，也不启动异步任务。
     * </p>
     *
     * @param operation 当前创建操作名称
     * @param queryTool 对应查单工具类名
     * @param merchantOrderNo 创建前已持久化的商户订单号
     * @param response SDK 返回的创建响应
     * @throws XPayIntlException 创建响应不满足成功合同时抛出，异常提示保留原订单号查单
     */
    public static void printCreateResult(String operation,
                                         String queryTool,
                                         String merchantOrderNo,
                                         XPayIntlResponse response) {
        printResponse(operation, response);
        if (response.isSuccessful()) {
            return;
        }
        String errorCode = errorCode(response);
        if (response.isSignatureVerified() && response.statusCode >= 400 && response.statusCode < 500) {
            throw new XPayIntlException("create was rejected with verified HTTP " + response.statusCode
                    + " and error.code=" + safeErrorCode(errorCode)
                    + "; do not treat this stable rejection as an unknown result");
        }
        if (response.isSignatureVerified()
                && response.statusCode == 503
                && !ORDER_RESULT_UNKNOWN.equals(errorCode)) {
            throw unknownCreateResult(queryTool, merchantOrderNo,
                    new XPayIntlException("create returned contract-invalid HTTP 503 error.code="
                            + safeErrorCode(errorCode)));
        }
        throw unknownCreateResult(queryTool, merchantOrderNo,
                new XPayIntlException("create returned HTTP " + response.statusCode
                        + " with signatureVerified=" + response.isSignatureVerified()
                        + " and error.code=" + safeErrorCode(errorCode)));
    }

    /**
     * 将创建调用异常包装为要求原订单号查单的保守恢复异常。
     * <p>
     * 本方法只构造异常，不判断核心订单是否已创建，不执行重试、事务、缓存、数据库或异步操作。提示要求
     * 先查单；安全重试只能在有界查询仍不存在后，使用同一单号和完全一致的原请求串行执行。
     * </p>
     *
     * @param queryTool 对应查单工具类名
     * @param merchantOrderNo 创建前已持久化的商户订单号
     * @param cause 原始 SDK 异常
     * @return 包含安全恢复指引且保留原始原因的 SDK 异常
     */
    public static XPayIntlException unknownCreateResult(String queryTool,
                                                        String merchantOrderNo,
                                                        XPayIntlException cause) {
        return new XPayIntlException("create result may be unknown; query first with merchantOrderNo="
                + merchantOrderNo + " and run " + queryTool
                + " with the same intl-sdk-local.properties. If bounded queries remain not found,"
                + " a safe retry must use the same merchantOrderNo and unchanged request without concurrency", cause);
    }

    private static String errorCode(XPayIntlResponse response) {
        if (response == null || response.body == null || response.body.length == 0) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response.body);
            JsonNode code = root.path("error").path("code");
            return code.isTextual() ? code.textValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeErrorCode(String errorCode) {
        return errorCode == null || errorCode.length() == 0 ? "UNKNOWN" : errorCode;
    }

    private static void printResponse(String operation, XPayIntlResponse response) {
        System.out.println(operation + " HTTP " + response.statusCode
                + ", signatureVerified=" + response.isSignatureVerified());
        System.out.println("Response headers: " + response.headers);
        System.out.println("Response body (UTF-8):");
        System.out.println(response.bodyAsString());
    }
}
