#!/usr/bin/env python3
"""一次性迁移：把旧 canary release tag 批量改名（GitHub + Gitee 两边）。

规范（v2）：{version}-canary.YYYYMMDD-HHMMSS（UTC 全日期时间戳，唯一到秒）。
本次存量迁移目标按 2026-09-16 拍板的对应表（保留 v 前缀，GitHub 侧即
「release 的 tag_name + 显示名都改成新 tag」）：

  Gitee release id   旧 tag                          新 tag
  1146301            v2.3.0-canary.35006725721      v2.3.0-canary.20260915-181912
  1146308            v2.3.0                         v2.3.0（不动）
  1146320            v2.3.0-canary.35011662647      v2.3.0-canary.20260915-190654
  1146334            v2.3.0-canary.0915-201815      v2.3.0-canary.20260915-201740
  1146343            v2.3.1                         v2.3.1（不动）

新 tag 的时间戳 = 对应 GitHub Actions 运行的 created_at（UTC）：
  35006725721 -> 2026-09-15T18:19:12Z
  35011662647 -> 2026-09-15T19:06:54Z
  35018711451 -> 2026-09-15T20:17:40Z

用法：
  python3 rename_canary_releases.py                # 干跑（只打印将做什么）
  python3 rename_canary_releases.py --apply        # 真执行 GitHub + Gitee
  python3 rename_canary_releases.py --apply --github-only
  python3 rename_canary_releases.py --apply --gitee-only
  GITEE_TOKEN 取环境变量，或 ~/.gitee-token 文件（用完建议吊销）。

GitHub 侧：PAT 从 ~/.git-credentials 解析（x-access-token 或 URL 内嵌格式）。
Gitee 侧：先试 PUT 原地改 tag_name；不支持则 删旧 release -> 建新 release
（保留 body/prerelease，name=新 tag）-> 从 CDN 下载旧 apk 重新上传（curl -m 600）。
"""
from typing import Any, Optional
import json, os, re, shutil, subprocess, sys, urllib.request, urllib.error, urllib.parse

OWNER, REPO = "easyTIDollar", "jussichords"
GH = f"https://api.github.com/repos/{OWNER}/{REPO}"
GIT = f"https://gitee.com/api/v5/repos/{OWNER}/{REPO}"

# (gitee_release_id, old_tag, new_tag)；old==new 为校验性 no-op
MAPPING = [
    (1146301, "v2.3.0-canary.35006725721", "v2.3.0-canary.20260915-181912"),
    (1146308, "v2.3.0", "v2.3.0"),
    (1146320, "v2.3.0-canary.35011662647", "v2.3.0-canary.20260915-190654"),
    (1146334, "v2.3.0-canary.0915-201815", "v2.3.0-canary.20260915-201740"),
    (1146343, "v2.3.1", "v2.3.1"),
]

APPLY = "--apply" in sys.argv
ONLY_GH = "--github-only" in sys.argv
ONLY_GIT = "--gitee-only" in sys.argv
results = []


def log(msg): print(msg, flush=True)


def record(gid, tag, platform, ok, note=""):
    results.append((gid, tag, platform, ok, note))
    log(f"  [{'OK ' if ok else 'FAIL'}] {platform} {tag} {note}")


def http_json(method: str, url: str, token: Optional[str] = None, bearer: bool = False,
              body: Optional[Any] = None, timeout: int = 60, raw: bool = False) -> tuple[int, Any]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("User-Agent", "jussichords-migration")
    if token:
        if bearer:
            req.add_header("Authorization", f"Bearer {token}")
        if body is not None:
            req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode()
            return (r.status, txt if raw else json.loads(txt or "null"))
    except urllib.error.HTTPError as e:
        txt = e.read().decode(errors="replace")
        return (e.code, txt if raw else (json.loads(txt) if txt.strip().startswith("{") else txt))


def gh_token():
    p = os.path.expanduser("~/.git-credentials")
    if not os.path.exists(p):
        return None
    txt = open(p).read()
    m = re.findall(r"x-access-token:\s*([^\s@]+)", txt)
    if m:
        return m[0]
    m = re.findall(r"https://[^:/]+:([^@]+)@github\.com", txt)
    return m[0] if m else None


def gitee_token():
    t = os.environ.get("GITEE_TOKEN", "").strip()
    if t:
        return t
    p = os.path.expanduser("~/.gitee-token")
    if os.path.exists(p):
        return open(p).read().strip()
    return None


# ---------------- GitHub ----------------
def gh_releases(tok):
    st, d = http_json("GET", f"{GH}/releases?per_page=100", token=tok, bearer=True)
    return {r["tag_name"]: r for r in d} if st == 200 else {}


def migrate_github():
    log("== GitHub ==")
    tok = gh_token()
    if not tok:
        log("  无 GitHub PAT，跳过")
        return
    rels = gh_releases(tok)
    for gid, old, new in MAPPING:
        r = rels.get(old)
        if not r:
            # 可能已经改过了：直接校验 new 存在
            if new in rels:
                record(gid, old, "github", True, "(已是新名)")
            else:
                record(gid, old, "github", False, "旧 tag 不存在")
            continue
        if old == new:
            record(gid, old, "github", True, "no-op")
            continue
        if not APPLY:
            record(gid, old, "github", True, f"dry-run: PATCH {r['id']} tag_name->{new}")
            continue
        st, d = http_json("PATCH", f"{GH}/releases/{r['id']}", token=tok, bearer=True,
                          body={"tag_name": new, "name": new})
        if st != 200 or d.get("tag_name") != new:
            record(gid, old, "github", False, f"PATCH {st}: {json.dumps(d)[:160]}")
            continue
        # 校验：新 tag 指向同一 commit，旧 tag 已消失
        st2, t_new = http_json("GET", f"{GH}/tags/{urllib.parse.quote(new)}", token=tok, bearer=True)
        st3, _ = http_json("GET", f"{GH}/tags/{urllib.parse.quote(old)}", token=tok, bearer=True)
        ok = st2 == 200 and st3 == 404 and t_new.get("commit", {}).get("sha") == r.get("target_commitish", t_new.get("commit", {}).get("sha"))
        record(gid, old, "github", ok,
               f"-> {new}（旧 tag {'已删' if st3 == 404 else '仍在!'}）")


# ---------------- Gitee ----------------
def gitee_get(gid, tok):
    return http_json("GET", f"{GIT}/releases/{gid}?access_token={tok}", timeout=30)


def gitee_remake(gid, old, new, tok, apk_url):
    """先试 PUT 原地改 tag_name；不行则 删旧 -> 建新 -> 重传 apk。"""
    st, cur = gitee_get(gid, tok)
    if st != 200:
        record(gid, old, "gitee", False, f"GET {st}: {str(cur)[:120]}")
        return
    # 已就位
    if cur.get("tag_name") == new:
        record(gid, old, "gitee", True, "(已是新名)")
        return
    if not APPLY:
        record(gid, old, "gitee", True, "dry-run: PUT 或 删建重传")
        return
    body = cur.get("body") or ""
    # 尝试原地改
    st, d = http_json("PUT", f"{GIT}/releases/{gid}?access_token={tok}",
                       body={"tag_name": new}, timeout=30)
    st2, chk = gitee_get(gid, tok)
    in_place = st2 == 200 and chk.get("tag_name") == new
    if in_place:
        record(gid, old, "gitee", True, f"PUT 原地改名成功")
        return
    log(f"  PUT 不支持改 tag（{st}），走 删旧->建新->重传")
    # 1) 从 CDN 下载旧 apk
    tmp = f"/tmp/jussichords_{new}.apk"
    dl = subprocess.run(["curl", "-fsSL", "-m", "300", "-o", tmp, apk_url or
                         f"https://github.com/{OWNER}/{REPO}/releases/download/{old}/jussichords-canary.apk"])
    if dl.returncode != 0:
        record(gid, old, "gitee", False, "apk 下载失败")
        return
    # 2) 删旧
    st, _ = http_json("DELETE", f"{GIT}/releases/{gid}?access_token={tok}", timeout=60)
    if st not in (200, 204):
        record(gid, old, "gitee", False, f"DELETE {st}")
        return
    # 3) 建新（name 也统一为新 tag）
    st, d = http_json("POST", f"{GIT}/releases?access_token={tok}",
                      body={"tag_name": new, "target_commitish": "master",
                            "name": new, "body": body, "prerelease": True}, timeout=60)
    new_id = d.get("id") if st in (200, 201) else None
    if not new_id:
        record(gid, old, "gitee", False, f"POST {st}: {json.dumps(d, ensure_ascii=False)[:160]}")
        return
    # 4) 重传 apk（multipart 走 curl，CN 带宽给足超时）
    up = subprocess.run(["curl", "-fsS", "-m", "600", "-X", "POST",
                         f"{GIT}/releases/{new_id}/attach_files?access_token={tok}",
                         "-F", f"file=@{tmp}"], capture_output=True, text=True)
    if up.returncode != 0:
        record(gid, old, "gitee", False, f"upload {up.stderr[:120]}")
        return
    shutil.rmtree(os.path.dirname(tmp), ignore_errors=True)
    st, chk = gitee_get(new_id, tok)
    ok = st == 200 and chk.get("tag_name") == new and any(
        a.get("name", "").endswith(".apk") for a in chk.get("assets", []))
    record(gid, old, "gitee", ok, f"新建 id={new_id}")


def migrate_gitee():
    log("== Gitee ==")
    tok = gitee_token()
    if not tok:
        log("  无 GITEE_TOKEN（env 或 ~/.gitee-token），跳过 —— 找用户要 token")
        return
    # 先取一次全量列表拿各 apk 的下载链接
    for gid, old, new in MAPPING:
        if old == new:
            st, cur = gitee_get(gid, tok)
            record(gid, old, "gitee", st == 200, "no-op")
            continue
        st, cur = gitee_get(gid, tok)
        if st != 200:
            # 可能已改成 new：查一下
            st2, rels = http_json("GET", f"{GIT}/releases?per_page=100&access_token={tok}", timeout=30)
            if st2 == 200 and any(r.get("tag_name") == new for r in rels):
                record(gid, old, "gitee", True, "(已是新名)")
            else:
                record(gid, old, "gitee", False, f"GET {st}")
            continue
        apk = next((a.get("url") or a.get("browser_download_url") for a in cur.get("assets", [])
                    if a.get("name", "").endswith(".apk")), None)
        gitee_remake(gid, old, new, tok, apk)


def main():
    log(f"APPLY={APPLY} github={'skip' if ONLY_GIT else 'on'} gitee={'skip' if ONLY_GH else 'on'}")
    if not ONLY_GIT:
        migrate_github()
    if not ONLY_GH:
        migrate_gitee()
    fails = [r for r in results if not r[3]]
    log(f"完成：{len(results) - len(fails)}/{len(results)} 成功")
    if fails:
        log("失败项：")
        for r in fails:
            log(f"  {r[1]} [{r[2]}] {r[4]}")
        sys.exit(1)


if __name__ == "__main__":
    main()
