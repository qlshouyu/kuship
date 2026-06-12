## Context

团队建/删。已实测确认：`create_team` 本身纯 console DB（建 tenant_info + tenant_perms owner + 3 默认角色 + 管理员 user_role）；region provision 仅在 `AddTeamView` 对 `useable_regions` 非空时的循环里发生。`delete_by_tenant_id` 对无 region 团队仅删 tenant_perms + tenant_info（不级联 role_info/user_role）。故无 region 绑定路径全程纯 DB、可对 7070 校准。

## Goals / Non-Goals

**Goals:** 无 region 路径的团队建（含默认角色初始化 + owner）/删，对 7070 校准；2 接口。
**Non-Goals:** region 绑定 provision/卸载（依赖 region 域）；成员加入 AdminAddUserView；改名/移交/退出（P1-g 已做）。

## Decisions

### 决策 1：新建 TeamLifecycleService + TeamLifecycleController（modules/team）
- 复用 Tenants/PermRelTenant/RoleInfo/RolePerms/UserRole 仓储写；`PermsCatalog` 加 `DEFAULT_TEAM_ROLE_PERMS`。

### 决策 2：create_team 忠实复刻（事务）
- 随机 tenant_name：8 位 `[a-z0-9]`，查重不撞（对齐 random_tenant_name；用 java.util.Random）。
- 事务：save Tenants（creater=user, limit_memory=0, namespace/logo/alias, create_time/update_time=now, is_active 取 7070 新团队实测值）→ save PermRelTenant(user_id, tenant_id=team.ID, identity="owner", enterprise_id=企业PK int) → 建 3 角色(RoleInfo kind=team/kind_id=tenant_id) + 各 role_perms(DEFAULT_TEAM_ROLE_PERMS, app_id=-1) → user_role(creater, 管理员.ID)。
- 企业 PK：经 TenantEnterprise(eid).getId()（tenant_perms.enterprise_id 是 int PK）。

### 决策 3：namespace 校验返回干净 400（不复刻 rainbond 源 bug）
- rainbond 非法 namespace 时 ErrQualifiedName 文案含未转义引号 → 实际抛 TypeError/500。kuship 返回 400 + 规范文案（按校准打法对源 bug 可偏离，docs 记录）。
- `is_qualified_name`：正则 `^[a-z]([-a-z0-9]*[a-z0-9])?$`（移植 console/utils/validation.py）。

### 决策 4：delete 忠实——只删 tenant_perms + tenant_info
- 对齐 `team_repo.delete_by_tenant_id`：不级联 role_info/user_role（留孤儿，与 rainbond 一致）。团队不存在→404「{team}团队不存在」。无 region 团队不涉及 region 卸载。

### 决策 5：时间戳格式化 + bean to_dict 12 字段
- 复用 P1-g 的 yyyy-MM-dd HH:mm:ss 格式化（LocalDateTime 默认 ISO 不符 7070）。bean 字段集/顺序同 P1-g modifyname。

## Risks / Trade-offs

- **[建/删真实团队]** 联调用可丢弃团队：建→校准→删→清理孤儿 role_info/user_role；绝不动 default。
- **[is_active 默认值]** 新团队 is_active 以 7070 实测为准（建团队后读 bean 确认）。
- **[随机名不可 diff]** tenant_name 随机 → 建团队响应的 tenant_name 不参与逐字节 diff（仅验存在/格式），其余字段比对。
- **[region 绑定缺失]** useable_regions 非空时 kuship 不 provision → 与 7070（会 provision）行为不同；本轮只测/支持空 region 路径，docs 明确 defer。

## Migration Plan

无 schema 变更。回滚移除 2 路由与服务、PermsCatalog 的 DEFAULT_TEAM_ROLE_PERMS。联调：7070 已验建/删契约；写用可丢弃团队，测毕清理（含孤儿）。

## Open Questions

- 新团队 is_active 7070 返回值（true？）——建团队后读 bean 确认并对齐。
- create 响应是否含除 bean 外的额外字段——以实测为准（已知 msg=success/msg_show=团队添加成功）。
