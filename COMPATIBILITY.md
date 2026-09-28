# 兼容矩阵

| SDK 版本 | 生命周期 | Java | HTTP 合同 | 依赖形态 | 建议 |
| --- | --- | --- | --- | --- | --- |
| `1.2.0` | 已发布；当前正式版本（2026-09-29） | Java 8 及以上 | 接口版本 `1.0` 的开发者平台 `/intl/v1` 最终合同，包含付款人联系方式和可选 successUrl | 普通 jar，外部依赖 OkHttp/Jackson | 新接入使用 |
| `1.1.0` | 已发布；旧版（2026-09-04） | Java 8 及以上 | 接口版本 `1.0` 的开发者平台 `/intl/v1` 最终合同，包含代收付款人联系方式 | 普通 jar，外部依赖 OkHttp/Jackson | 既有接入可继续使用；需要 successUrl 时升级 |
| `1.0.0` | 已发布；旧版（2026-08-13） | Java 8 及以上 | 接口版本 `1.0` 的开发者平台 `/intl/v1` 最终合同，不含类型化 `payer` 模型 | 普通 jar，外部依赖 OkHttp/Jackson | 既有接入可继续使用；需要付款人字段时升级 |
| `1.0-Release` | 历史试用版，冻结 | Java 8 及以上 | 历史预览合同 | 普通 jar | 不用于新接入 |

## 兼容边界

- SDK 只支持统一国际版 `/intl/v1`，不兼容旧 PH `method/version/data/sign` 协议。
- 公开客户端只封装余额查询、收款创建/查单、代付创建/查单五个最终接口。
- 请求 Header 固定为 `Api-Key`、`Timestamp`、`Nonce`、`Signature`；不支持 Bearer、`X-Xpay-*`、
  API 日期版本或 `Idempotency-Key`。
- 创建请求使用 lowerCamelCase，幂等和查单统一依赖同一商户下的 `merchantOrderNo`。
- 可识别 API Key 的普通响应必须通过 API Secret 验签；认证前或网关自产的无签名非 2xx 错误只可作为
  `signatureVerified=false` 的诊断响应返回，无签名 2xx 一律拒绝。
- SDK 禁止自动重试业务创建请求。平台明确未知结果固定为已验签 HTTP `503` +
  `ORDER_RESULT_UNKNOWN`，且不含 `platOrderNo`；网络异常、读取超时或无法验证的响应按同样保守流程处理。
  必须先按原 `merchantOrderNo` 查单；有界退避后只能使用同一单号和完全一致的原请求串行安全重试。
- 默认连接、写入、读取超时分别为 10、10、30 秒；SDK 对默认和自定义客户端都强制关闭连接重试、
  HTTP 重定向和 HTTPS 重定向，自定义外层不得增加自动重放 POST 的逻辑。

## 升级约束

- 当前源码直接以开发者平台最终合同为准，不保留旧预览协议的兼容入口；`1.2.0` 的可选 successUrl 保持向后兼容，null 时不序列化，非空原文参与签名和幂等。
- 破坏性合同变化必须同步更新 OpenAPI、双语文档、Postman/Newman、共享签名向量、SDK 和测试。
- 已发布制品不可覆盖；发布修复时使用新的语义版本并在 `CHANGELOG.md` 标明合同影响范围。
