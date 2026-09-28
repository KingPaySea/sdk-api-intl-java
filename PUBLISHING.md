# GitHub 发布维护手册

本文面向 SDK 维护者。商户接入说明见 [README.md](README.md)。公开目标仓库固定为
`https://github.com/KingPaySea/sdk-api-intl-java`，首个公开版本从内部 `xpay-server` 当前 SDK
目录导出干净快照，禁止把整个服务端仓库或 SDK 的内部历史直接推到 GitHub。

## 首次发布前门禁

- 由代码所有者确认公开范围只包含 `sdk/sdk-api-intl-java` 当前快照。
- 确认根目录保留已经批准的 Apache License 2.0 `LICENSE`，变更许可证必须重新经过代码所有者审核。
- `CHANGELOG.md` 中目标版本从 `Unreleased` 改为实际发布日期，`pom.xml` 使用相同语义版本。
- 执行 `mvn -B -ntp clean verify`，确认 main、sources、Javadoc、SBOM 和 SHA-256 文件均生成。
- 确认 `git status --short --ignored` 中真实 `intl-sdk-local.properties` 仍为忽略状态。
- 扫描当前快照，确认不存在 API Key、API Secret、Webhook Secret、私钥、真实商户资料、内网地址或内部文档。
- GitHub 账号必须开启双因素认证，并确认 `gh auth status` 当前登录的是有权创建目标仓库的账号。

## 首次创建公开仓库

以下命令从已审核、已提交的内部版本生成全新 Git 历史，避免公开 SDK 曾经删除的内部代码或凭据。请从
`xpay-server/sdk/sdk-api-intl-java` 目录执行：

```bash
gh auth login -h github.com
gh auth status

SDK_EXPORT_DIR="$(mktemp -d)"
git -C ../.. archive HEAD:sdk/sdk-api-intl-java | tar -x -C "${SDK_EXPORT_DIR}"

git -C "${SDK_EXPORT_DIR}" init -b main
git -C "${SDK_EXPORT_DIR}" add -A
git -C "${SDK_EXPORT_DIR}" status --short
git -C "${SDK_EXPORT_DIR}" commit -m "Release XPay International Java SDK 1.0.0"

gh repo create KingPaySea/sdk-api-intl-java \
  --public \
  --description "XPay International OpenAPI Java SDK" \
  --source "${SDK_EXPORT_DIR}" \
  --remote origin \
  --push
```

创建后，在 GitHub 仓库设置中完成以下保护：

- 默认分支保持 `main`，启用分支保护并要求 `CI / Java 8 build` 通过。
- 禁止 force push 和删除受保护分支，至少要求一名代码所有者审核发布变更。
- Actions 的 `Workflow permissions` 保持只读默认值；`release.yml` 只通过文件内的
  `contents: write` 创建 Release。
- 如组织支持 GitHub Rulesets，要求标签 `v*` 仅由发布负责人创建。

## 发布版本

先确认公开仓库 `main` 上的 `pom.xml`、`CHANGELOG.md` 和源代码已经审核一致，再创建带注释标签：

```bash
git switch main
git pull --ff-only
mvn -B -ntp clean verify
git status --short

git tag -a v1.2.0 -m "XPay International Java SDK 1.2.0"
git push origin v1.2.0
```

标签推送后，`.github/workflows/release.yml` 会再次使用 Java 8 构建，只有标签与 POM 版本完全一致时才创建
GitHub Release。Release 会包含 Apache-2.0 `LICENSE`、main JAR、sources、Javadoc、POM、
CycloneDX JSON/XML SBOM 及所有 SHA-256 文件；同名 Release 已存在时流程失败，不会覆盖已发布制品。

## 后续从内部仓库同步

每次发布都使用临时目录克隆公开仓库，再用内部模块的当前快照替换工作树。先审核删除和新增文件，再提交；
不要对公开仓库使用强制推送，也不要发布内部服务端历史。

```bash
SDK_PUBLIC_DIR="$(mktemp -d)"
git clone https://github.com/KingPaySea/sdk-api-intl-java.git "${SDK_PUBLIC_DIR}"

git -C "${SDK_PUBLIC_DIR}" rm -r --ignore-unmatch .
git -C ../.. archive HEAD:sdk/sdk-api-intl-java | tar -x -C "${SDK_PUBLIC_DIR}"
git -C "${SDK_PUBLIC_DIR}" add -A
git -C "${SDK_PUBLIC_DIR}" diff --cached --check
git -C "${SDK_PUBLIC_DIR}" diff --cached --stat
git -C "${SDK_PUBLIC_DIR}" status --short
```

完成代码审核和独立构建后，在公开仓库创建普通提交并通过 PR 合入 `main`。合入后再按上节创建版本标签，
不得复用或移动已经发布的标签。

## 失败处理

- 标签与 POM 版本不一致：删除尚未公开使用的错误标签，修正版本后重新创建；已被商户使用的标签不得移动。
- GitHub Actions 构建失败：不手工上传本地产物，先修复可复现构建并重新推送新的补丁版本。
- 发现凭据：立即停止发布并轮换凭据；仅删除当前文件不够，还要确认公开 Git 历史、Release 资产和 Actions 日志。
- 已发布 JAR 有缺陷：保留原 Release，发布新的补丁版本并在 `CHANGELOG.md` 标出影响和升级建议。
