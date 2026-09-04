# XPay 国际版 Java SDK

该 SDK 面向服务端 Java 8+ 应用，严格实现接口版本 `1.0` 的开发者平台 `/intl/v1` 最终合同。
当前 SDK 源码及发布制品版本为 `1.1.0`。它封装 5 类公开接口、
lowerCamelCase 请求模型、五行请求签名、普通 API 响应验签和 Webhook 验签，不兼容旧 PH
`method/version/data/sign` 协议。

## 从 GitHub Release 下载

正式版本统一发布在
[`KingPaySea/sdk-api-intl-java` Releases](https://github.com/KingPaySea/sdk-api-intl-java/releases)。
商户应下载带明确版本号的制品，不要直接使用 `main` 分支源码或覆盖本地同版本 JAR。

以 `1.1.0` 为例，macOS/Linux 可执行：

```bash
curl -fLO https://github.com/KingPaySea/sdk-api-intl-java/releases/download/v1.1.0/sdk-api-intl-java-1.1.0.jar
curl -fLO https://github.com/KingPaySea/sdk-api-intl-java/releases/download/v1.1.0/sdk-api-intl-java-1.1.0.jar.sha256
curl -fLO https://github.com/KingPaySea/sdk-api-intl-java/releases/download/v1.1.0/pom.xml
curl -fLO https://github.com/KingPaySea/sdk-api-intl-java/releases/download/v1.1.0/pom.xml.sha256

shasum -a 256 -c sdk-api-intl-java-1.1.0.jar.sha256
shasum -a 256 -c pom.xml.sha256

mvn -q org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file \
  -Dfile=sdk-api-intl-java-1.1.0.jar \
  -DpomFile=pom.xml
```

校验通过并安装到本机 Maven 仓库后，在业务项目中声明：

```xml
<dependency>
  <groupId>com.xpay</groupId>
  <artifactId>sdk-api-intl-java</artifactId>
  <version>1.1.0</version>
</dependency>
```

接口版本继续使用 `1.0`，Maven 制品按语义版本写为 `1.1.0`；SDK 版本表示在同一公开合同上的向后兼容能力演进。

`sdk-api-intl-java` 是普通薄 JAR，运行时依赖由随 Release 发布的 POM 声明，包括 OkHttp 和 Jackson。
因此推荐使用上面的 `install-file + pom.xml` 方式，或由商户公司制品库管理员把同一组已校验资产上传到内部
Maven 仓库；不要只复制 JAR 后遗漏传递依赖。CI 使用内部制品库时，仓库账号只能放在 Maven
`settings.xml` 或 CI Secret，不要写入业务 POM、代码仓库或日志。

每个 Release 还包含 Apache-2.0 `LICENSE`、sources、Javadoc、CycloneDX JSON/XML SBOM 及其
SHA-256 文件，便于许可证审查、代码审查、IDE 调试和供应链检查。主 JAR 内也包含
`META-INF/LICENSE`。

## 初始化

```java
import com.xpay.sdk.intl.XPayIntlClient;
import com.xpay.sdk.intl.XPayIntlConfig;

XPayIntlConfig config = new XPayIntlConfig(
    System.getenv("INTL_API_BASE_URL"),
    System.getenv("INTL_API_KEY"),
    System.getenv("INTL_API_SECRET"));
XPayIntlClient client = new XPayIntlClient(config);
```

`INTL_API_BASE_URL` 必须是平台下发的 HTTPS 接口域名，不能包含 `/intl/v1` 路径前缀。默认连接、
写入、读取超时分别为 10、10、30 秒。SDK 对默认和自定义 `OkHttpClient` 都强制关闭连接自动重试、HTTP 重定向和 HTTPS
重定向，防止 POST 重放或签名 Header 跨地址发送。可通过构造 `XPayIntlConfig` 传入其他客户端配置和
大于零的响应验签窗口；不要添加会在 SDK 外层自动重放 POST 的包装逻辑。

## 最终公开接口

| SDK 方法 | HTTP | 路径 |
| --- | --- | --- |
| `getBalances()` / `getBalances(country, currency)` | GET | `/intl/v1/balance/query` |
| `getBalancesByCountry(country)` / `getBalancesByCurrency(currency)` | GET | `/intl/v1/balance/query` |
| `createPaymentOrder(request)` | POST | `/intl/v1/payment/order/create` |
| `getPaymentOrderByMerchantOrderNo(merchantOrderNo)` | GET | `/intl/v1/payment/order/query` |
| `createPayout(request)` | POST | `/intl/v1/payout/order/create` |
| `getPayoutByMerchantOrderNo(merchantOrderNo)` | GET | `/intl/v1/payout/order/query` |

查单 Query 固定使用 `merchantOrderNo`。创建幂等由同一商户下的 `merchantOrderNo` 保证，不发送
`Idempotency-Key`；SDK 不提供资源 ID 查单、capabilities 或自定义 path 请求方法。

## 查询余额

```java
XPayIntlResponse response = client.getBalances(); // 查询当前商户全部 ACTIVE 国家币种账户
XPayIntlResponse phpResponse = client.getBalancesByCurrency("PHP");
XPayIntlResponse exactResponse = client.getBalances("PH", "PHP");
if (!response.isSuccessful()) {
    throw new IllegalStateException(response.bodyAsString());
}
System.out.println(response.bodyAsString());
```

余额响应中的 `availableAmount`、`pendingAmount`、`frozenAmount` 都是字符串主单位金额，只代表本次查询的
国家和币种快照。响应 Body 固定为 `{ "balances": [...] }`；筛选无匹配时返回空列表。

## 创建和查询收款订单

```java
import com.xpay.sdk.intl.model.Amount;
import com.xpay.sdk.intl.model.Payer;
import com.xpay.sdk.intl.model.PaymentOrderCreateRequest;

Amount amount = new Amount();
amount.value = "100.50"; // 字符串，不是 double 或 JSON number

Payer payer = new Payer();
payer.email = "payer@example.com"; // 孟加拉代收产品必填
payer.phone = "+639171234567"; // 选填

PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
request.merchantOrderNo = "DEMO_PAY_202606010001";
request.productCode = "PH_PHP_PAYMENT_QRPH_GCASH";
request.amount = amount;
request.orderDescription = "Virtual order";
request.payer = payer;

XPayIntlResponse created = client.createPaymentOrder(request);
XPayIntlResponse queried =
    client.getPaymentOrderByMerchantOrderNo(request.merchantOrderNo);
```

`productCode` 必须使用平台为商户开通的产品编码，调用方应把它当作不透明值，不要拼接、拆解或替换为
provider、渠道号、银行映射等内部参数。创建接口返回 HTTP `200` 时，Body 必定包含核心交易系统已创建
订单的真实 `platOrderNo`；该字段是不透明对账标识，不使用 `pay_` / `po_` 接口资源前缀。HTTP
`4xx` / `5xx` 只返回 `error` 对象，不包含 `platOrderNo`。HTTP `200` 仍不代表订单已达终态，订单
结果以 Body `status`、查单接口或 Webhook 为准。

## 创建和查询代付订单

```java
import com.xpay.sdk.intl.model.Amount;
import com.xpay.sdk.intl.model.PayoutCreateRequest;
import com.xpay.sdk.intl.model.Recipient;

Amount amount = new Amount();
amount.value = "500.00";

Recipient recipient = new Recipient();
recipient.bankCode = "DEMO_BANK";
recipient.accountNo = "0000000001"; // 必须是字符串，保留前导零
recipient.accountName = "Demo Recipient";

PayoutCreateRequest request = new PayoutCreateRequest();
request.merchantOrderNo = "DEMO_PAYOUT_202606010001";
request.productCode = "PH_PHP_PAYOUT_INSTANT";
request.amount = amount;
request.recipient = recipient;

XPayIntlResponse created = client.createPayout(request);
XPayIntlResponse queried =
    client.getPayoutByMerchantOrderNo(request.merchantOrderNo);
```

代付受理不代表资金已到达收款账户。已验签 HTTP `200` 中的 `platOrderNo` 是真实核心平台订单号；
`4xx` / `5xx` 错误体不包含该字段。平台明确分类的未知下单结果统一为已验签 HTTP `503` +
`ORDER_RESULT_UNKNOWN`，且不包含 `platOrderNo`。必须先按原 `merchantOrderNo` 查单；有界退避后仍查不到时，
安全重试只能复用同一单号和完全一致的原请求，不得换单号或并发重提。超时、断连或无法验证的响应也按该保守流程处理。

## 请求签名

SDK 自动发送以下 Header：

```http
Accept: application/json
Api-Key: ik_live_xxx
Timestamp: 1780272000
Nonce: nonce_xxx
Signature: v1=<64位小写十六进制>
```

POST 额外发送 `Content-Type: application/json`。请求签名原文固定五行，每行后均追加 `\n`，包括最后一行：

```text
HTTP请求方法\n
实际请求path和原始Query\n
请求时间戳\n
请求随机串\n
实际请求Body\n
```

SDK 不排序 Query、不重新格式化已序列化 Body，也不发送 `Authorization`、`X-Xpay-*`、版本 Header 或
`Idempotency-Key`。

## 普通 API 响应验签

可识别 API Key 的普通响应包含 `Timestamp`、`Nonce` 和 `Signature`。SDK 在返回
`XPayIntlResponse` 前，使用 API Secret 对以下三行原文验签：

```text
响应时间戳\n
响应随机串\n
原始响应Body\n
```

Header 缺失、时间戳超窗、签名错误或 Body 被改写时，SDK fail-close 抛出 `XPayIntlException`，调用方
不得使用该 Body 更新交易或资金状态。认证前或网关自产的无签名非 2xx 错误会作为
`signatureVerified=false` 的诊断响应返回；无签名 2xx 一律拒绝，该 Body 不能作为业务结果。

## Webhook 验签

根据 `Webhook-Secret-Id` 精确选择本地保存的 Webhook Secret，并在 JSON 解析前使用原始 Body 字节验签：

```java
import com.xpay.sdk.intl.XPayIntlWebhookVerifier;

byte[] rawBody = readRawRequestBody(request);
String webhookSecretId = request.getHeader("Webhook-Secret-Id");
String webhookSecret = loadWebhookSecretById(webhookSecretId);

boolean valid = XPayIntlWebhookVerifier.verify(
    request.getHeader("Timestamp"),
    request.getHeader("Nonce"),
    request.getHeader("Signature"),
    rawBody,
    webhookSecret,
    300L);
if (!valid) {
    throw new SecurityException("invalid webhook signature");
}
```

Webhook 验签原文也是 `timestamp + "\n" + nonce + "\n" + rawBody + "\n"`。验签通过后再按 Body
`eventId` 幂等落库或进入可靠队列，随后返回任意 HTTP `2xx`；验签失败、Secret ID 未知或事件未可靠接收时
返回非 `2xx`。

## 安全与排障

- API Secret、Webhook Secret 只能保存在服务端密钥管理系统，禁止写入前端、移动端、仓库或日志。
- 普通响应和 Webhook 都必须使用原始 Body 验签，验签通过后再解析 JSON。
- `XPayIntlResponse.isSuccessful()` 只在响应验签通过且 HTTP 为 `200` 时返回 `true`，仍不判断订单终态。
- 创建成功体的 `platOrderNo` 是核心交易系统真实平台订单号；不要按前缀猜测业务类型。
- HTTP `4xx` / `5xx` 仅把 `error.code` 和 `error.message` 作为错误诊断，不得从错误体提取或伪造平台订单号。
- 报障时提供环境、UTC 时间、商户号、`merchantOrderNo`、HTTP 状态、`Request-Id` 和 `error.code`；
  不要提供 Secret、完整签名、完整请求 Body、完整付款人联系方式或收款人完整账号。

## 示例与验证

完整示例见 [`examples/Quickstart.java`](examples/Quickstart.java)。它默认只查询余额，只有显式设置
`INTL_ENABLE_WRITES=true` 才创建订单。

仓库还提供与老网关本地联调用途一致、但不在源码保存凭据的五个独立手工联调类：

- [`src/test/java/com/xpay/sdk/intl/Keys.java`](src/test/java/com/xpay/sdk/intl/Keys.java) 从本地
  UTF-8 properties 文件加载商户资料；实际配置文件已被 Git 忽略。

| 可直接运行的手工联调类 | 最终接口 |
| --- | --- |
| `BalanceQueryLocalTestTool` | `GET /intl/v1/balance/query` |
| `PaymentCreateLocalTestTool` | `POST /intl/v1/payment/order/create` |
| `PaymentQueryLocalTestTool` | `GET /intl/v1/payment/order/query` |
| `PayoutCreateLocalTestTool` | `POST /intl/v1/payout/order/create` |
| `PayoutQueryLocalTestTool` | `GET /intl/v1/payout/order/query` |

先在 SDK 模块目录复制模板并填写平台实际提供的环境 URL、API Key、API Secret、产品及测试订单资料：

```bash
cp src/test/resources/intl-sdk-local.properties.example \
  src/test/resources/intl-sdk-local.properties
```

孟加拉代收产品必须填写 `paymentPayerEmail`；`paymentPayerPhone` 为选填。

实际凭据文件不得提交。创建操作还需在本地文件显式设置 `enableWrites=true`，并把
`writeConfirmedBaseUrl` 设置为与本次 `apiBaseUrl` 完全相同的完整地址；切换环境 URL 后旧确认会立即失效。
连接生产环境需另行设置 `allowProduction=true`。在 IDE 中直接运行上表任一类的 `main` 即可调用对应
接口，无需再选择操作名。未传程序参数时统一读取 `intl-sdk-local.properties`；如需使用其他位置的配置，
只传一个绝对路径，或设置 JVM 参数 `-Dintl.config=/absolute/path/to/intl-sdk-local.properties`。

也可以从 SDK 模块目录执行，下面示例运行支付查单；把类名替换为上表其他类即可运行对应接口：

```bash
mvn -q -DskipTests test-compile \
  org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.xpay.sdk.intl.manual.PaymentQueryLocalTestTool \
  -Dexec.args="/absolute/path/to/intl-sdk-local.properties"
```

创建工具不会生成或替换 `merchantOrderNo`，必须先把配置中的订单号持久化。已验签 HTTP `503` +
`ORDER_RESULT_UNKNOWN`、网络异常或验签失败时，改用相同配置文件中的原订单号运行对应 query 操作。
工具不自动重试；人工安全重试只能在有界查单后使用同一 `merchantOrderNo` 和原业务参数执行。工具只在终端输出 HTTP 状态、`Request-Id`、验签结果、订单号、订单状态及 `error.code` 等
信息，并打印全部响应 Header 和完整 UTF-8 Body。响应可能包含订单、付款人摘要或收款人信息，请勿直接分享终端输出。

从独立仓库根目录执行以下命令，可以使用 Java 8 编译 SDK、五个手工联调工具和 Quickstart，并生成与
GitHub Release 相同的 main、sources、Javadoc、CycloneDX SBOM 和 SHA-256 资产。该命令不调用真实 API；
当前发布门禁是编译与制品完整性验证，不应表述为真实网关或交易链路验收。

```bash
mvn -B -ntp clean verify
```

SDK 维护者的首次建库、分支保护、标签和后续同步步骤见 [`PUBLISHING.md`](PUBLISHING.md)。
