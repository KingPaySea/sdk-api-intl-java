package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 创建国际版收款订单的请求模型。
 * <p>
 * 字段按开发者平台 lowerCamelCase 协议直接序列化；{@code merchantOrderNo} 同时承担创建幂等和查单关联。
 * SDK 只承载公开产品合同，不推导渠道、银行或处理器参数，服务端负责权限、产品和金额的最终 fail-close 校验。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentOrderCreateRequest {

    /** 商户订单号，同一商户下用于创建幂等和查单，长度 5 至 32，不能为空。 */
    public String merchantOrderNo;

    /** 平台为商户开通的收款产品编码，长度 1 至 32；调用方应视为不透明值，不能为空。 */
    public String productCode;

    /** 创建金额对象，只包含大于零的主单位字符串 {@code value}，不能为空。 */
    public Amount amount;

    /** 商户侧订单或商品描述，不能为空，最长 128 字符；不得包含敏感数据。 */
    public String orderDescription;

    /** 付款人联系方式；孟加拉代收产品要求提供邮箱，响应和通知可返回付款人摘要，手机号只返回后四位。 */
    public Payer payer;

    /**
     * 可选商户关联数据，允许为空，最多 20 个 lowerCamelCase 字符串键值；不参与幂等、路由、清算或风控。
     * 禁止放入 API/Webhook 密钥、签名、认证 Token、银行卡号、完整收款账号、手机号、邮箱等敏感信息；
     * 代收付款人联系方式应使用 {@link #payer}；幂等重复请求只返回首次受理请求保存的不可变快照，后续请求
     * 不会合并或覆盖。
     */
    public Map<String, String> customData;
}
