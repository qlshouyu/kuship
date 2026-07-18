# 对齐报告：GET /console/enterprise/{eid}/licenses

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/license.py` `LicenseLView.get`（JWTAuthApiView）→ `license_service.get_licenses(eid)`：读 `console_sys_config` 的 `AUTHZ_CODE`；无授权码或 license 无效 → HTTP 400 `{code:400, msg:"invalid authz code", msg_show:"无效授权码", bean:{authz_code}}`；有授权码有集群 → 调 region `/v2/license/status` 组装
- **kuship 实现**：`modules/enterprise/controller/LicenseController` + `LicenseService`

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/enterprise/{enterprise_id}/licenses` |
| 鉴权 | 需要（GRJWT） |
| 参数 | path: enterprise_id（**不校验存在性**，任意值同样走授权码分支） |

## 响应契约

- 无 AUTHZ_CODE（当前环境）：HTTP 400，`{code:400, msg:"invalid authz code", msg_show:"无效授权码", data:{bean:{authz_code:""}, list:[]}}`
- 有授权码：HTTP 200，bean=19 字段授权信息（authz_code/valid/reason/code/enterprise_id/company/contact/tier/cluster_id/plugin_mapping/plugins/start_at/expire_at/subscribe_until/cluster_limit/node_limit/memory_limit/cpu_limit/access_key）

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 真实 eid（无 AUTHZ_CODE 环境） | MATCH ✅ HTTP 400 + body 逐叶子+键序全一致 |
| 不存在的 eid | MATCH ✅（同上，eid 不影响该分支） |

## 本轮修复

无需修复，实现即一致。

## 未覆盖分支（本环境不可测）

- 有 AUTHZ_CODE 的有效授权分支（需真实授权码 + region `/v2/license/status`）；no_region 分支的本地 base64 解码 plugins 逻辑未移植（代码注释已标注）。待有授权码环境再校准。

## Defer

- `POST .../licenses`（更新授权码）、`licenses/.../cluster_id`、`activate`、`status` 等同族端点未实现，404 backlog。
