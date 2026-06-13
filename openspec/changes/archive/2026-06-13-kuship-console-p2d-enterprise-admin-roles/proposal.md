## Why
企业 admin 角色列表（`enterprise/{eid}/admin/roles`）是纯计算（perms.py `ENTERPRISE` 键）、可完整校准的小读接口。补齐它完善企业域读。

## What Changes
- 企业角色名列表（对齐 `AdminRolesView.get`）：`GET /console/enterprise/{enterprise_id}/admin/roles` → 返回 `ENTERPRISE` 的角色键列表 `["admin","app_store"]`（`data.list`）。纯计算，复用 `PermsCatalog.ENTERPRISE`。
- **不包含**：企业 info（多源配置）、regions 列表（含 region 资源/健康计算，近 region 边界）、admin 用户增删。

## Capabilities
### New Capabilities
- `enterprise-admin-roles`: 企业 admin 角色名列表（纯计算）。

## Impact
- 代码：`EnterpriseController`（或新方法）加 GET admin/roles，复用 `PermsCatalog.ENTERPRISE.keySet()`。
- 数据：无 DB 访问（纯计算）。
- 鉴权：JWTAuthApiView（仅登录）。
