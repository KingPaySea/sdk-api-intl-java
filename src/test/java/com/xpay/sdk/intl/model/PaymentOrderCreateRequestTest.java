package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 代收创建请求付款人字段的 JSON 序列化回归测试。
 * <p>
 * 用于保证升级后的 SDK 会发送商户显式填写的付款人联系方式，同时在未填写时保持与 1.0.0 相同的请求结构。
 * 测试只在本地内存中执行序列化，不调用网络，不涉及事务、缓存、异步任务或并发状态。
 * </p>
 */
public class PaymentOrderCreateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证非空付款人联系方式会按公开合同序列化为嵌套 payer 对象。
     * 无参数、无返回值；序列化异常时立即失败，不做重试或降级。本测试不涉及事务、缓存、异步任务或并发状态。
     *
     * @throws Exception Jackson 无法序列化请求时抛出，测试直接失败且不降级
     */
    @Test
    public void shouldSerializePayerContact() throws Exception {
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        Payer payer = new Payer();
        payer.email = "payer@example.com";
        payer.phone = "+8801712345678";
        request.payer = payer;

        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"payer\""));
        assertTrue(json.contains("\"email\":\"payer@example.com\""));
        assertTrue(json.contains("\"phone\":\"+8801712345678\""));
    }

    /**
     * 验证未填写付款人信息时不输出 payer 字段，保持旧版请求兼容性。
     * 无参数、无返回值；序列化异常时立即失败，不做重试或降级。本测试不涉及事务、缓存、异步任务或并发状态。
     *
     * @throws Exception Jackson 无法序列化请求时抛出，测试直接失败且不降级
     */
    @Test
    public void shouldOmitNullPayerForBackwardCompatibility() throws Exception {
        String json = objectMapper.writeValueAsString(new PaymentOrderCreateRequest());

        assertFalse(json.contains("\"payer\""));
    }
}
