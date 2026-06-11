#!/usr/bin/env python3
"""kuship vs rainbond 接口响应对比工具。

用法:
    python3 tools/api-diff/diff.py --user <name> --password <pwd>

可选:
    --rainbond URL          rainbond-console 基址，默认 http://localhost:7070
    --kuship URL            kuship-console 基址，默认 http://localhost:8080
    --config PATH           endpoints.json 路径，默认 tools/api-diff/endpoints.json
    --out PATH              输出 Markdown 报告路径，默认 tools/api-diff/reports/report-<timestamp>.md
    --login-target rainbond|kuship|both
                            登录哪边拿 token；默认 rainbond（两边共用 JWT_SECRET_KEY 时只需登 1 边）
    --only id1,id2          仅跑指定 endpoint id，逗号分隔
    --timeout N             单次 HTTP 超时秒数，默认 10
"""

from __future__ import annotations

import argparse
import datetime
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


# ---------- HTTP 小封装 ----------

def http_post_form(url: str, data: dict, timeout: int) -> tuple[int, dict]:
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body, method="POST",
                                  headers={"Content-Type": "application/x-www-form-urlencoded"})
    return _do(req, timeout)


def http_get(url: str, token: str | None, timeout: int,
             extra_headers: dict[str, str] | None = None) -> tuple[int, Any]:
    req = urllib.request.Request(url, method="GET")
    if token:
        req.add_header("Authorization", f"GRJWT {token}")
    if extra_headers:
        for k, v in extra_headers.items():
            req.add_header(k, v)
    return _do(req, timeout)


def _do(req: urllib.request.Request, timeout: int) -> tuple[int, Any]:
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, _decode(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, _decode(e.read())
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        return -1, {"_error": str(e)}


def _decode(raw: bytes) -> Any:
    if not raw:
        return None
    txt = raw.decode("utf-8", errors="replace")
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        return {"_raw": txt[:500]}


# ---------- 登录 ----------

def login(base: str, user: str, password: str, timeout: int) -> str | None:
    url = base.rstrip("/") + "/console/users/login"
    status, body = http_post_form(url, {"nick_name": user, "password": password}, timeout)
    if status != 200 or not isinstance(body, dict):
        print(f"[login {base}] FAILED status={status} body={_short(body)}", file=sys.stderr)
        return None
    # 两端响应都是 general_message：data.bean.token
    token = (body.get("data") or {}).get("bean", {}).get("token")
    if not token:
        # rainbond JWTTokenView 老格式：data.token / token
        token = (body.get("data") or {}).get("token") or body.get("token")
    if not token:
        print(f"[login {base}] no token in response: {_short(body)}", file=sys.stderr)
        return None
    return token


# ---------- diff 核心 ----------

@dataclass
class EndpointResult:
    id: str
    method: str
    path: str
    rb_status: int
    kp_status: int
    rb_body: Any
    kp_body: Any
    diffs: list[str] = field(default_factory=list)
    suppressed: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return self.rb_status == self.kp_status == 200 and not self.diffs


def _suppress_expected(diffs: list[str], expected: list[str]) -> tuple[list[str], list[str]]:
    """从 diffs 中移除"已知合规差异"（字符串相等 OR 子串匹配）。返回 (剩余, 已屏蔽)。"""
    kept: list[str] = []
    sup: list[str] = []
    for d in diffs:
        if any(e == d or e in d for e in expected):
            sup.append(d)
        else:
            kept.append(d)
    return kept, sup


def diff_endpoint(rb_body: Any, kp_body: Any) -> list[str]:
    """对比两端响应，返回人类可读 diff 行表。空表 = 完全一致。"""
    out: list[str] = []
    if not isinstance(rb_body, dict) or not isinstance(kp_body, dict):
        out.append(f"top-level shape mismatch: rb={type(rb_body).__name__} kp={type(kp_body).__name__}")
        return out

    # code 字段一致性
    if rb_body.get("code") != kp_body.get("code"):
        out.append(f"code: rb={rb_body.get('code')!r} vs kp={kp_body.get('code')!r}")

    # data 节点
    rb_data = rb_body.get("data") or {}
    kp_data = kp_body.get("data") or {}
    if not isinstance(rb_data, dict) or not isinstance(kp_data, dict):
        out.append(f"data shape mismatch: rb={type(rb_data).__name__} kp={type(kp_data).__name__}")
        return out

    # data 顶层 key 集
    rb_keys = set(rb_data.keys())
    kp_keys = set(kp_data.keys())
    only_rb = rb_keys - kp_keys
    only_kp = kp_keys - rb_keys
    if only_rb:
        out.append(f"data.* only-in-rainbond: {sorted(only_rb)}")
    if only_kp:
        out.append(f"data.* only-in-kuship: {sorted(only_kp)}")

    # data.bean diff
    rb_bean = rb_data.get("bean")
    kp_bean = kp_data.get("bean")
    if isinstance(rb_bean, dict) and isinstance(kp_bean, dict):
        out.extend(_diff_dict("data.bean", rb_bean, kp_bean))
    elif rb_bean != kp_bean:
        out.append(f"data.bean: rb={_short(rb_bean)} vs kp={_short(kp_bean)}")

    # data.list diff
    rb_list = rb_data.get("list")
    kp_list = kp_data.get("list")
    if isinstance(rb_list, list) and isinstance(kp_list, list):
        if len(rb_list) != len(kp_list):
            out.append(f"data.list length: rb={len(rb_list)} kp={len(kp_list)}")
        if rb_list and kp_list:
            r0 = rb_list[0]
            k0 = kp_list[0]
            if isinstance(r0, dict) and isinstance(k0, dict):
                out.extend(_diff_dict("data.list[0]", r0, k0))
            elif r0 != k0:
                out.append(f"data.list[0]: rb={_short(r0)} vs kp={_short(k0)}")
    elif rb_list != kp_list:
        out.append(f"data.list shape: rb={type(rb_list).__name__} kp={type(kp_list).__name__}")

    # extras (除 bean / list 外 data 下的 key)
    for k in (rb_keys | kp_keys) - {"bean", "list"}:
        rv = rb_data.get(k)
        kv = kp_data.get(k)
        if rv != kv:
            out.append(f"data.{k}: rb={_short(rv)} vs kp={_short(kv)}")

    return out


def _diff_dict(prefix: str, rb: dict, kp: dict) -> list[str]:
    out: list[str] = []
    rk = set(rb.keys())
    kk = set(kp.keys())
    only_rb = rk - kk
    only_kp = kk - rk
    if only_rb:
        out.append(f"{prefix} only-in-rainbond keys: {sorted(only_rb)}")
    if only_kp:
        out.append(f"{prefix} only-in-kuship keys: {sorted(only_kp)}")
    for k in rk & kk:
        rv = rb[k]
        kv = kp[k]
        rt = type(rv).__name__
        kt = type(kv).__name__
        # None vs None：相同；None vs 其他：仅记类型，不打值
        if rv is None and kv is None:
            continue
        if rt != kt and not (rv is None or kv is None):
            out.append(f"{prefix}.{k} type: rb={rt} kp={kt} (rb={_short(rv)} kp={_short(kv)})")
        elif isinstance(rv, dict) and isinstance(kv, dict):
            out.extend(_diff_dict(f"{prefix}.{k}", rv, kv))
        elif isinstance(rv, list) and isinstance(kv, list):
            if len(rv) != len(kv):
                out.append(f"{prefix}.{k} list length: rb={len(rv)} kp={len(kv)}")
        elif rv != kv:
            out.append(f"{prefix}.{k}: rb={_short(rv)} vs kp={_short(kv)}")
    return out


def _short(v: Any, limit: int = 80) -> str:
    s = json.dumps(v, ensure_ascii=False, default=str) if not isinstance(v, str) else v
    return s if len(s) <= limit else s[: limit - 3] + "..."


# ---------- 主流程 ----------

def render_placeholders(s: str, ctx: dict[str, str]) -> str:
    out = s
    for k, v in ctx.items():
        out = out.replace("{" + k + "}", str(v))
    return out


def run(args: argparse.Namespace) -> int:
    cfg_path = Path(args.config)
    if not cfg_path.exists():
        print(f"config not found: {cfg_path}", file=sys.stderr)
        return 2
    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    ctx = {k: str(v) for k, v in (cfg.get("context") or {}).items()}
    endpoints = cfg.get("endpoints") or []
    if args.only:
        wanted = set(args.only.split(","))
        endpoints = [e for e in endpoints if e["id"] in wanted]
        if not endpoints:
            print(f"--only {args.only} matched 0 endpoints", file=sys.stderr)
            return 2

    # 登录
    rb_token = kp_token = None
    target = args.login_target
    if target in ("rainbond", "both"):
        rb_token = login(args.rainbond, args.user, args.password, args.timeout)
        if not rb_token:
            return 3
    if target in ("kuship", "both"):
        kp_token = login(args.kuship, args.user, args.password, args.timeout)
        if not kp_token:
            return 3
    if target == "rainbond" and not kp_token:
        kp_token = rb_token  # 共享 JWT_SECRET_KEY 时复用
    if target == "kuship" and not rb_token:
        rb_token = kp_token

    print(f"login ok target={target} rb_token={_mask(rb_token)} kp_token={_mask(kp_token)}", file=sys.stderr)

    # 通用 header：rainbond RegionTenantHeaderView 期待 X-Region-Name，
    # 否则全局 400 "请求参数不全"。kuship-ui 同样发送此 header（utils/request.js）。
    common_headers: dict[str, str] = {}
    if ctx.get("region_name"):
        common_headers["X-Region-Name"] = ctx["region_name"]
        common_headers["Cookie"] = f"region_name={ctx['region_name']}"
    if ctx.get("team_name"):
        common_headers["X-Team-Name"] = ctx["team_name"]

    # 跑端点
    results: list[EndpointResult] = []
    for ep in endpoints:
        path = render_placeholders(ep["path"], ctx)
        method = ep.get("method", "GET").upper()
        if method != "GET":
            print(f"[{ep['id']}] skip method={method} (not yet supported)", file=sys.stderr)
            continue
        rb_status, rb_body = http_get(args.rainbond.rstrip("/") + path, rb_token, args.timeout, common_headers)
        kp_status, kp_body = http_get(args.kuship.rstrip("/") + path, kp_token, args.timeout, common_headers)
        r = EndpointResult(
            id=ep["id"], method=method, path=path,
            rb_status=rb_status, kp_status=kp_status,
            rb_body=rb_body, kp_body=kp_body,
        )
        if rb_status != kp_status:
            r.diffs.append(f"HTTP status: rb={rb_status} kp={kp_status}")
        if rb_status == kp_status == 200:
            r.diffs.extend(diff_endpoint(rb_body, kp_body))
        # 过滤端点声明的"已知合规差异"（例如 kuship list=List 契约 vs rainbond dict-as-list）
        expected = ep.get("expected_diffs") or []
        if expected:
            r.diffs, suppressed = _suppress_expected(r.diffs, expected)
            r.suppressed = suppressed
        results.append(r)
        tag = "OK  " if r.ok else "DIFF"
        sup = f" suppressed={len(r.suppressed)}" if r.suppressed else ""
        print(f"[{tag}] {ep['id']:24s} rb={rb_status} kp={kp_status} diffs={len(r.diffs)}{sup}", file=sys.stderr)

    # 写报告
    out_path = Path(args.out) if args.out else _default_report_path(cfg_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(_render_report(args, results), encoding="utf-8")
    print(f"\nreport written: {out_path}", file=sys.stderr)

    fails = sum(1 for r in results if not r.ok)
    print(f"summary: {len(results) - fails}/{len(results)} match, {fails} diff", file=sys.stderr)
    return 0 if fails == 0 else 1


def _default_report_path(cfg_path: Path) -> Path:
    ts = datetime.datetime.now().strftime("%Y-%m-%d-%H%M")
    return cfg_path.parent / "reports" / f"report-{ts}.md"


def _mask(token: str | None) -> str:
    if not token:
        return "<none>"
    return token[:8] + "..." + token[-4:] if len(token) > 16 else "<short>"


def _render_report(args: argparse.Namespace, results: list[EndpointResult]) -> str:
    ts = datetime.datetime.now().isoformat(timespec="seconds")
    fails = sum(1 for r in results if not r.ok)
    lines = [
        f"# kuship vs rainbond 接口对比 — {ts}",
        "",
        f"- rainbond: `{args.rainbond}`",
        f"- kuship:   `{args.kuship}`",
        f"- 总端点:    {len(results)}",
        f"- 一致:      {len(results) - fails}",
        f"- 差异:      {fails}",
        "",
        "## 概览",
        "",
        "| id | method | rb / kp HTTP | diff 行数 | 状态 |",
        "|---|---|---|---|---|",
    ]
    for r in results:
        tag = "✅" if r.ok else "⚠️"
        lines.append(f"| `{r.id}` | {r.method} | {r.rb_status} / {r.kp_status} | {len(r.diffs)} | {tag} |")
    lines.append("")

    for r in results:
        lines.append(f"## `{r.id}`  {'✅ MATCH' if r.ok else '⚠️ DIFF'}")
        lines.append("")
        lines.append(f"**path**: `{r.method} {r.path}`")
        lines.append("")
        lines.append(f"**HTTP**: rainbond=`{r.rb_status}` kuship=`{r.kp_status}`")
        lines.append("")
        if r.diffs:
            lines.append("**diff**:")
            for d in r.diffs:
                lines.append(f"- {d}")
            lines.append("")
        if r.suppressed:
            lines.append("**suppressed (合规已知差异)**:")
            for d in r.suppressed:
                lines.append(f"- {d}")
            lines.append("")
        # 附带响应快照（截断长度防爆炸）
        lines.append("<details><summary>rainbond response</summary>\n\n```json")
        lines.append(_truncate_json(r.rb_body))
        lines.append("```\n\n</details>")
        lines.append("")
        lines.append("<details><summary>kuship response</summary>\n\n```json")
        lines.append(_truncate_json(r.kp_body))
        lines.append("```\n\n</details>")
        lines.append("")
    return "\n".join(lines)


def _truncate_json(v: Any, max_chars: int = 4000) -> str:
    s = json.dumps(v, ensure_ascii=False, indent=2, default=str)
    return s if len(s) <= max_chars else s[:max_chars] + "\n... (truncated)"


def main() -> int:
    p = argparse.ArgumentParser(description="kuship vs rainbond API diff")
    p.add_argument("--user", required=True)
    p.add_argument("--password", required=True)
    p.add_argument("--rainbond", default="http://localhost:7070")
    p.add_argument("--kuship", default="http://localhost:8080")
    p.add_argument("--config", default=str(Path(__file__).resolve().parent / "endpoints.json"))
    p.add_argument("--out", default=None)
    p.add_argument("--login-target", choices=["rainbond", "kuship", "both"], default="rainbond")
    p.add_argument("--only", default=None, help="comma-separated endpoint ids")
    p.add_argument("--timeout", type=int, default=10)
    return run(p.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
