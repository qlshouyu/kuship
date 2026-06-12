## Why

团队/RBAC 域已闭环，下一域是应用（ServiceGroup）。其最基础、纯 console 库、可校准的读面是"团队应用列表"。先落地它作为应用域的读底座：引入 `ServiceGroup` 实体与按团队+集群的列表读。应用创建/运行态聚合（依赖 region 后端）留后续。

## What Changes

- 引入 `ServiceGroup` 只读实体（映射既有 `service_group` 表，PK 大写 ID）。
- 应用列表读（对齐 `TenantGroupView.get` + `list_tenant_group_on_region`）：`GET /console/teams/{team_name}/groups?region_name=` → 按 `tenant_id`+`region_name` 查 `service_group`，按 `update_time` 降序、`order_index` 降序排序；每项 `{group_name, group_id, group_note}`（纯 DB，3 字段，无 region 运行态聚合）。
- **不包含**（顺延，依赖 region）：应用创建（`POST groups` → `create_app` 会调 region provision）、应用详情/运行态（组件数、状态等聚合自 region）、应用其余操作。本轮仅纯 DB 的应用列表读。

## Capabilities

### New Capabilities
- `app-read`: 团队应用（ServiceGroup）列表读，纯 console 库。

## Impact

- 代码：新增 `modules/app`（ServiceGroup 实体/仓储/服务/controller）。
- 数据：只读 `service_group`；无 schema 变更、不触 region。
- 接口：新增 `GET /console/teams/{team_name}/groups`；空列表 live 校准（interop default 无应用），非空形态以 DB 直插临时应用行校准（建后即删）。
- 鉴权：对齐 rainbond 路由 `APP_CREATE_PERMS` 的 GET（所需码为空，团队成员可读）——不挂权限码门槛。
- 不变：不引入应用创建/region 聚合。
