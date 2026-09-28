package com.xpay.sdk.intl;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 国际版 SDK 本地联调配置加载器。
 * <p>
 * 本类只供受控开发机上的手工联调使用，从 Git 忽略的 UTF-8 properties 文件读取测试或生产商户资料，
 * 不在 Java 源码中保存 API Key、API Secret 或固定环境地址。生产环境访问使用独立开关，创建类操作还要求
 * 写确认地址与实际 Base URL 完全一致后才放行，避免配置切换时误连生产或误发资金类请求；配置不进入缓存、
 * 数据库、事务或异步任务。
 * </p>
 */
public final class Keys {

    private static final String DEFAULT_CONFIG_PATH = "src/test/resources/intl-sdk-local.properties";

    private static final String REPOSITORY_CONFIG_PATH =
            "sdk/sdk-api-intl-java/src/test/resources/intl-sdk-local.properties";

    private final Properties properties;

    private final Path sourcePath;

    private Keys(Properties properties, Path sourcePath) {
        this.properties = properties;
        this.sourcePath = sourcePath;
    }

    /**
     * 加载本地商户联调配置。
     * <p>
     * 路径优先使用方法参数，其次读取 JVM 参数 {@code -Dintl.config=/absolute/path}，最后使用模块内默认的
     * Git 忽略文件。文件缺失、不可读或属性为空时立即抛出异常，不回退到内置凭据，也不访问网络、数据库、
     * 缓存或异步线程。
     * </p>
     *
     * @param configFile UTF-8 properties 文件路径；可为空以使用 JVM 参数或默认路径
     * @return 只在当前进程内持有配置的加载器
     * @throws IllegalStateException 配置文件不存在、不可读或解析失败时抛出
     */
    public static Keys load(String configFile) {
        String selected = trimToNull(configFile);
        if (selected == null) {
            selected = trimToNull(System.getProperty("intl.config"));
        }
        Path path = selected == null ? findDefaultConfigPath() : Paths.get(selected).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("local SDK config file does not exist: " + path
                    + "; copy intl-sdk-local.properties.example first");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return new Keys(properties, path);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load local SDK config: " + path, ex);
        }
    }

    /**
     * 使用本地文件中的 Base URL、API Key 和 API Secret 创建国际版客户端。
     * <p>
     * 构造过程只做配置校验，不发送请求、不自动重试、不启动事务、缓存或异步任务。调用方必须先执行本类的
     * 环境与写操作门禁；必要字段缺失时 fail-close，异常信息只包含属性名，不包含凭据值。
     * </p>
     *
     * @return 可复用且线程安全边界与 {@link XPayIntlClient} 一致的客户端
     * @throws IllegalStateException 必要属性缺失时抛出
     * @throws IllegalArgumentException URL 或 SDK 配置不符合安全约束时抛出
     */
    public XPayIntlClient createClient() {
        assertOperationAllowed("client initialization", false);
        XPayIntlConfig config = new XPayIntlConfig(
                required("apiBaseUrl"),
                required("apiKey"),
                required("apiSecret"));
        return new XPayIntlClient(config);
    }

    /**
     * 校验当前环境和操作是否被本地安全开关明确允许。
     * <p>
     * 生产访问、创建操作和目标地址确认均采用 fail-close；校验只读取当前进程配置，不发送网络请求，
     * 不参与事务、缓存、异步任务或自动重试。
     * </p>
     *
     * @param operation 当前操作名称，仅用于错误提示
     * @param writeOperation 是否为会创建真实订单的写操作
     * @throws IllegalStateException 环境非法或生产、写操作门禁未明确放行时抛出
     */
    public void assertOperationAllowed(String operation, boolean writeOperation) {
        String environment = required("environment").toLowerCase(java.util.Locale.ROOT);
        if (!"test".equals(environment) && !"production".equals(environment)) {
            throw new IllegalStateException("environment must be test or production");
        }
        if ("production".equals(environment) && !flag("allowProduction")) {
            throw new IllegalStateException("production access is disabled; set allowProduction=true explicitly");
        }
        if (writeOperation && !flag("enableWrites")) {
            throw new IllegalStateException(operation
                    + " is a create operation; set enableWrites=true explicitly after checking the target environment");
        }
        if (writeOperation && !required("apiBaseUrl").equals(required("writeConfirmedBaseUrl"))) {
            throw new IllegalStateException("writeConfirmedBaseUrl must exactly match apiBaseUrl for every create run");
        }
    }

    /**
     * 读取必填的本地联调属性。
     *
     * @param name 属性名
     * @return 去除首尾空白后的属性值
     * @throws IllegalStateException 属性缺失或为空时抛出；异常不包含属性值
     * @apiNote 仅同步读取内存配置，不访问事务、缓存、网络或异步线程。
     */
    public String required(String name) {
        String value = trimToNull(properties.getProperty(name));
        if (value == null) {
            throw new IllegalStateException("missing required local SDK property: " + name);
        }
        return value;
    }

    /**
     * 读取可选的本地联调属性。
     *
     * @param name 属性名
     * @param defaultValue 属性缺失或为空时返回的默认值
     * @return 去除首尾空白后的属性值，或调用方提供的默认值
     * @apiNote 仅同步读取内存配置，无异常降级之外的副作用，不访问事务、缓存、网络或异步线程。
     */
    public String optional(String name, String defaultValue) {
        String value = trimToNull(properties.getProperty(name));
        return value == null ? defaultValue : value;
    }

    /**
     * 读取参与签名和幂等的可选原文属性，避免自动 trim 改变成功返回地址。
     * <p>仅同步读取内存中的 Properties 解析值，不做 URL 归一化；非法值由服务端 fail-close 拒绝。
     * 无网络、数据库事务、缓存、异步或并发写操作。</p>
     *
     * @param name 属性名
     * @return 原文属性值；缺失或零长度表示未配置，返回 null；空白字符不会被静默删除
     */
    public String optionalLiteral(String name) {
        String value = properties.getProperty(name);
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 生成不包含 API Key、API Secret 或签名的联调目标摘要。
     *
     * @return 环境、Base URL、核对用商户号及配置文件路径组成的安全摘要
     * @throws IllegalStateException 环境或 Base URL 等必填属性缺失时抛出
     * @apiNote 仅同步拼接内存配置，不访问事务、缓存、网络或异步线程。
     */
    public String targetSummary() {
        return "environment=" + required("environment")
                + ", apiBaseUrl=" + required("apiBaseUrl")
                + ", merchantNo=" + optional("merchantNo", "(bound to Api-Key)")
                + ", config=" + sourcePath;
    }

    private boolean flag(String name) {
        String value = optional(name, "false");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException(name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static Path findDefaultConfigPath() {
        Path modulePath = Paths.get(DEFAULT_CONFIG_PATH).toAbsolutePath().normalize();
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        Path repositoryPath = Paths.get(REPOSITORY_CONFIG_PATH).toAbsolutePath().normalize();
        return Files.isRegularFile(repositoryPath) ? repositoryPath : modulePath;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }
}
