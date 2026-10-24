# 更新说明（release notes）

正式版发版时，把定稿的更新说明写进这个目录，文件名 = 发版 tag（不含 `v` 前缀无所谓，带不带都行，CI 按 `release-notes/<tag>.md` 找）：

```
release-notes/v2.4.6.md
```

CI（`.github/workflows/build.yml` 的 `Generate release notes` 步骤）会：

- **canary**：始终按「上一个 canary → 本次构建」的提交差异**自动生成**说明，所以每次 canary 发布的说明都对应这一版的真实改动，不会一直是旧文案。
- **正式版**：优先用本目录下与 tag 同名的文件；没有该文件时同样自动生成（以「上一个正式版 tag」为 diff 基线）。

## 写法要求

- 按类别分组，组标题单独一行、不加粗（如 `界面`、`功能`、`修复`）
- 每条一行 `- ` 开头，短句
- 组与组之间、条目之间**不要插空行**（GitHub 会把空行渲染成 `<p>` 间距）
- 不要写「新增」这类总标题

## 自动生成的分类规则

见 `scripts/generate_release_notes.py`：

- commit 前缀 `feat/fix/perf/refactor/style` → `功能/修复/性能/重构/界面`
- `docs/test/build/ci/chore` → 跳过（用户无感知）
- 提交内容命中「横屏/大屏/折叠/分屏/界面/布局/样式/主题/动画」等关键词 → 归到 `界面`
- 组顺序：界面 → 功能 → 修复 → 性能 → 其他
