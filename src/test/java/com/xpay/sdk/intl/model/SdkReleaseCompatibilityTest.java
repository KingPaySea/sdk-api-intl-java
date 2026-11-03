package com.xpay.sdk.intl.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpay.sdk.intl.XPayIntlSigner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 国际版 SDK 发布前的收款人和代收可选字段兼容性检查。
 * <p>保证新增 PIX 类型不改变既有银行代付、付款人及成功返回地址的 JSON 合同。
 * 仅使用固定虚构数据在内存运行，不访问支付接口，不涉及事务、缓存、异步任务或共享并发状态；
 * 序列化或断言异常立即阻断发布，不重试或降级。</p>
 */
public class SdkReleaseCompatibilityTest {

    /**
     * 验证银行账号保留前导零且不发送未设置的 PIX 类型，保护既有银行代付接入。
     * 无参数、无返回值；只在内存运行，无事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldPreserveLegacyBankRecipient() throws Exception {
        Recipient recipient = new Recipient();
        recipient.bankCode = "DEMO_BANK";
        recipient.accountNo = "0000000001";
        recipient.accountName = "Demo Recipient";
        JsonNode body = new ObjectMapper().valueToTree(recipient);
        assertEquals("DEMO_BANK", body.path("bankCode").asText());
        assertTrue(body.path("accountNo").isTextual());
        assertEquals("0000000001", body.path("accountNo").asText());
        assertFalse(body.has("accountType"));
    }

    /**
     * 验证五种 PIX 类型作为嵌套 recipient 字段发送，且不混入银行编码。
     * 无参数、无返回值；不执行服务端格式校验或真实代付，无事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldSerializeAllPixRecipientTypes() throws Exception {
        String[][] fixtures = {
                {"CPF", "00000000000"}, {"CNPJ", "00000000000000"},
                {"PHONE", "+5500000000000"}, {"EMAIL", "recipient@example.test"},
                {"EVP", "00000000-0000-0000-0000-000000000000"}
        };
        ObjectMapper mapper = new ObjectMapper();
        for (String[] fixture : fixtures) {
            Recipient recipient = new Recipient();
            recipient.accountType = fixture[0];
            recipient.accountNo = fixture[1];
            recipient.accountName = "Demo Recipient";
            PayoutCreateRequest request = new PayoutCreateRequest();
            request.productCode = "BR_BRL_PAYOUT_INSTANT";
            request.recipient = recipient;
            JsonNode body = mapper.readTree(mapper.writeValueAsString(request)).path("recipient");
            assertEquals(fixture[0], body.path("accountType").asText());
            assertEquals(fixture[1], body.path("accountNo").asText());
            assertFalse(body.has("bankCode"));
        }
    }

    /**
     * 验证未配置付款人和成功返回地址时保持旧代收请求结构。
     * 无参数、无返回值；仅内存序列化，无事务、缓存、异步或共享并发状态。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldOmitUnconfiguredPaymentFields() throws Exception {
        JsonNode body = new ObjectMapper().valueToTree(new PaymentOrderCreateRequest());
        assertFalse(body.has("payer"));
        assertFalse(body.has("successUrl"));
    }

    /**
     * 验证付款人联系方式不因 PIX 版本发布而丢失。
     * 无参数、无返回值；仅内存序列化，无事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化失败时直接终止测试，不重试或降级
     */
    @Test
    public void shouldRetainPayerContacts() throws Exception {
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        request.payer = new Payer();
        request.payer.email = "payer@example.test";
        request.payer.phone = "+8801700000000";
        JsonNode payer = new ObjectMapper().valueToTree(request).path("payer");
        assertEquals(request.payer.email, payer.path("email").asText());
        assertEquals(request.payer.phone, payer.path("phone").asText());
    }

    /**
     * 验证成功返回地址按原文序列化并参与五行签名，禁止裁剪或重新编码。
     * 无参数、无返回值；只使用虚构测试材料，无网络、事务、缓存、异步或并发副作用。
     *
     * @throws Exception 序列化或签名失败时立即阻断测试，不重试或降级
     */
    @Test
    public void shouldPreserveLiteralSuccessUrlInSignature() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        String originalBody = mapper.writeValueAsString(request);
        request.successUrl = " https://merchant.example/return?next=%2Forders&label=a+b ";
        String body = mapper.writeValueAsString(request);
        assertEquals(request.successUrl, mapper.readTree(body).path("successUrl").asText());
        assertEquals("POST\n/intl/v1/payment/order/create\n1234567890\nrelease-test\n" + body + "\n",
                XPayIntlSigner.buildSignPayload("POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", body));
        assertNotEquals(
                XPayIntlSigner.sign("sdk-release-fixture", "POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", originalBody),
                XPayIntlSigner.sign("sdk-release-fixture", "POST", "/intl/v1/payment/order/create",
                        "1234567890", "release-test", body));
    }
}
