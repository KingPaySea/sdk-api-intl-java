package com.xpay.sdk.intl.manual;

import com.xpay.sdk.intl.IntlLocalTestSupport;
import com.xpay.sdk.intl.Keys;
import com.xpay.sdk.intl.XPayIntlClient;
import com.xpay.sdk.intl.XPayIntlException;
import com.xpay.sdk.intl.XPayIntlResponse;
import com.xpay.sdk.intl.model.Amount;
import com.xpay.sdk.intl.model.PayoutCreateRequest;
import com.xpay.sdk.intl.model.Recipient;

/**
 * 国际版代付订单创建接口本地手工联调工具。
 * <p>
 * 直接运行本类的 {@link #main(String[])} 即调用代付创建接口。运行前必须持久化配置中的
 * {@code payoutMerchantOrderNo}，并显式通过写操作及目标环境门禁。本工具绝不自动重试；创建结果未知时，
 * 只提示使用原商户订单号运行 {@link PayoutQueryLocalTestTool} 查单。仅在有界退避查单仍不存在后，才可由
 * 操作员串行重放同一商户订单号、参数完全一致的原创建请求，不得换号或并发重试，避免重复出款。
 * </p>
 */
public final class PayoutCreateLocalTestTool {

    private PayoutCreateLocalTestTool() {
    }

    /**
     * 调用 {@code POST /intl/v1/payout/order/create}。
     *
     * @param args 可选的 UTF-8 {@code intl-sdk-local.properties} 文件路径
     * @throws IllegalArgumentException 参数数量、请求模型或 SDK 配置不合法时抛出
     * @throws IllegalStateException 配置、生产环境或写操作门禁不满足时抛出
     * @throws XPayIntlException 创建结果可能未知时抛出；必须先使用原订单号查单，本工具不自动重试；有界查单仍不存在时，
     *         仅允许操作员串行重放同一订单号且参数完全一致的原请求
     */
    public static void main(String[] args) {
        Keys keys = IntlLocalTestSupport.loadKeys(args, "payout-create", true);
        String merchantOrderNo = keys.required("payoutMerchantOrderNo");

        Amount amount = new Amount();
        amount.value = keys.required("payoutAmount");
        Recipient recipient = new Recipient();
        recipient.bankCode = keys.required("recipientBankCode");
        recipient.accountNo = keys.required("recipientAccountNo");
        recipient.accountName = keys.required("recipientAccountName");
        PayoutCreateRequest request = new PayoutCreateRequest();
        request.merchantOrderNo = merchantOrderNo;
        request.productCode = keys.required("payoutProductCode");
        request.amount = amount;
        request.recipient = recipient;

        System.out.println("Persisted payout merchantOrderNo: " + merchantOrderNo);
        XPayIntlResponse response;
        try {
            XPayIntlClient client = keys.createClient();
            response = client.createPayout(request);
        } catch (XPayIntlException ex) {
            throw IntlLocalTestSupport.unknownCreateResult(
                    PayoutQueryLocalTestTool.class.getSimpleName(), merchantOrderNo, ex);
        }
        IntlLocalTestSupport.printCreateResult("payout-create",
                PayoutQueryLocalTestTool.class.getSimpleName(), merchantOrderNo, response);
    }
}
