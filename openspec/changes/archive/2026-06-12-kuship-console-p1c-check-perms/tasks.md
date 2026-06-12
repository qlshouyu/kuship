## 1. 联调与参照准备

- [x] 1.1 确认 7070/8000/mysql/redis 在线；从运行版 console 进程取真实 SECRET_KEY 回写 `/tmp/kuship_secret.txt`（见校准打法的 secret 失效坑）
- [x] 1.2 抓存 7070 无权响应基线：用一个低权限用户（在 default 团队建一个无 `describe`/观察者成员，或临时调整角色）请求 `teams/{team}/overview`，记录 HTTP 403 + 信封 `code/msg/msg_show/data` 形态到 `docs/p1c-7070-reference.md`
- [x] 1.3 记录有权基线：interop（admin）请求同接口 200，及其团队权限码并集口径

## 2. 整型权限码展开（PermsCatalog 扩展）

- [x] 2.1 `getEnterpriseAdminerCodes()`：团队 + 企业全部权限码集合（对齐 `get_enterprise_adminer_codes`/`get_perm_code`）
- [x] 2.2 `listEnterprisePermCodesByRole(role)` / `listEnterprisePermCodesByRoles(roles)`：admin→全码；其它→角色码 + common_perms 码；恒叠加 common_perms 码
- [x] 2.3 `allTeamPermCodes()`：团队 kind 全部权限码（owner 短路用）
- [x] 2.4 单测：admin 全码、非 admin 叠加 common、与 7070 口径一致

## 3. 异常体系扩展（无权 403/10402）

- [x] 3.1 `ServiceHandleException` 增 `errorCode`（默认回落 `status`），保持既有抛点行为不变
- [x] 3.2 新增 `NoPermissionsException`（status=403、errorCode=10402、msg=`no permissions `、msgShow=`没有操作权限`）
- [x] 3.3 `GlobalExceptionHandler` 信封 `code` 取 `errorCode`、HTTP 取 `status`；回归既有异常单测
- [x] 3.4 单测：无权异常渲染为 403 + code=10402 + msg_show

## 4. 用户权限码计算（AuthorizationService）

- [x] 4.1 `enterprisePermCodes(user)`：listRoles → listEnterprisePermCodesByRoles（复用 P1-b RbacReadService.listRoles）
- [x] 4.2 `teamPermCodes(tenantId, user, isOwner)`：企业码 ∪（owner ? allTeamPermCodes+100001 : 成员 role_perms 全局码并集）。**仅 owner 短路**，企业管理员经企业码展开通过（见 design 决策 3 校正）
- [x] 4.3 owner 判定：团队 creater==user_id（与 P1-b users/details 同口径）；企业 admin 由 listRoles→admin 整型展开覆盖
- [x] 4.4 单测：owner 全团队码+100001、企业 admin 经企业码含团队码、成员按码、企业码并入

## 5. 路由绑定与 check_perms 拦截

- [x] 5.1 定义 `@RequiresPerms(kind, codes[])` 注解（kind=ENTERPRISE/TEAM）
- [x] 5.2 `CheckPermsInterceptor`：读 HandlerMethod 的 `@RequiresPerms`；ENTERPRISE→enterprisePermCodes，TEAM→解析团队（team_name+企业）后 teamPermCodes；所需码⊄用户码则抛 `NoPermissionsException`；无注解放行
- [x] 5.3 `WebMvcConfig` 注册拦截器，排在 `TenantContextInterceptor` 之后；防御无 currentUser
- [x] 5.4 团队解析结果放入 `RequestContext` 供业务复用（避免重复查询；团队不存在交既有 not found 路径）
- [x] 5.5 单测：有权放行、无权抛 NoPermissions、无注解放行、空所需码放行

## 6. 落地团队 overview 并校准

- [x] 6.1 `teams/{team_name}/overview` 处理方法标注 `@RequiresPerms(TEAM, {200001})`
- [x] 6.2 实跑校准：interop（有 200001）→ 200；低权用户（无 200001）→ 403/10402，与 `docs/p1c-7070-reference.md` 一致
- [x] 6.3 全量构建 + 现有单测通过；docs 记录已知差异/defer（企业级接口是否需标注按 7070 实测）
- [x] 6.4 走 openspec 校验（`openspec validate`），准备归档
