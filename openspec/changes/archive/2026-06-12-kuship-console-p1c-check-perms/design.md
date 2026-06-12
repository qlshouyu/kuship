## Context

P1-b 完成 RBAC 读：`PermsCatalog`（权限码定义 + 字符串权限展开）、只读实体 `role_info`/`user_role`/`role_perms`、`RbacReadService`（角色/权限树解析）。但请求未受权限拦截。

rainbond 的强制鉴权（`console/views/base.py`）：每条受保护路由在 Django URL 第三参带一份 `__message`（`perms_route_config.py`，按方法声明所需整型码）；视图 `initial()` 先解析用户/企业/团队上下文，计算 `self.user_perms`（整型码集合），再 `check_perms` 取当前方法所需码与 `user_perms` 求交，缺则抛 `NoPermissionsError`（403/10402）。两类 `get_perms`：`JWTAuthApiView` 只算企业码；`TenantHeaderView` 在企业码上叠加团队码（owner→全团队码+100001，企业管理员等同 owner，成员→`role_perms` 全局码并集）。

kuship 现状：P0 的 JWT 过滤器注入 `RequestContext.currentUser`；`TenantContextInterceptor` 从路径变量注入 `team_name`；`ServiceHandleException` 把 HTTP status 当信封 code 用；团队域接口仅 `teams/{team_name}/overview`。约束：只读共享库、对 7070 校准。

## Goals / Non-Goals

**Goals:**
- 整型权限码展开（`listEnterprisePermCodesByRoles`/`getEnterpriseAdminerCodes`），补进 `PermsCatalog`。
- 用户权限码计算服务：企业级 + 团队级（owner/企业管理员短路、成员角色码并集），复用 P1-b 仓储。
- 路由→所需码绑定（注解 `@RequiresPerms`）+ `CheckPermsInterceptor` 在认证/上下文之后、业务之前校验。
- 无权异常 → 403 + 信封 `code=10402`、`msg_show=没有操作权限`；异常体系支持独立 error_code。
- 把 `teams/{team_name}/overview` 纳入鉴权（需 200001），对 7070 校准有权/无权。

**Non-Goals:**
- 团队角色/成员/权限写接口 CRUD、建/退团队（P1-d）。
- `perms_info` 实体（写时名↔码映射用）（P1-d）。
- 应用级 `perm_apps` 细粒度鉴权（依赖应用域）。
- 全量移植 `perms_route_config` 的所有条目——仅落地当前已存在的受保护接口；其余接口随其实现再标注。

## Decisions

### 决策 1：路由所需码用方法注解 `@RequiresPerms`，而非 Django 式路径表
- **选择**：在 controller 处理方法上标注 `@RequiresPerms(kind = TEAM, codes = {200001})`。Spring 中一个 `@GetMapping` 方法天然对应单一 HTTP 方法，等价 rainbond `__message[method]`。`CheckPermsInterceptor` 从 `HandlerMethod` 读注解。
- **理由**：类型安全、与处理方法同处可见、避免维护一张与路由并行的路径正则表；语义等价 `perms_route_config`。
- **备选**：路径 pattern→码表（更贴近 rainbond 数据驱动）——被否，kuship 路由用 Spring 注解而非集中式 URLConf，路径表会与 `@RequestMapping` 重复且易漂移。

### 决策 2：用 `HandlerInterceptor` 接入，排在认证与团队上下文之后
- **选择**：新增 `CheckPermsInterceptor`（preHandle），在 JWT 过滤器（已注入 currentUser）与 `TenantContextInterceptor`（已注入 team_name）之后执行；无 `@RequiresPerms` 的 handler 直接放行。
- **理由**：对齐 rainbond `initial()` 中"先上下文、再 check_perms、最后业务"的次序；拦截器能拿到 `HandlerMethod` 注解与 `RequestContext`。
- **次序要点**：团队级鉴权需先确定团队（取 owner 判定与团队码），故拦截器对 `kind=TEAM` 时按 `team_name + 当前用户企业` 解析团队实体（复用 P1-b/P1-a 的团队解析）；团队不存在则交由既有"team not found"路径（与 7070 一致）。

### 决策 3：用户权限码计算复用 P1-b 数据，新增整型展开；团队码仅 owner 短路
- **选择**：`AuthorizationService.enterprisePermCodes(user)` = `listEnterprisePermCodesByRoles(listRoles(...))`；`teamPermCodes(tenantId, user, isOwner)` = 企业码 ∪（**owner**（user==creater）? 全团队码+100001 : 成员 `role_perms` 全局码并集）。
- **关键校正（实现中发现）**：`TenantHeaderView.get_perms` 的团队码短路**只看 is_team_owner**，不把企业管理员当 owner。企业管理员能过团队鉴权，是因其 `admin` 角色经整型码展开已含全部团队码（走企业码这条），而非 owner 短路。rainbond 里 `is_enterprise_admin` 仅按"存在 enterprise_user_perm 行"置真且只影响 perm_apps 可见性——若据此短路全团队码，会对 identity≠admin 的企业成员**多放行**。故本服务**不引入 ent-admin 团队码短路**，只忠实 owner 短路 + 企业码展开。
- **owner 判定**：团队 `creater == user_id`（与 P1-b users/details 同口径）。
- **注意**：整型 `list_enterprise_perm_codes_by_roles`（鉴权用）与 P1-b 字符串 `list_enterprise_perms_by_roles`（users/details 展示用）是两套，不可混用。这也意味着 check_perms 的团队码口径与 P1-b `tenant_actions`（用 `get_user_perms` 且对 is_ent_admin 短路展示）刻意不同——前者鉴权、后者展示。

### 决策 4：异常体系引入独立业务 error_code
- **选择**：给 `ServiceHandleException` 增加 `errorCode` 字段（默认等于 `status`，保持 P0/P1 既有行为不变），新增 `NoPermissionsException`（status=403、errorCode=10402、msg/msgShow 对齐）。`GlobalExceptionHandler` 渲染时信封 `code` 取 `errorCode`、HTTP 取 `status`。
- **理由**：rainbond 无权响应 HTTP 403 但信封 `code=10402`（实测登录失败信封 `code=10401` 亦印证 code=error_code 而非 status）。现有 `ServiceHandleException` 把 status 当 code，需扩展才能精确对齐。
- **风险**：改 `GlobalExceptionHandler` 可能影响既有错误响应的 code。缓解：`errorCode` 默认回落 `status`，既有抛点行为不变；仅无权场景显式设 10402。回归现有异常处理单测。

### 决策 5：无权校准需低权限用户
- **选择**：现仅 interop=admin（全权），无法触发 403。校准时在 default 团队建一个仅含"观察者"或无 `describe` 的成员用户，或临时移除其 200001，验证 403/10402；有权路径仍用 interop 校准 200。
- **理由**：校准打法要求实跑验证两个分支；403 分支必须有低权用户。

## Risks / Trade-offs

- **[拦截器次序错乱]** 若 `CheckPermsInterceptor` 早于 JWT 认证或团队上下文，则拿不到 user/team → 误判 → 缓解：在 `WebMvcConfig` 显式排序，置于 `TenantContextInterceptor` 之后；并对"无 currentUser"防御（理论上 P0 已拦截未认证）。
- **[error_code 回归]** 见决策 4 缓解（默认回落 + 跑既有异常单测）。
- **[owner/admin 判定双口径漂移]** 见决策 3（复用 P1-b 同一判定）。
- **[团队解析重复查询]** 拦截器与业务都查团队 → 轻微重复。缓解：可将解析后的团队放入 `RequestContext` 供业务复用；本轮接口少，先接受重复，必要时再缓存。
- **[perms_route_config 漂移]** 仅落地现有接口的码，未来接口需同步标注 → 缓解：约定"新增受保护接口必须带 `@RequiresPerms`"，并在 design/Non-Goals 注明其余条目随实现补。

## Migration Plan

- 纯读、无 schema 变更。受保护接口无权行为从 200→403（有权不变）。无数据迁移；回滚即移除 `@RequiresPerms` 标注与拦截器注册。
- 联调沿用 7070 环境；有权用 interop（200），无权需低权成员用户（403/10402）。

## Open Questions

- 现有企业级读接口（`/console/enterprises`、`enterprise/{id}/overview`、`teams` 列表）在 7070 是否带 `__message`（即是否需要企业级 check_perms）？本轮先只确保机制可用并落地团队 overview；企业级接口按 7070 实测决定是否补标注。
- 无权信封除 `code/msg/msg_show` 外是否还有 `data` 形态差异？以 7070 实测 403 响应为准校准。
