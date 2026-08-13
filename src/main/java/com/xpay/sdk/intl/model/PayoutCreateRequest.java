package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 创建国际版代付订单的请求模型。
 * <p>
 * 字段按开发者平台 lowerCamelCase 协议直接序列化；{@code merchantOrderNo} 同时承担创建幂等和查单关联。
 * SDK 不接受内部 provider、渠道或路由参数，服务端按商户权限、产品和银行能力执行最终 fail-close 校验。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayoutCreateRequest {

    /** 商户订单号，同一商户下用于创建幂等和查单，长度 5 至 32，不能为空。 */
    public String merchantOrderNo;

    /** 平台为商户开通的代付产品编码，长度 1 至 32；调用方应视为不透明值，不能为空。 */
    public String productCode;

    /** 创建金额对象，只包含大于零的主单位字符串 {@code value}，不能为空。 */
    public Amount amount;

    /** 银行账户收款人信息，账号保持字符串避免前导零丢失，不能为空。 */
    public Recipient recipient;

    /**
     * 可选商户关联数据，允许为空，最多 20 个 lowerCamelCase 字符串键值；不参与幂等、路由、清算或风控。
     * 禁止放入 API/Webhook 密钥、签名、认证 Token、银行卡号、完整收款账号等敏感信息；幂等重复请求只返回
     * 首次受理请求保存的不可变快照，后续请求不会合并或覆盖。
     */
    public Map<String, String> customData;
}
