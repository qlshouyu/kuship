## Context

应用域（ServiceGroup）的读底座。`TenantGroupView.get` → `list_tenant_group_on_region(tenant, region)` = `ServiceGroup.objects.filter(tenant_id=tenant.tenant_id, region_name=region).order_by("-update_time","-order_index")`，纯 console 库。视图序列化每项仅 `{group_name, group_id(=ID), group_note(=note)}`，无 region 运行态聚合。故应用列表读可纯 DB 实现并对 7070 校准。

## Goals / Non-Goals

**Goals:** ServiceGroup 只读实体 + 应用列表读 + 对 7070 校准（空 + DB 直插非空）。
**Non-Goals:** 应用创建（create_app 调 region provision）、应用详情/运行态聚合（region）、应用其余操作。

## Decisions

### 决策 1：ServiceGroup 实体只映射所需列（validate 友好）
- 映射 `id`/`tenantId`/`groupName`/`regionName`/`note`/`orderIndex`/`updateTime`（排序用）。validate 模式只校验已映射列存在；未映射列（is_default/governance_mode 等）不影响。

### 决策 2：列表项仅 3 字段，排序在仓储完成
- `ServiceGroupRepository.findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc(tenantId, regionName)`；service 映射为 `{group_name, group_id, group_note}`。

### 决策 3：GET 无权限码门槛
- 对齐 rainbond `APP_CREATE_PERMS` 的 GET 所需码为空（团队成员可读）；不挂 `@RequiresPerms`（团队上下文仍由 TenantContextInterceptor 解析，team_name 经 region 端点带 region_name）。

### 决策 4：校准——空 live + 非空 DB 直插
- interop default 无应用 → 空列表 live 校准。非空：DB 直插一条 service_group(default tenant_id, region rainbond) → 8000 与 7070 同读该行 deep-diff → 测毕删除。纯 DB 无 region 介入。

## Risks / Trade-offs

- **[无真实应用]** 生产应用经 create_app+region 建；本环境无 → 用 DB 直插临时行校准列表读（项形态简单，3 字段，风险低）。测毕删除。
- **[region_name 过滤]** 列表按 region_name 过滤；测试行写 region_name="rainbond" 对齐查询参数。

## Migration Plan

无 schema 变更；纯只读。回滚移除 modules/app。联调：空 live + 临时行非空校准，测毕删除临时 service_group 行。

## Open Questions

- 无（列表项形态由视图源码确定且字段极简；非空数据经 DB 直插校准）。
