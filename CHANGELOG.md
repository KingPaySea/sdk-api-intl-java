# Changelog

本文件记录 `com.xpay:sdk-api-intl-java` 的公开行为变化。已发布制品不可覆盖；修复必须发布新的
Semantic Versioning 版本。

## 1.1.0 - 2026-09-04

- 代收创建请求新增类型化 `payer` 对象，支持付款人 `email` 和 `phone`；孟加拉代收产品必须提供邮箱。
- 查询响应和 Webhook 可返回付款人摘要，手机号只返回后四位；完整联系方式不得写入 `customData` 或报障信息。
- Quickstart 和本地代收创建工具支持通过环境变量或本地配置传入付款人联系方式。
- 保持公开接口版本 `1.0`、Java 8、请求签名、响应验签、幂等与未知结果处理规则不变，兼容既有 `1.0.0` 接入。

## 1.0.0 - 2026-08-13

- 直接替换旧预览协议，公开客户端仅保留开发者平台最终确定的五个业务接口。
- 请求改为 `Api-Key`、`Timestamp`、`Nonce`、`Signature` 和五行 HMAC-SHA256 原文；移除 Bearer、
  `X-Xpay-*`、日期版本、`Idempotency-Key`、capabilities、自定义请求和资源 ID 查单。
- 创建请求模型改为 lowerCamelCase、嵌套金额对象和类型化收款人字段。
- 新增普通 API 响应原始 Body 验签；签名缺失或无效时 fail-close，只有认证前或网关自产的无签名非 2xx
  响应可作为不可信诊断响应返回。
- Webhook 验签改为 `Timestamp`、`Nonce`、原始 Body 三行原文并保留最后换行。
- 默认和自定义 OkHttp 客户端均强制关闭连接重试及 HTTP/HTTPS 重定向，防止 POST 重放和签名 Header 跨地址发送。
- 与开发者平台共享请求、普通响应和 Webhook 签名向量，并新增最终 HTTPS 报文回归测试。
- 成功 HTTP `200` 订单响应和 Webhook 统一使用核心交易系统真实 `platOrderNo`，不再对外暴露
  `pay_` / `po_` 资源标识；HTTP `4xx` / `5xx` 错误体不包含平台订单号。
- 未知下单结果统一为已验签 HTTP `503` + `ORDER_RESULT_UNKNOWN`；只允许按原
  `merchantOrderNo` 查单，或在有界查询后使用同一单号和完全一致的请求串行安全重试。
- 发布 main、sources、Javadoc、CycloneDX JSON/XML SBOM、SHA-256 校验文件；release profile 增加 GPG 签名。
- 增加快速接入示例，以及不在源码保存密钥、默认禁止写操作的本地五接口联调工具。

## 1.0-Release - 历史试用版

- 早期商户联调制品，现已冻结，仅用于识别历史依赖。
- 不覆盖、不重新发布，也不建议新项目继续使用；新接入统一使用当前正式版 SDK `1.1.0`。
