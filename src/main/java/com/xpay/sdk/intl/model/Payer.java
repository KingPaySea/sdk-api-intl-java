package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 国际版代收订单的付款人联系方式。
 * <p>
 * 本模型仅用于商户服务端创建代收订单时提交必要联系方式。孟加拉代收产品要求传入 {@link #email}；
 * {@link #phone} 为选填字段。SDK 不在本地推导国家、渠道或风控规则，服务端会按商户已开通产品执行最终
 * fail-close 校验。查询响应和 Webhook 可返回付款人摘要，其中手机号只返回后四位。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Payer {

    /** 付款人邮箱；孟加拉代收产品必填，其他代收产品默认选填，最长 254 字符。 */
    public String email;

    /** 付款人手机号；选填，建议使用带国家区号的国际格式，最长 25 字符。 */
    public String phone;
}
