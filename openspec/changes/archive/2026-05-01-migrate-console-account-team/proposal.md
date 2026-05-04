## Why

`migrate-console-region-client` 已落实 region API 客户端基础设施，但任何业务接口（应用 / 集群 / 插件……）都先要解决"用户是谁、所在哪个 team / enterprise、有什么权限"。`kuship-ui` 启动后第一个走的也是 `POST /console/users/login` 然后用拿到的 JWT 访问其他 `/console/*` 端点，因此本 change 是首个"端到端可登录"的里程碑——做完之后用户就能用 rainbond-console 既有账号登录 kuship-console，并在前端完成 team 切换、角色管理等基础导航。

由于账户/团队相关代码在 rainbond-console 中达 ~5.6K LOC（user.py 430 + team.py 1231 + enterprise.py 1148 + oauth.py 767 + perms 291 + accesstoken 77 + user_operation 844 + enterprise_active/config 400 + team_overview/resources 433），整体打包风险过大，本 change 严格聚焦 **MVP 闭环：登录 + 基础 RBAC + 团队/企业/权限的核心读写**，OAuth/审计日志/资源聚合/激活配置等留给后续 change。

## What Changes

### 用户认证与自我（user 模块）

- ADDED：`UserAuthController`
  - `POST /console/users/login` —— 用户名/密码 → JWT（HS256，与 rainbond-console 同 secret，跨服务可互认）
  - `POST /console/users/logout` —— 服务端不维护 session，仅返回 `general_message` 占位（与 rainbond-console 一致）
  - `POST /console/users/register` —— 注册 + 自动建默认 team + 默认绑定到 enterprise
  - `POST /console/users/changepwd` —— 当前用户改密码（旧/新）
- ADDED：`UserSelfController`
  - `GET /console/users/details` —— 当前用户全量信息（含 enterprise / 默认 team / is_user_enable / origin / phone）
  - `GET /console/users/team_details` —— 当前用户所有 team
  - `GET /console/users/query?query={kw}` —— 用户模糊搜索（按用户名 / nick_name / email / phone）
  - `GET/PUT /console/users/custom_configs` —— 用户自定义配置 JSON（前端持久化用户偏好）
- ADDED：`UserAccessTokenController`
  - `GET /console/users/access-token`
  - `POST /console/users/access-token`
  - `DELETE /console/users/access-token/{id}`

### 团队成员 / 角色 / 权限（team 模块）

- ADDED：`TeamController`
  - `POST /console/teams/init` —— 创建 team（含 namespace、enterprise_id、region 绑定）
  - `PUT /console/teams/{team_name}` —— 改 team 名 / 别名
  - `DELETE /console/teams/{team_name}` —— 删 team（仅 owner 或企业管理员）
  - `POST /console/teams/{team_name}/exit` —— 当前用户退出该 team
- ADDED：`TeamMemberController`
  - `GET /console/teams/{team_name}/users` —— 成员列表（分页）
  - `POST /console/teams/{team_name}/users` —— 添加成员（user_ids + role_ids）
  - `DELETE /console/teams/{team_name}/users/batch/delete` —— 批量删除成员
  - `GET /console/teams/{team_name}/notjoinusers` —— 当前 enterprise 内未在该 team 的用户
  - `POST /console/teams/{team_name}/pemtransfer` —— 转让 team owner
- ADDED：`TeamRoleController`
  - `GET/POST /console/teams/{team_name}/roles`
  - `GET/PUT/DELETE /console/teams/{team_name}/roles/{role_id}`
  - `GET /console/teams/{team_name}/roles/perms` —— 该 team 所有 role 的 perms 矩阵
  - `GET/PUT /console/teams/{team_name}/roles/{role_id}/perms`
  - `GET /console/teams/{team_name}/users/roles` —— 成员-角色矩阵
  - `PUT/DELETE /console/teams/{team_name}/users/{user_id}/roles`

### 企业（enterprise 模块）

- ADDED：`EnterpriseController`
  - `GET /console/enterprise/info` —— 平台默认 enterprise 信息（**未授权可访问**，登录页用）
  - `GET /console/enterprises` —— 当前用户所属所有 enterprise
  - `GET/PUT /console/enterprise/{enterprise_id}` —— enterprise 详情 / 改名
  - `GET /console/enterprise/{enterprise_id}/teams` —— enterprise 内所有 team
  - `GET /console/enterprise/{enterprise_id}/myteams` —— 当前用户在该 enterprise 中的 team
- ADDED：`EnterpriseUserController`
  - `GET/POST /console/enterprise/{enterprise_id}/users` —— enterprise 内用户列表 / 创建用户
  - `PUT/DELETE /console/enterprise/{enterprise_id}/user/{user_id}`
  - `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`
  - `GET/POST/DELETE /console/enterprise/{enterprise_id}/admin/user[/{user_id}]` —— 企业管理员管理
  - `GET /console/enterprise/{enterprise_id}/admin/roles` —— 企业管理员角色
  - `GET/PUT /console/enterprise/{enterprise_id}/users/{user_id}/teams/{tenant_name}/roles` —— 跨 team 调整成员角色
  - `POST /console/enterprise/admin/add-user` / `POST /console/enterprise/admin/join-team`

### 权限元数据

- ADDED：`PermsController`
  - `GET /console/perms` —— 所有权限码 + 分组 + 中文标签（前端权限树渲染用）
  - `POST /console/init/perms` —— 初始化（仅启动一次，确保 perm_info / role_info 默认数据存在）

### JPA Entity / Repository

- ADDED：`UserInfo` / `Tenants`(team) / `TenantEnterprise` / `PermGroup` / `PermsInfo` / `RoleInfo` / `RolePerms` / `UserRole` / `TenantUserPerm` / `EnterpriseUserPerm` / `UserAccessKey` 等 ~12 entity（共享 `console` DB，只读 schema 不发 DDL）
- ADDED：对应 Spring Data JPA Repository，关键查询走 QueryDSL（用户模糊搜索 / team-user 关联查询）

### 安全集成

- MODIFIED：在 `migrate-console-response-contract` 已落地的 `JwtAuthFilter` 上，从 token payload 的 `user_id` 真实加载 `UserInfo` → `RequestContext`（之前是 stub），并在 `RequestContext` 中加 `enterpriseId` / `tenantName`（从路径变量回填）
- MODIFIED：`SecurityConfig` 放行 `/console/users/login`、`/console/users/register`、`/console/enterprise/info`、`/console/perms`（perms 元数据公开），其他路径要求 JWT
- ADDED：`@RequirePerm("xxx")` 注解 + `PermAspect` —— 在 controller 方法上标注权限码，自动校验 `RequestContext.user` 在当前 `team_name` 下是否拥有该权限；权限码完全沿用 rainbond-console `console/utils/perms.py` 的常量（如 `team_member_perms`、`team_role_perms`）

### 不进入此 change（明确 punt）

- 邮件相关：`send_reset_email`、`begin_password_reset` —— 需邮件服务，留给 `migrate-console-misc`
- 短信相关：`register-by-phone`、`login-by-phone` —— 需短信网关，留给 `migrate-console-misc`
- 邀请链接：`/users/invite[/{id}]` —— 留给 `migrate-console-misc`
- OAuth 完整体系（`/console/oauth/*`，~767 LOC + 多 provider 适配）—— 单独 change `migrate-console-oauth`
- 企业激活/license/object-storage/visualmonitor/alerts —— `migrate-console-enterprise-advanced`
- 企业聚合视图（overview/monitor/operation-logs/login-events）—— `migrate-console-misc`
- team_overview / team_resources —— `migrate-console-application-core`（依赖 region 资源聚合）
- 备份/恢复 —— `migrate-console-misc`
- registry/auth、cluster_namespaces、resource-name —— `migrate-console-region-cluster`

## Capabilities

### New Capabilities

无（不引入新 capability）

### Modified Capabilities

- `kuship-console-app`：在已有 22 requirements 基础上 ADDED 账户认证 / RBAC / 团队管理 / 企业管理 / 权限元数据相关 requirements；MODIFIED 现有的 "JWT 鉴权" requirement（payload claim 加载用户）和 "RequestContext" requirement（增 enterpriseId / tenantName）

## Impact

- **新增依赖**：无新增 maven 依赖；密码哈希直接复刻 rainbond-console 的自定义算法（`www/utils/crypt.py::encrypt_passwd`）—— Java 实现 `SHA-224(c7 + email+raw + c5 + 'goodrain' + c2/7)` 后取 16 位 hex，**不切换到 bcrypt**，保证 kuship-console 与 rainbond-console 共用同一份 `user_info.password` 字段，跨服务可登录
- **共享数据库**：本 change 把 `console` DB 中 ~12 张账户/团队/权限表纳入 JPA 管理（read + 部分 write）；schema 演进权仍归 rainbond-console，本 change 永不发 DDL
- **跨服务**：rainbond-console 与 kuship-console 共用 SECRET_KEY 后，两边签发的 JWT 可互认；用户在 rainbond-console 登录后拿到的 token，可直接调 kuship-console 的端点，反之亦然
- **kuship-ui**：login/logout/details/team_details/perms/teams/enterprise 全链路 unblock；前端 `services/user.js` / `services/team.js` 中已存在的调用全部可用
