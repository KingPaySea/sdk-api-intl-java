package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 国际版银行账户代付的收款人信息。
 * <p>
 * 所有账号字段均以字符串承载，避免前导零丢失；SDK 不校验具体银行能力，服务端会按商户已开通产品和
 * 支持的银行目录执行最终 fail-close 校验。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Recipient {

    /** 代付产品支持的收款银行编码，不能为空，最长 20 字符。 */
    public String bankCode;

    /** 收款银行或钱包账号，不能为空，最长 50 字符；PH 银行账户产品当前最长 25 字符。 */
    public String accountNo;

    /** 收款账户持有人姓名，不能为空，最长 64 字符。 */
    public String accountName;
}
