package com.xpay.sdk.intl.manual;

import com.xpay.sdk.intl.IntlLocalTestSupport;
import com.xpay.sdk.intl.Keys;
import com.xpay.sdk.intl.XPayIntlClient;
import com.xpay.sdk.intl.XPayIntlException;

/**
 * 国际版余额查询接口本地手工联调工具。
 * <p>
 * 直接运行本类的 {@link #main(String[])} 即调用余额查询接口；默认读取模块内
 * {@code intl-sdk-local.properties}，也可传入一个配置文件路径。工具只发起一次只读请求并输出完整 HTTP 响应。
 * </p>
 */
public final class BalanceQueryLocalTestTool {

    private BalanceQueryLocalTestTool() {
    }

    /**
     * 调用 {@code GET /intl/v1/balance/query}。
     *
     * @param args 可选的 UTF-8 {@code intl-sdk-local.properties} 文件路径
     * @throws IllegalArgumentException 参数数量或 SDK 配置不合法时抛出
     * @throws IllegalStateException 配置、环境门禁或响应成功合同不满足时抛出
     * @throws XPayIntlException 网络或响应验签失败时抛出
     */
    public static void main(String[] args) {
        Keys keys = IntlLocalTestSupport.loadKeys(args, "balance-query", false);
        XPayIntlClient client = keys.createClient();
        IntlLocalTestSupport.printSuccessful("balance-query",
                client.getBalances(keys.optional("country", null), keys.optional("currency", null)));
    }
}
