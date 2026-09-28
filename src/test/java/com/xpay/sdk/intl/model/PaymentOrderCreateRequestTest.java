package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpay.sdk.intl.XPayIntlSigner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 代收 SDK 发布前的可选字段兼容性回归测试。
 * <p>保护付款人联系方式和成功返回地址的序列化合同；仅在内存运行，不访问网络，
 * 不使用事务、缓存、异步任务或共享并发状态。断言失败立即阻断发布，不重试或降级。</p>
 */
public class PaymentOrderCreateRequestTest {

    /**
     * 验证显式填写的付款人联系方式完整发送，防止新版本回退已有能力。
     * 无参数、无返回值；仅在内存序列化，无事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldSerializePayerContact() throws Exception {
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        Payer payer = new Payer();
        payer.email = "payer@example.com";
        payer.phone = "+8801712345678";
        request.payer = payer;
        String json = new ObjectMapper().writeValueAsString(request);
        assertTrue(json.contains("\"email\":\"payer@example.com\""));
        assertTrue(json.contains("\"phone\":\"+8801712345678\""));
    }

    /**
     * 验证未填写新增可选字段时不改变旧版本请求结构。
     * 无参数、无返回值；只在内存执行，无事务、缓存、异步或共享并发状态。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldOmitNullOptionalFields() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new PaymentOrderCreateRequest());
        assertFalse(json.contains("\"payer\""));
        assertFalse(json.contains("\"successUrl\""));
    }

    /**
     * 验证成功地址不被裁剪、重新编码或移出五行签名原文。
     * 无参数、无返回值；只使用固定测试签名材料，无网络、事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化或签名失败时直接阻断测试，不重试或降级
     */
    @Test
    public void shouldPreserveSuccessUrlInSignedBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        String legacyBody = mapper.writeValueAsString(request);
        String successUrl = " https://merchant.example/return?next=%2Forders%2F1&label=a+b ";
        request.successUrl = successUrl;
        String body = mapper.writeValueAsString(request);
        assertEquals(successUrl, mapper.readTree(body).get("successUrl").asText());
        assertEquals("POST\n/intl/v1/payment/order/create\n1234567890\nrelease-test\n" + body + "\n",
                XPayIntlSigner.buildSignPayload("POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", body));
        assertNotEquals(
                XPayIntlSigner.sign("test-only-not-a-credential", "POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", legacyBody),
                XPayIntlSigner.sign("test-only-not-a-credential", "POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", body));
    }
}
