## ADDED Requirements

### Requirement: 企业 admin 角色名列表
系统 SHALL 提供 `GET /console/enterprise/{enterprise_id}/admin/roles`（对齐 `AdminRolesView.get`），返回 `PermsCatalog.ENTERPRISE` 的角色键列表（`["admin","app_store"]`，顺序与定义一致），置于 `data.list`。

#### Scenario: 返回企业角色名
- **WHEN** 已认证用户请求 `GET /console/enterprise/{enterprise_id}/admin/roles`
- **THEN** 返回 `code=200`、`data.list=["admin","app_store"]`，与 7070 一致
