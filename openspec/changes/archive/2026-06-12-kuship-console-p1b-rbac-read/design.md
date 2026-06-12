## Context

P1-a 落地了进控制台 6 个读接口，`GET /console/users/details` 已返回完整骨架，但三个 RBAC 派生字段以空值/默认占位：企业 `permissions=[]`、每团队 `role_name_list=[]`、`tenant_actions={}`（仅 owner / 企业管理员的 `roles` 与 `is_team_owner` 走了短路）。本轮做 RBAC **读路径**底座，把这三个字段填成与 7070 一致的真实值。

rainbond 的对应实现分两类来源：

- **企业权限**：纯计算，来自硬编码的 `console/utils/perms.py`（`ENTERPRISE`/`common_perms` 映射 + `list_enterprise_perms_by_roles`），输入只是角色名列表（`enterprise_user_perm.identity.split(",")`）。不查 RBAC 关系表。
- **团队权限**：`role_name_list` 与 `tenant_actions` 来自 DB 关系表 `role_info`/`user_role`/`role_perms`，再用 `perms.py` 的团队权限树模型（`get_team_perms_model`/`get_app_perms_model`）把权限码并集 `pack` 成布尔权限树。owner / 企业管理员短路为全 `true`。

kuship-console 已具备：`EnterpriseUserPerm`（含 `identity`）、`Tenants`、`AccountProfileService`（已注入 owner/ent_admin 短路所需上下文）。本轮新增 `modules/rbac`，并把解析结果接回 `AccountProfileService`。

约束：只读共享 console 库的 rainbond 既有表，无 schema 变更；逐字段对 7070 deep-diff 校准（见 [[kuship-7070-calibration-playbook]]）。

## Goals / Non-Goals

**Goals:**
- 移植 `perms.py` 权限码体系（分段常量 + 企业映射 + 团队/应用权限树 + 装配函数）到 Java，权限码与树结构与 7070 完全一致。
- 新增 `role_info`/`user_role`/`role_perms` 只读实体与仓储。
- 提供角色/权限解析服务：企业 `list_roles` + `list_enterprise_perms_by_roles`；团队 `get_user_roles`（role_name_list）+ `get_user_perms`（tenant_actions 树，含 owner/ent_admin 短路）。
- 把三个派生字段接回 `users/details`，对 7070 逐叶子校准通过。

**Non-Goals:**
- 不做 `check_perms` 路由级强制鉴权拦截（P1-c）。
- 不做任何 RBAC 写接口（建/改/删角色、分配权限、成员增删、建/退团队）（P1-c）。
- 不移植 `perms_info` 表的读取（读路径不需要它——名称↔码映射只在写时用），其实体留 P1-c。
- 不处理 `TenantHeaderView.get_perms` 里"指定 app_id 的细粒度 perm_apps"逻辑（那服务于带 tenantName 的鉴权，本轮 users/details 不需要）。

## Decisions

### 决策 1：权限码体系用 Java 常量 + 装配工具复刻，而非读表
- **选择**：把 `perms.py` 的 `ENTERPRISE`/`common_perms`/`TEAM`/`APP` 译成 Java 静态结构，配 `get_perms`/`pack_role_perms_tree` 等装配工具方法。
- **理由**：rainbond 自身的企业权限就是纯硬编码计算（不读 `perms_info`）；团队 `tenant_actions` 树的"形状"也来自硬编码模型，DB 只提供"哪些码为真"。复刻常量能保证树结构逐字节对齐，且无需依赖 `perms_info` 表数据是否已初始化。
- **备选**：从 `perms_info` 表读取再组装——被否，rainbond 读路径本身不这么做，且会引入表数据完整性的不确定性。

### 决策 2：`tenant_actions` 是权限树（嵌套布尔），不是权限码列表
- **选择**：严格复刻 `pack_role_perms_tree`：以团队模型为骨架，叶子 `{名称: bool, code}`，`sub_models` 嵌套；`team_app_manage` 下按团队内每个 app 生成 `app_<id>` 子树。owner/ent_admin → 所有叶子 `true`。
- **理由**：7070 实测 `tenant_actions` 是嵌套对象树而非扁平码列表；前端按树渲染。
- **风险点**：app 子模型依赖"团队下的应用列表"（rainbond 查 `ServiceGroup`）。本轮 kuship 尚无应用域——见决策 4。

### 决策 3：企业 `permissions` 形如 `group.name` 字符串集合
- **选择**：复刻 `list_enterprise_perms_by_roles`：`admin` 角色展开除 `admin` 组外所有企业子组的 `组.权限名`，并对所有角色叠加 `app_store.<common_perms 名>`。
- **理由**：与 7070 输出格式一致（字符串集合，非整型码）。注意它与 `list_enterprise_perm_codes_by_roles`（整型码集合，用于 check_perms）是两套，本轮只需前者。

### 决策 4：app 子树的应用列表来源——本轮按空应用集处理并对 7070 校准
- **选择**：kuship 尚无应用（ServiceGroup）域。先以"团队无应用"装配 `tenant_actions`（`team_app_manage` 下 app 子模型为空集），随后对 7070 同测试用户 deep-diff：
  - 若测试团队本身无应用 → kuship 与 7070 天然一致，通过；
  - 若 7070 含 app 子树而 kuship 缺 → 在 tasks 中记为已知差异，明确 defer 到应用域（不阻塞本轮 owner/普通成员的全局团队权限校准）。
- **理由**：保持本轮范围聚焦读路径底座；应用域是独立的后续刀。校准打法允许"本轮明确 defer 的差异放过"。

### 决策 5：模块落点 `modules/rbac`，解析结果由 `AccountProfileService` 消费
- **选择**：`modules/rbac/{entity,repository,service}` + `modules/rbac/perms`（常量与装配工具）。`AccountProfileService` 注入 `RbacReadService`，替换三处空占位。
- **理由**：与既有 `modules/<域>/{entity,repository,service,controller}` 分层一致；RBAC 是被 account 复用的横切读能力，独立成域便于 P1-c 在其上加写接口与 check_perms。

## Risks / Trade-offs

- **[树结构细节易错]** `sub_models` 顺序、`team_app_manage` 是第 3 个 sub_model（索引 2）等硬编码位置若译错，整棵树错位 → 缓解：移植后用 7070 实测树做递归 deep-diff，逐叶子比对。
- **[app 子树缺失]** 无应用域导致 `tenant_actions` 的 app 部分可能与 7070 不一致 → 缓解：选测试团队为"无应用"或将差异显式记为 defer（决策 4），不掩盖。
- **[common_perms 叠加细节]** 企业 `permissions` 对空角色仍叠加 `app_store.<common>`，易漏 → 缓解：以 7070 空角色用户实测为准校准。
- **[role_perms 表名]** 实际库表是 `role_perms`（非 `role_perm`），实体 `@Table` 必须钉准，否则查询为空且静默 → 缓解：建表名以 rainbond `db_table` 为唯一真相，并在联调中验证非空。
- **[owner/ent_admin 判定一致性]** 短路条件须与 P1-a/rainbond 一致（owner=团队 creater==user_id；ent_admin=identity 含 admin 或 initial admin marker）→ 复用 `AccountProfileService` 已有判定，避免两套口径。

## Migration Plan

- 纯读、无 schema 变更、无对外契约结构变更（仅 `users/details` 三字段内容从空变真值）。无需迁移；回滚即还原 `AccountProfileService` 的空占位。
- 联调沿用 P0/P1-a 环境（共享 console 库 + 同源 SECRET_KEY + 7070 all-in-one），用既有测试用户 interop 对 `users/details` 做 deep-diff。

## Open Questions

- 测试用户 interop 所在测试团队是否含应用？决定 app 子树是天然对齐还是需记 defer（决策 4，联调首步即可确认）。
- 7070 对"无企业角色"用户的 `permissions` 是否真的只剩 `app_store.<common>`？以实测为准（决策 3 / 风险点）。
