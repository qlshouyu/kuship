## Context

P1-b 落地 RBAC 只读实体（`role_info`/`user_role`/`role_perms`）与 `PermsCatalog`（含 `packRolePermsTree` 把码并集→布尔权限树）；P1-c 落地 `@RequiresPerms`/`CheckPermsInterceptor`/`NoPermissionsException`。本轮做团队角色管理写：角色 CRUD + 角色权限树读写，是 kuship 首次对共享 console 库**写入**。

rainbond 对应实现（`console/views/perms.py` + `console/services/perm_services.py`）：
- 角色：`RoleKindService.create_role/update_role/delete_role`，`get_roles(with_default)` 取团队角色（`kind_id=tenant_id`）∪ 默认角色（`kind_id="default"`）。
- 角色权限树读：`RolePermService.get_roles_perms`（列表）/`get_role_perms`（单个，含应用子模型）——底层即 P1-b 的 `pack_role_perms_tree`。
- 角色权限树写：`update_role_perms` = `delete_role_perms` + `unpack_role_perms_tree(perms_model, role_id, get_perms_name_code_kv())`——遍历提交树，true 叶子按"分组名_权限名→码"映射写 `role_perms`。
- 权限元数据：`PermsInfoLView`（AllowAny）→ `get_perms_structure(tenant_id)`（`get_structure` 形态：name/desc/code + sub_models）。

**关键澄清**：`get_perms_name_code_kv()` 与 `get_perms_structure()` 均由 `perms.py` 硬编码计算，**不读 `perms_info` 表**。故本轮不需要 `perms_info` 实体。

## Goals / Non-Goals

**Goals:**
- `role_info`/`role_perms`/`user_role` 仓储补写方法；`PermsCatalog` 加 `getPermsNameCodeKv`/`getStructure`/`getPermsStructure`/`unpackRolePermsTree` 支撑。
- 角色写服务：create/update/delete（名校验、默认角色保护、删除事务连带清理）、角色权限树读（list/single）、写（`updateRolePerms` 事务重建）。
- 6 个接口落地并标 `@RequiresPerms`（TEAM_ROLE_PERMS）；`/console/perms` 免鉴权。
- 逐接口对 7070 deep-diff（读）+ 写后回读校准。

**Non-Goals:**
- 成员管理、团队生命周期（P1-e/后续）。
- `perms_info` 实体（本流程不读该表）。
- 应用级权限子树（依赖应用域；无应用时为空，与 7070 天然一致）。

## Decisions

### 决策 1：落点 `modules/rbac` 下扩写，不新建模块
- **选择**：角色写服务放 `modules/rbac/service`（`RoleWriteService`、`RolePermWriteService` 或合并入既有 `RbacReadService` 的姊妹类）；controller 放 `modules/rbac/controller/TeamRoleController`。仓储在 P1-b 接口上加写方法。
- **理由**：角色/权限是 RBAC 域；与 P1-b 实体、`PermsCatalog` 同域便于复用 `packRolePermsTree`。

### 决策 2：with_default = `kind_id ∈ {tenant_id, "default"}`
- **选择**：仓储加 `findByKindAndKindIdInOrderById(kind, [tenantId,"default"])` 等；mutation 的存在性校验用"团队自有"口径（`kind_id=tenant_id`），保证默认角色不可改删。
- **理由**：对齐 rainbond `Q(kind_id=kind_id)|Q(kind_id="default")`。当前库无 `kind_id="default"` 行（默认角色未播种，team 自有 3 角色即管理员/开发者/观察者），故 with_default 仅多空集，行为与 7070 一致。

### 决策 3：权限树写 = 事务删 + 降维重建
- **选择**：`updateRolePerms(roleId, permsTree)` 加 `@Transactional`：先 `rolePermsRepository.deleteByRoleId(roleId)`，再 `unpackRolePermsTree(permsTree)` 产出 `RolePerms` 行 `saveAll`。`unpackRolePermsTree` 遍历提交树：对 `app_<id>` 子节点解析 `appId`（无则 -1），对每个 `true` 叶子用 `getPermsNameCodeKv()["分组名_权限名"]` 取码，生成 `RolePerms(roleId, code, appId)`。
- **理由**：对齐 `unpack_role_perms_tree`；整体重建避免增量 diff 复杂度。
- **风险**：树形/键名错配导致码丢失或 KeyError。缓解：`getPermsNameCodeKv` 与 `packRolePermsTree` 同源（都来自 `PermsCatalog` 模板）；对单角色 PUT 后回读比对 7070。

### 决策 4：删除角色事务连带清理
- **选择**：`deleteRole` 加 `@Transactional`：删 `role_perms`（by role_id）+ `user_role`（by kind/kind_id/role_id 即该团队该角色的成员关联）+ `role_info`。仅团队自有角色可删。
- **理由**：对齐 `RoleKindService.delete_role`，避免悬挂关联。

### 决策 5：响应字段对 7070 实测校准，不靠源码推断
- **选择**：创建/详情/权限树等 bean 形态先抓 7070 真实响应存基线，再实现比对（沿用校准打法）。`to_dict` 去 `kind`/`kind_id` 的细节以实测为准。
- **理由**：P0~P1-c 均验证此法最可靠。

### 决策 6：写操作联调用可丢弃角色，不动 interop 默认 3 角色
- **选择**：校准 create/update/delete/perms 时新建临时角色（如 `p1d_tmp`）操作，删除后还原；读类接口用既有角色。
- **理由**：共享库写有破坏性；保护 interop 的管理员/开发者/观察者及其 role_perms。

## Risks / Trade-offs

- **[首次写共享库]** 误写污染 rainbond 数据 → 缓解：决策 6 用可丢弃角色；事务保证一致性；validate 模式仍禁 DDL。
- **[unpack 键名/树形错配]** → 缓解：kv 与 pack 同源 + 写后回读 diff（决策 3/5）。
- **[RegionTenantHeaderView 的 region 依赖]** rainbond 部分角色接口继承 `RegionTenantHeaderView`，可能要求 `region_name` 查询参数 → 见 Open Question；按 7070 实测决定是否接收/忽略。
- **[默认角色判定]** 当前库无 `kind_id="default"` 行，全 3 角色为 team 自有（可改删）→ 与 7070 一致；若未来播种默认角色，保护逻辑已就位。
- **[名↔码 kv 覆盖]** 提交树若含 catalog 未定义的键 → KeyError/忽略。缓解：未知键跳过并 log（不崩溃），以 catalog 为准。

## Migration Plan

- 无 schema 变更；引入对 `role_info`/`role_perms`/`user_role` 的写。回滚即移除写 controller/service 与 `@RequiresPerms` 标注。
- 联调：7070 抓基线 → 实现 → 写后回读 deep-diff；写操作用可丢弃角色。

## Open Questions

- 角色接口（继承 `RegionTenantHeaderView`）在 7070 是否强制 `region_name` 查询参数？以实测决定 kuship 是否接收/忽略该参数（功能上角色读写不需要 region）。
- `roles` 列表项除 `name`/`ID` 外 7070 是否还返回其它字段？`get_role_perms` 单角色 bean 的精确包裹（是否含 `role_id`/`permissions` 外层）？均以 7070 实测为准。
