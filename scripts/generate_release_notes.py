#!/usr/bin/env python3
"""按本次构建实际包含的提交生成 release notes（更新说明）。

背景：更新说明之前是 build.yml 里写死的一段文字，每次构建都发布同一份旧文案，
用户看到的永远是过期内容。这里改成**从本次构建相对上一个版本的提交里自动生成**，
发版说明跟着代码走。

用法（CI 内）：
    python3 scripts/generate_release_notes.py --out "$RUNNER_TEMP/release-notes.md"
用法（本地）：
    python3 scripts/generate_release_notes.py --out /tmp/notes.md [--base <ref>] [--max 12]

基线（diff 起点）依次尝试，且**必须是 HEAD 的祖先**（否则 diff 会把整个历史算进来）：
  1. 上一个 canary tag（v*-canary.*，按 tag 内时间戳取最新）
  2. 上一个正式版 tag（v*，排除 canary）
  3. 都没有 → 最近 30 个提交

输出格式（与本仓库正式版 release note 风格一致）：
  - 按类别分组，组标题单独一行，不加粗
  - 每条一行 `- ` 短句
  - 组与组之间、条目之间不插空行（GitHub 会把空行渲染成 <p> 间距）
"""
import argparse
import os
import re
import subprocess
import sys

MAX_BULLETS = 15
MAX_PER_GROUP = 6
FALLBACK_COMMITS = 30

# conventional-commit 前缀 → 展示用分类
PREFIX_CATEGORY = {
    "feat": "功能",
    "fix": "修复",
    "perf": "性能",
    "refactor": "重构",
    "style": "界面",
    "docs": "文档",
    "test": "测试",
    "build": "构建",
    "ci": "构建",
    "chore": "杂项",
}
# 这些前缀对用户没有可感知的意义，直接跳过
SKIP_PREFIXES = {"docs", "test", "build", "ci", "chore"}
# 提到界面/横屏类改动的归到「界面」，比按 feat/fix 分更有信息量
UI_HINTS = (
    "横屏", "大屏", "折叠", "分屏", "界面", "布局", "样式", "主题", "动画", "动效",
    "landscape", "adaptive", "ui", "screen", "compose", "theme", "animation",
)
UI_CATEGORY = "界面"

COMMIT_RE = re.compile(
    r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?:\s*(?P<text>.+)$"
)


def git(*args: str) -> str:
    out = subprocess.run(
        ["git", *args], capture_output=True, text=True, timeout=120
    )
    return out.stdout.strip()


def is_ancestor(ref: str) -> bool:
    """ref 是否是 HEAD 的祖先 —— diff 起点的必要条件。"""
    if not git("rev-parse", "--verify", f"{ref}^{{commit}}"):
        return False
    return subprocess.run(
        ["git", "merge-base", "--is-ancestor", ref, "HEAD"],
        capture_output=True, text=True, timeout=120,
    ).returncode == 0


def tag_timestamp(tag: str) -> str:
    """v2.4.5-canary.20261024-065011 -> 20261024-065011"""
    return tag.rsplit("canary.", 1)[-1] if "canary." in tag else ""


def pick_base(prefer: str = "canary") -> str:
    """挑一个 diff 起点 ref（保证是 HEAD 的祖先）。"""
    canaries = [t for t in git("tag", "--list", "v*-canary.*").splitlines() if t]
    canaries.sort(key=tag_timestamp, reverse=True)
    stables = [
        t for t in git("tag", "--list", "v*").splitlines()
        if t and "-canary." not in t
    ]
    stables.sort(key=lambda t: t.rsplit("v", 1)[-1], reverse=True)
    # canary 发布以「上一个 canary」为基线；正式版以「上一个正式版」为基线。
    # 另一类 tag 通常不是当前分支的祖先，is_ancestor 会自动跳过。
    groups = (
        (canaries, stables) if prefer == "canary" else (stables, canaries)
    )
    for group in groups:
        for tag in group:
            if is_ancestor(tag):
                return tag
    return f"HEAD~{FALLBACK_COMMITS}"


def classify(subject: str) -> tuple[str | None, str]:
    """返回 (分类, 去掉前缀后的正文)。分类为 None 表示跳过。"""
    m = COMMIT_RE.match(subject)
    if not m:
        return None, subject
    ctype = m.group("type").lower()
    text = m.group("text").strip()
    scope = (m.group("scope") or "").strip()
    if ctype in SKIP_PREFIXES:
        return None, ""
    haystack = f"{scope} {text}".lower()
    if any(hint in haystack for hint in UI_HINTS):
        return UI_CATEGORY, text
    return PREFIX_CATEGORY.get(ctype, "其他"), text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="输出文件路径")
    parser.add_argument("--base", default="", help="手动指定 diff 起点 ref")
    parser.add_argument("--max", type=int, default=MAX_BULLETS)
    parser.add_argument(
        "--override",
        default="",
        help="人工撰写的说明文件；存在且非空时直接采用，不做自动生成",
    )
    parser.add_argument(
        "--prefer",
        default="canary",
        choices=["canary", "stable"],
        help="diff 基线优先用哪类 tag：canary=上一个 canary，stable=上一个正式版",
    )
    args = parser.parse_args()

    # 人工撰写的说明优先（正式版通常走这条路：聊天里定稿 → 写进仓库 → 发版）
    if args.override:
        try:
            with open(args.override, encoding="utf-8") as fh:
                manual = fh.read().strip()
        except OSError:
            manual = ""
        if manual:
            os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
            with open(args.out, "w", encoding="utf-8") as fh:
                fh.write(manual + "\n")
            print(f"[notes] 使用人工说明: {args.override} -> {args.out}")
            return 0

    base = args.base or pick_base(args.prefer)
    raw = git("log", f"{base}..HEAD", "--no-merges", "--pretty=format:%s")
    subjects = [s for s in raw.splitlines() if s.strip()]

    if not subjects:
        subjects = [
            s for s in git(
                "log", f"-{FALLBACK_COMMITS}", "--no-merges", "--pretty=format:%s"
            ).splitlines() if s.strip()
        ]

    # git log 是「新 → 老」，先按时间截取最近的一批提交，再分组展示，
    # 避免一次发版把几个月前的旧改动也列进来。
    recent: list[tuple[str, str]] = []
    for subject in subjects:
        category, text = classify(subject)
        if category is None or not text:
            continue
        recent.append((category, text))
        if len(recent) >= args.max:
            break

    grouped: dict[str, list[str]] = {}
    order: list[str] = []
    for category, text in recent:
        bucket = grouped.setdefault(category, [])
        if text not in bucket:
            bucket.append(text)
            if category not in order:
                order.append(category)

    # 组顺序：界面 / 功能 / 修复 / 性能 / 其他
    priority = ["界面", "功能", "修复", "性能", "重构", "其他", "文档", "杂项"]
    original_index = {category: i for i, category in enumerate(order)}
    order.sort(
        key=lambda c: (
            priority.index(c) if c in priority else len(priority),
            original_index[c],
        )
    )

    lines: list[str] = []
    for category in order:
        items = grouped[category]
        if not items:
            continue
        lines.append(category)
        lines.extend(f"- {item}" for item in items)

    body = "\n".join(lines) if lines else "- 内部构建"

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(body + "\n")

    print(f"[notes] base={base} commits={len(subjects)} -> {args.out}")
    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
