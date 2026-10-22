#!/usr/bin/env python3
"""清理 canary release 垃圾：只保留最新的 N 个 canary pre-release，
其余的 release 与对应 tag 一并删除（正式版 release 完全不碰）。

认证：GH_TOKEN（或 GITHUB_TOKEN）环境变量，需 contents: write 权限。
用法（CI 内）：KEEP_CANARY_RELEASES=2 python3 scripts/cleanup_canary_releases.py
用法（本地）：GH_TOKEN=xxx KEEP_CANARY_RELEASES=2 python3 scripts/cleanup_canary_releases.py

为什么用 git refs 枚举而不是 /releases 列表：
GitHub 的 /releases 列表接口对新创建的 release 存在可见性延迟（tag 已存在，
但列表里查不到），会导致刚发布的 canary 不被计入，保留名额被旧 release 占掉，
多留一个垃圾。git refs（tags）列表是即时的，且 canary 的唯一标识就在 tag 名里。
再用 /releases/tags/{tag} 逐个取 release 详情（id / prerelease / draft），
既准确又不会误伤正式版。

说明：用 curl 子进程而不是 urllib，因为本地可能走 socks5 代理，urllib 不支持。
"""
import json
import os
import subprocess
import sys

REPO = os.environ.get("GITHUB_REPOSITORY", "easyTIDollar/jussichords")
KEEP = int(os.environ.get("KEEP_CANARY_RELEASES", "2"))
TOKEN = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN") or ""
API = "https://api.github.com"


def api(method: str, path: str):
    """调用 GitHub API，返回 (http_status, parsed_body_or_None)。"""
    cmd = [
        "curl", "-sS", "-X", method,
        "-H", f"Authorization: Bearer {TOKEN}",
        "-H", "Accept: application/vnd.github+json",
        "-H", "X-GitHub-Api-Version: 2022-11-28",
        "-w", "\n%{http_code}",
        API + path,
    ]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=120).stdout
    except subprocess.TimeoutExpired:
        print(f"[cleanup] 请求超时: {method} {path}", file=sys.stderr)
        return 0, None
    if "\n" not in out:
        return 0, None
    body, _, code = out.rpartition("\n")
    try:
        status = int(code.strip())
    except ValueError:
        status = 0
    try:
        return status, json.loads(body) if body.strip() else None
    except json.JSONDecodeError:
        return status, None


def tag_timestamp(tag: str) -> str:
    """从 canary tag 里取时间戳：v2.4.5-canary.20261022-153330 -> 20261022-153330。"""
    return tag.rsplit("canary.", 1)[-1] if "canary." in tag else ""


def collect_canary_releases():
    """枚举所有 canary release：tag 列表 → 逐个查 release 详情。"""
    status, tags = api("GET", f"/repos/{REPO}/tags?per_page=100")
    if status != 200 or not isinstance(tags, list):
        print(f"[cleanup] 拉取 tag 列表失败 (HTTP {status})，跳过清理")
        return None

    found = []
    for t in tags:
        name = (t or {}).get("name") or ""
        if "-canary." not in name:
            continue
        st, rel = api("GET", f"/repos/{REPO}/releases/tags/{name}")
        if st != 200 or not isinstance(rel, dict):
            # tag 存在但没有对应 release（release 已被删），跳过
            continue
        # 双保险：只处理非 draft 的 pre-release
        if rel.get("draft") or not rel.get("prerelease"):
            continue
        found.append({"id": rel["id"], "tag": name, "created_at": rel.get("created_at") or ""})

    # 新的在前：以 tag 内时间戳为主键，缺失时退回 created_at
    found.sort(key=lambda r: (tag_timestamp(r["tag"]) or r["created_at"], r["tag"]), reverse=True)
    return found


def main() -> int:
    if not TOKEN:
        print("[cleanup] 缺少 GH_TOKEN / GITHUB_TOKEN，跳过清理")
        return 0

    print(f"[cleanup] repo={REPO} keep={KEEP}")

    canaries = collect_canary_releases()
    if canaries is None:
        return 0

    print(f"[cleanup] 当前 canary release 数：{len(canaries)}")
    if len(canaries) <= KEEP:
        print("[cleanup] 未超过保留上限，保持不动")
        return 0

    keeping = [r["tag"] for r in canaries[:KEEP]]
    removing = [r["tag"] for r in canaries[KEEP:]]
    print(f"[cleanup] 保留: {', '.join(keeping)}")
    print(f"[cleanup] 将删除: {', '.join(removing)}")

    for r in canaries[KEEP:]:
        rid = r["id"]
        tag = r["tag"]
        # 双保险：只处理非 draft 的 pre-release，且 tag 必须含 -canary.
        if "-canary." not in tag:
            print(f"[cleanup] 跳过非 canary 预发布: {tag}")
            continue
        print(f"[cleanup] 删除 canary release: {tag} (id={rid}, created={r['created_at']})")
        st, _ = api("DELETE", f"/repos/{REPO}/releases/{rid}")
        if st == 204:
            print(f"[cleanup]   已删除 release: {tag}")
        else:
            print(f"[cleanup]   ! 删除 release 失败 (HTTP {st}): {tag}")

        # tag 与 release 同名；tag 不存在时忽略
        st, ref = api("GET", f"/repos/{REPO}/git/ref/tags/{tag}")
        if st == 200 and ref:
            st, _ = api("DELETE", f"/repos/{REPO}/git/refs/tags/{tag}")
            if st == 204:
                print(f"[cleanup]   已删除 tag: {tag}")
            else:
                print(f"[cleanup]   ! 删除 tag 失败 (HTTP {st}): {tag}")

    print("[cleanup] 完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
