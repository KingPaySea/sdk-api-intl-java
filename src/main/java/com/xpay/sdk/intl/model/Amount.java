package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 国际版创建订单使用的正金额对象。
 * <p>
 * 创建请求只提交主单位金额字符串，币种与精度由已开通的 {@code productCode} 决定；使用字符串可避免
 * JSON number、科学计数法或客户端浮点运算造成资金精度偏差。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Amount {

    /** 金额主单位字符串，必须大于零，不能为空；小数位必须符合产品对应币种精度。 */
    public String value;
}
