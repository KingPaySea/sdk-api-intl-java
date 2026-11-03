package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 国际版银行账户或巴西PIX代付的收款人信息。
 * <p>
 * 所有账号字段均以字符串承载，避免前导零丢失；SDK 不校验具体银行能力，服务端会按商户已开通产品和
 * 支持的银行目录执行最终 fail-close 校验。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Recipient {

    /** 收款银行编码，最长20字符；与accountType恰填其一，巴西PIX代付不传。 */
    public String bankCode;

    /** BR_BRL_PAYOUT_INSTANT必填的PIX类型：CPF/CNPJ/PHONE/EMAIL/EVP；银行代付不传。 */
    public String accountType;

    /** 收款银行、钱包账号或与accountType匹配的PIX Key，不能为空，最长50字符；PH最多25字符。 */
    public String accountNo;

    /** 收款账户持有人姓名，不能为空，最长 64 字符。 */
    public String accountName;
}
