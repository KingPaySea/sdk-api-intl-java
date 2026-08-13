package com.xpay.sdk.intl.manual;

import com.xpay.sdk.intl.IntlLocalTestSupport;
import com.xpay.sdk.intl.Keys;
import com.xpay.sdk.intl.XPayIntlClient;
import com.xpay.sdk.intl.XPayIntlException;

/**
 * 国际版支付订单查询接口本地手工联调工具。
 * <p>
 * 直接运行本类的 {@link #main(String[])}，使用配置中的原 {@code paymentMerchantOrderNo} 查单。
 * 本工具也是支付创建超时、断连或结果未知后的唯一手工恢复入口，不会重新提交创建请求。
 * </p>
 */
public final class PaymentQueryLocalTestTool {

    private PaymentQueryLocalTestTool() {
    }

    /**
     * 调用 {@code GET /intl/v1/payment/order/query}。
     *
     * @param args 可选的 UTF-8 {@code intl-sdk-local.properties} 文件路径
     * @throws IllegalArgumentException 参数数量或 SDK 配置不合法时抛出
     * @throws IllegalStateException 配置、环境门禁或响应成功合同不满足时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public static void main(String[] args) {
        Keys keys = IntlLocalTestSupport.loadKeys(args, "payment-query", false);
        XPayIntlClient client = keys.createClient();
        IntlLocalTestSupport.printSuccessful("payment-query",
                client.getPaymentOrderByMerchantOrderNo(keys.required("paymentMerchantOrderNo")));
    }
}
