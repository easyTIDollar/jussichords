# jussichords canary 版本命名与发布标准（v2）

> 本标准从 canary 构建 `2026-09-16` 起生效。存量旧命名由 `scripts/rename_canary_releases.py` 一次性迁移。

## 命名规则（所有 canary 构建必须遵循）

| 项 | 格式 | 示例 |
|---|---|---|
| versionName（App 内、APK 内） | `{base}-canary.{YYYYMMDD-HHMMSS}` | `2.3.0-canary.20260916-030015` |
| tag（GitHub + Gitee 一致） | `v{base}-canary.{YYYYMMDD-HHMMSS}` | `v2.3.0-canary.20260916-030015` |
| release 显示名 | `jussichords-canary.{YYYYMMDD-HHMMSS}` | `jussichords-canary.20260916-030015` |
| APK 文件名 | `jussichords-canary.apk`（固定） | — |

- `{base}` 取自该构建分支 `app/build.gradle.kts` 的 `versionName`（如 canary-dev 上是 `2.2.7`，main 上是 `2.3.1`）。
- 时间戳为 **UTC** 全日期 `年月日-时分秒`（CI 里 `date -u +%Y%m%d-%H%M%S`），精确到秒、唯一、可读。
- 不再使用 GitHub `run_id`（11 位数字，不可读、无法人工排序）做尾号。
- 旧的 `-canary.<run_id>`、`-canary.<MMdd-HHMMSS>`（短日期）命名一律视为不合规。

## 构建与发布流程（CI 已实现，人工核对清单）

1. **触发**：`canary-dev` 分支手动 `workflow_dispatch`（build_type=canary）；main 分支手动（build_type=canary）。
2. **构建**：`assembleCanary`，CI 注入 `-PCANARY_VERSION_SUFFIX=-canary.{ts}`；canary 测试密钥由 keytool 现场生成（`canary-keystore.jks`）。
3. **GitHub 发布**：pre-release，tag/名称按上表。
4. **Gitee 同步**（同一 run 自动执行）：
   - 删 Gitee 同名旧 release（`per_page=100` 列表里匹配 `tag_name`）；
   - 建 pre-release（`target_commitish=master`，缺失 tag 自动补）；
   - 上传 APK 到 `attach_files`（`-m 300` 给足超时）。
   - 完成后两边应同时存在该 tag。
5. **人工核对**（每次构建后）：
   - GitHub release 与 Gitee release 的 `tag_name` 一致、APK 都在；
   - Gitee 上旧 tag 的 release 数量只增不删（稳定版）；canary 旧 tag 累积属正常（每个构建一个 tag）。

## Canary 垃圾自动清理（防堆积）

无电脑调试时每次 canary 构建都会新增一个 release + tag，长期会堆很多垃圾。CI 已内建清理：

- **触发**：canary 构建发布成功后自动执行 `scripts/cleanup_canary_releases.py`（`Cleanup old canary releases` 步骤）。
- **保留策略**：只保留**最新 2 个** canary pre-release（`prerelease=true` 且 tag 含 `-canary.`），其余的 **release 与同名 tag 一并删除**。
- **枚举方式**：用 **git refs（tags）列表**枚举 canary tag，再逐个查 `/releases/tags/{tag}` 拿 release 详情。
  原因：`/releases` 列表接口对**刚创建**的 release 存在可见性延迟（tag 已存在但列表查不到），
  直接用列表会把刚发布的 canary 漏掉，导致保留名额被旧 release 占掉、多留一个垃圾。
- **正式版 release 完全不碰**（脚本只筛 `-canary.` 的 pre-release，并有二次校验跳过非 canary 预发布）。
- **权限**：用 CI 内置 `GITHUB_TOKEN`（workflow 已有 `permissions: contents: write`），无需额外 secret。
- **不破坏 App 内更新**：App 的 canary 通道读最新 pre-release，保留的 2 个即最新，`checkUpdate()` 判定与命名规则自洽。
- **手动补清**（可选）：`GH_TOKEN=xxx KEEP_CANARY_RELEASES=2 python3 scripts/cleanup_canary_releases.py`。
- **调整保留数量**：改 workflow 里 `KEEP_CANARY_RELEASES` 环境变量值即可（默认 `'2'`）。

## 能否把 canary 变成「私人」

GitHub 的 release **没有单独的「设为私有」开关**，只有三条路：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A（当前采用）** | 保持公开 + CI 自动清理旧 canary | 无代价，App 内更新/下载完全不受影响 |
| B | canary 改发 Actions artifact（固定名 + 7 天过期） | 下载要进 Actions run 页面；App 内 canary 更新通道失效 |
| C | canary 移到单独的 private 仓库（CI 用 PAT 跨仓推） | 公开仓库零痕迹；手机下载需登录；App 内 canary 通道失效 |
| D | 整个 repo 转 private | 正式版公开分发和 App 内更新全崩，**不可行** |

结论：**变私人后当前构建流程照样能用**（变的是发布落点，不是构建流程——`assembleCanary`、时间戳后缀、独立 keystore、`.canary` applicationId 全不变）。


## 检查更新逻辑（App 侧，与命名自洽）

- `AppUpdateManager.checkUpdate()` 渠道感知：canary 构建只跟踪 **pre-release**，正式版只跟踪 latest。
- 数据源：GitHub API 列表为主，失败回退 Gitee（`per_page=100`，按 `created_at` 倒序）。
- 判断「有更新」= 最新 release 的 tag（去 `v`）≠ 当前 `VERSION_NAME`。**因此每个新 canary 构建必然对所有旧 canary 提示更新**——这是设计目标，不是 bug。
- Gitee asset 无 size 字段：size=0 时补一次 HEAD 探测拿 content-length（进度条正常）。
- Gitee 网页 release 页探测须带 `Accept: application/json`（`octet-stream` 会 404）——CI 回退源代码已固定为 json。

## 旧命名 → 新命名迁移（一次性）

存量 canary release 已按「创建时间戳=对应 GitHub Actions run 的 `created_at`(UTC)」映射，
脚本 `scripts/rename_canary_releases.py` 双端（GitHub + Gitee）改名，映射表在脚本头部 `MAPPING`。

## 变更本标准时

1. 同时改 `canary-dev` 与 `main` 的 `.github/workflows/build.yml`（SUFFIX 生成处 + Gitee 步骤保持 parity）；
2. 改 `app/build.gradle.kts` 的 `versionNameSuffix` 注释；
3. 更新本文档；
4. 若只改未来规则不动存量，无需跑迁移脚本。
