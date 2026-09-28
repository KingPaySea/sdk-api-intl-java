import com.xpay.sdk.intl.XPayIntlClient;
import com.xpay.sdk.intl.XPayIntlConfig;
import com.xpay.sdk.intl.XPayIntlException;
import com.xpay.sdk.intl.XPayIntlResponse;
import com.xpay.sdk.intl.model.Amount;
import com.xpay.sdk.intl.model.Payer;
import com.xpay.sdk.intl.model.PaymentOrderCreateRequest;

/**
 * XPay 国际版 Java SDK 服务端快速接入示例。
 * <p>
 * Base URL、API Key 和 API Secret 只从进程环境变量读取。默认仅查询余额；只有显式设置
 * {@code INTL_ENABLE_WRITES=true} 才创建收款订单。写操作会在 {@code INTL_API_BASE_URL} 指向的环境创建
 * 真实订单，运行前必须确认环境、产品编码和商户订单号。孟加拉代收产品还必须通过
 * {@code INTL_PAYMENT_PAYER_EMAIL} 提供付款人邮箱；{@code INTL_PAYMENT_PAYER_PHONE} 为选填。
 * 已开通的收银台产品可通过 {@code INTL_PAYMENT_SUCCESS_URL} 传入商户指定的 HTTPS 成功返回地址，无需提前登记域名；原文不可在重试时改变。
 * </p>
 */
public final class Quickstart {

    private Quickstart() {
    }

    /**
     * 执行余额联调，并在明确启用时创建一笔收款订单。
     * <p>
     * 失败策略：环境变量缺失、HTTP 异常、响应验签失败或非 2xx 立即终止；SDK 不自动重试。已验签 HTTP 503
     * {@code ORDER_RESULT_UNKNOWN} 不包含平台订单号，调用方必须先使用原 {@code merchantOrderNo} 查单；
     * 有界查询后的安全重试也必须使用同一单号和完全一致的请求。本示例不使用数据库事务、缓存、异步任务或并发请求。
     * </p>
     *
     * @param args 未使用，全部配置来自服务端环境变量
     * @throws IllegalStateException 必要环境变量缺失、响应未验签或 HTTP 非 2xx 时抛出
     * @throws XPayIntlException 请求签名、网络调用、JSON 序列化或普通响应验签失败时抛出
     */
    public static void main(String[] args) {
        XPayIntlConfig config = new XPayIntlConfig(
                requiredEnv("INTL_API_BASE_URL"),
                requiredEnv("INTL_API_KEY"),
                requiredEnv("INTL_API_SECRET"));
        XPayIntlClient client = new XPayIntlClient(config);
        printSuccessful("balance", client.getBalances(
                optionalEnv("INTL_COUNTRY"), optionalEnv("INTL_CURRENCY")));

        if (!"true".equalsIgnoreCase(System.getenv("INTL_ENABLE_WRITES"))) {
            System.out.println("Write example is disabled. Set INTL_ENABLE_WRITES=true explicitly to enable it.");
            return;
        }
        System.err.println("WARNING: write mode creates a real order; verify INTL_API_BASE_URL first.");

        Amount amount = new Amount();
        amount.value = requiredEnv("INTL_PAYMENT_AMOUNT");
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        request.merchantOrderNo = requiredEnv("INTL_MERCHANT_ORDER_NO");
        request.productCode = requiredEnv("INTL_PAYMENT_PRODUCT_CODE");
        request.amount = amount;
        request.orderDescription = "SDK quickstart order";
        // 成功地址参与业务幂等，禁止使用会 trim 或重新编码的通用配置读取方法。
        String successUrl = System.getenv("INTL_PAYMENT_SUCCESS_URL");
        request.successUrl = successUrl == null || successUrl.isEmpty() ? null : successUrl;
        Payer payer = optionalPayer();
        if (payer != null) {
            request.payer = payer;
        }

        printSuccessful("payment_order", client.createPaymentOrder(request));
    }

    private static Payer optionalPayer() {
        String email = optionalEnv("INTL_PAYMENT_PAYER_EMAIL");
        String phone = optionalEnv("INTL_PAYMENT_PAYER_PHONE");
        if (email == null && phone == null) {
            return null;
        }
        Payer payer = new Payer();
        payer.email = email;
        payer.phone = phone;
        return payer;
    }

    private static void printSuccessful(String operation, XPayIntlResponse response) {
        System.out.println(operation + " HTTP " + response.statusCode);
        if (!response.isSignatureVerified()) {
            throw new IllegalStateException(operation + " response is unsigned and cannot be trusted");
        }
        System.out.println(response.bodyAsString());
        if (!response.isSuccessful()) {
            throw new IllegalStateException(operation + " request was not accepted");
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().length() == 0) {
            throw new IllegalStateException("missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.trim().length() == 0 ? null : value.trim();
    }
}
