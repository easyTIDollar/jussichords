#!/usr/bin/env python3
"""清理 canary release 垃圾：只保留最新的 N 个 canary pre-release，
其余的 release 与对应 tag 一并删除（正式版 release 完全不碰）。

认证：GH_TOKEN（或 GITHUB_TOKEN）环境变量，需 contents: write 权限。
用法（CI 内）：KEEP_CANARY_RELEASES=2 python3 scripts/cleanup_canary_releases.py
用法（本地）：GH_TOKEN=xxx KEEP_CANARY_RELEASES=2 python3 scripts/cleanup_canary_releases.py

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


def main() -> int:
    if not TOKEN:
        print("[cleanup] 缺少 GH_TOKEN / GITHUB_TOKEN，跳过清理")
        return 0

    print(f"[cleanup] repo={REPO} keep={KEEP}")

    status, releases = api("GET", f"/repos/{REPO}/releases?per_page=100")
    if status != 200 or not isinstance(releases, list):
        print(f"[cleanup] 拉取 release 列表失败 (HTTP {status})，跳过清理")
        return 0

    # canary 预发布版：prerelease 且非 draft 且 tag 含 -canary.
    canaries = [
        r for r in releases
        if r.get("prerelease")
        and not r.get("draft")
        and "-canary." in (r.get("tag_name") or "")
    ]

    def sort_key(r):
        # tag 里的时间戳（YYYYMMDD-HHMMSS）最可靠；缺失时退回 created_at。
        # 同一秒创建的多个 canary 用它区分，created_at 相同时顺序才稳定。
        tag = r.get("tag_name") or ""
        ts = tag.rsplit("canary.", 1)[-1] if "canary." in tag else ""
        return (ts or (r.get("created_at") or ""), tag)

    # 新的在前
    canaries.sort(key=sort_key, reverse=True)

    print(f"[cleanup] 当前 canary release 数：{len(canaries)}")
    if len(canaries) <= KEEP:
        print("[cleanup] 未超过保留上限，保持不动")
        return 0

    keeping = [r["tag_name"] for r in canaries[:KEEP]]
    removing = [r["tag_name"] for r in canaries[KEEP:]]
    print(f"[cleanup] 保留: {', '.join(keeping)}")
    print(f"[cleanup] 将删除: {', '.join(removing)}")

    for r in canaries[KEEP:]:
        rid = r["id"]
        tag = r["tag_name"]
        # 双保险：只处理非 draft 的 pre-release，且 tag 必须含 -canary.
        if r.get("draft") or not r.get("prerelease") or "-canary." not in tag:
            print(f"[cleanup] 跳过非 canary 预发布: {tag}")
            continue
        print(f"[cleanup] 删除 canary release: {tag} (id={rid}, created={r.get('created_at')})")
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
