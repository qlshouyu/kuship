## ADDED Requirements

### Requirement: 用户认证端点（兼容 rainbond-console 登录链路）

kuship-console SHALL 提供 `/console/users/login`、`/console/users/logout`、`/console/users/register`、`/console/users/changepwd` 四个公开/半公开端点，请求/响应 JSON 形状与 rainbond-console 一致，使前端 `kuship-ui` 现有 `services/user.js` 中的调用代码不需改动即可工作。

`/console/users/login` 与 `/console/users/register` SHALL 加入 SecurityConfig 的 permitAll 白名单；`/console/users/logout` 与 `/console/users/changepwd` 要求 JWT 认证。

#### Scenario: 用户名+密码登录成功签发 token

- **WHEN** 客户端发送 `POST /console/users/login`，body `{"nick_name":"admin","password":"goodrain"}`
- **THEN** 响应 `code=200`、`data.bean.token` 为 HS256 签名的 JWT，payload 含 `user_id`、`username`、`email`、`exp`（now+3650 天）、`orig_iat`
- **AND** 响应同时返回 `data.bean.user` 字段，含 `user_id` / `nick_name` / `email` / `enterprise_id`

#### Scenario: 错误密码登录失败

- **WHEN** 客户端发送 `POST /console/users/login` 带错误 password
- **THEN** 响应 `code=400`、`msg_show="用户名或密码错误"`，HTTP 200（业务码与 HTTP 解耦）

#### Scenario: 注册新用户

- **WHEN** 客户端发送 `POST /console/users/register`，body 含 `nick_name`、`email`、`password`（≥8 字符）、`real_name`、`phone`（可选）
- **THEN** 响应 `code=200`，新用户写入 `user_info` 表，密码字段经 `LegacyPasswordEncoder.encode(email + password)` 计算
- **AND** 自动给该用户绑定到默认 enterprise（首个 `tenant_enterprise.is_active=1` 的）；自动建一个默认 team（namespace 用 `nick_name + "-default"`）并把用户作为 owner

#### Scenario: 登出

- **WHEN** 已登录用户发送 `POST /console/users/logout`
- **THEN** 响应 `code=200`、`msg="logout success"`；服务端不维护 session，仅是占位响应

#### Scenario: 改密码

- **WHEN** 已登录用户发送 `POST /console/users/changepwd`，body `{"old_password":"x","new_password":"y"}`
- **THEN** 旧密码经 `LegacyPasswordEncoder.matches` 校验，校验通过则更新 `user_info.password`；新密码长度 < 8 触发 400

### Requirement: 密码哈希算法兼容 rainbond-console（自定义 SHA-224 截断）

kuship-console SHALL 提供 `LegacyPasswordEncoder`（实现 Spring Security 的 `PasswordEncoder` 接口），完全复刻 rainbond-console `www/utils/crypt.py::encrypt_passwd` 的算法：

```
input  = email + rawPassword          // length >= 8 强制
word   = ord(input[7]) + input + ord(input[5]) + "goodrain" + (ord(input[2]) / 7)
hash   = SHA-224(word.utf8).hex
result = hash[:16]                    // 截断 16 字符 hex
```

`encode(rawPassword)` 与 `matches(rawPassword, encoded)` 必须与 rainbond-console 输出二进制一致；针对 `input.length < 8`（如 email 为空、或调用方传 raw 而非 email+raw）SHALL 抛 `IllegalArgumentException("password material too short")`。

#### Scenario: 与 rainbond-console 输出一致

- **WHEN** 给定 `email="alice@example.com"`、`rawPassword="goodrain"`
- **THEN** `encode("alice@example.comgoodrain")` 输出与 rainbond-console Python `encrypt_passwd("alice@example.comgoodrain")` 完全相同（写一组 fixture 用例覆盖至少 5 组真实样本）

#### Scenario: matches 通过 hash 比对

- **WHEN** 数据库已存 `user_info.password="abcd1234efgh5678"`（rainbond 写入的样本）
- **AND** 调用 `matches("rawPwd", "abcd1234efgh5678")`
- **THEN** 返回 true 当且仅当 `encode("rawPwd")` 等于 `"abcd1234efgh5678"`

### Requirement: 跨服务 JWT 互认（rainbond-console 与 kuship-console 共用 SECRET_KEY）

kuship-console 的 `JwtIssuer` SHALL 使用与 rainbond-console 相同的 SECRET_KEY、相同的 HS256 算法、相同的 payload claim 命名（`user_id`、`username`、`email`、`exp`、`orig_iat`），且 `exp` 默认 `iat + 3650 天`。当 rainbond-console 与 kuship-console 配置同一个 `JWT_SECRET_KEY` 环境变量时：rainbond 签发的 token 在 kuship 端可解析，反之亦然，无需 token 转换层。

#### Scenario: rainbond token 在 kuship 解析成功

- **WHEN** 一个由 rainbond-console 签发的 token（payload `{"user_id":1,"username":"admin","email":"x@y","exp":...,"orig_iat":...}`）作为 `Authorization: GRJWT <token>` 发送到 kuship-console 任意需认证端点
- **THEN** 鉴权通过，`RequestContext.userId=1` 且实际从 `user_info` 表加载用户进 RequestContext

#### Scenario: kuship 签发的 token 反向兼容 rainbond

- **WHEN** kuship-console 通过 `/users/login` 签发 token
- **AND** 该 token 被 rainbond-console（同 SECRET_KEY 部署）的 `rest_framework_jwt` 解析
- **THEN** rainbond 鉴权通过（payload 字段一致）

### Requirement: 用户自我端点

kuship-console SHALL 提供 `/console/users/details`、`/console/users/team_details`、`/console/users/query`、`/console/users/custom_configs` 四个端点，返回结构与 rainbond-console 兼容。

#### Scenario: GET /console/users/details

- **WHEN** 已登录用户访问
- **THEN** 响应 `code=200`、`data.bean` 含 `user_id`、`nick_name`、`email`、`phone`、`real_name`、`is_user_enable`（=`is_active`）、`enterprise_id`、`origin`（默认 `"register"`）、`logo`

#### Scenario: GET /console/users/team_details

- **WHEN** 已登录用户访问
- **THEN** 响应 `data.list` 为该用户所有 team 的列表，每项含 `team_name`、`team_alias`、`tenant_id`、`region_list`（关联 `team_region` 联表查得）、`role_infos`（该用户在该 team 中的所有 role）

#### Scenario: 用户模糊搜索

- **WHEN** 客户端访问 `GET /console/users/query?query=abc`
- **THEN** 响应 `data.list` 为 `nick_name` / `email` / `phone` 任一字段 like `%abc%` 的用户分页结果

#### Scenario: 自定义配置读写

- **WHEN** `PUT /console/users/custom_configs` body `{"key":"theme","value":"dark"}`
- **THEN** 写入 `user_info.custom_configs` 字段（JSON 列）；后续 `GET` 同端点能读到

### Requirement: 用户 PAT (UserAccessToken) 管理

kuship-console SHALL 提供 `/console/users/access-token` 端点，让用户管理 Personal Access Token。本 change 仅落 PAT 的生成与列表/删除；PAT 在 `/openapi/v1/*` 上的鉴权由 `migrate-openapi-v1` change 实现。

#### Scenario: 生成 PAT

- **WHEN** `POST /console/users/access-token`，body `{"note":"my-cli","expire":"30d"}`
- **THEN** 写入 `user_access_key` 表（`user_id` + `note` + `access_key` + `expire_time`）；返回 `data.bean.access_key`（明文，仅此一次）

#### Scenario: 列表与删除

- **WHEN** `GET /console/users/access-token`
- **THEN** 列出当前用户所有 PAT（不含明文，仅 `access_key` 前 6 + 后 4 字符 mask）
- **AND** `DELETE /console/users/access-token/{id}` 删除该 PAT

### Requirement: Team 基础 CRUD

kuship-console SHALL 提供 team（=tenant）的核心增删改查端点：`POST /console/teams/init`、`PUT /console/teams/{team_name}`、`DELETE /console/teams/{team_name}`、`POST /console/teams/{team_name}/exit`。Team 的 `tenant_name`、`tenant_id`、`namespace`、`enterprise_id` 字段语义与 rainbond-console 完全一致。

#### Scenario: 创建 team

- **WHEN** `POST /console/teams/init`，body `{"team_name":"alpha","team_alias":"Alpha 团队","useable_regions":"region-1","enterprise_id":"ent-x","namespace":"alpha"}`
- **THEN** 在 `tenant_info` 表插入新行，当前用户作为 owner（`creator` 字段写入 `user_id`），并在 `team_region` 表写入团队-集群绑定记录

#### Scenario: 改 team 名

- **WHEN** owner 用户 `PUT /console/teams/alpha`，body `{"team_alias":"Alpha-V2"}`
- **THEN** `tenant_info.tenant_alias` 更新为 `"Alpha-V2"`

#### Scenario: 普通成员无权修改 team

- **WHEN** 非 owner 用户 `PUT /console/teams/alpha`
- **THEN** 响应 `code=403`、`msg_show="无该团队管理权限"`

#### Scenario: 退出 team

- **WHEN** 普通成员 `POST /console/teams/alpha/exit`
- **THEN** 从 `user_role` 删除该用户在该 team 的所有角色绑定；如该用户是该 team 唯一 owner 则拒绝退出（`code=400`、`msg_show="团队仅剩一位 owner，无法退出"`）

### Requirement: Team 成员管理

kuship-console SHALL 提供 `/console/teams/{team_name}/users`、`/console/teams/{team_name}/notjoinusers`、`/console/teams/{team_name}/users/batch/delete`、`/console/teams/{team_name}/pemtransfer` 四个端点。

#### Scenario: 列出成员

- **WHEN** `GET /console/teams/alpha/users?page=1&page_size=10`
- **THEN** 响应 `data.list` 为该 team 所有成员，每项含 `user_id`、`nick_name`、`email`、`role_infos[]`（该成员在 team 中的所有 role）；`data.bean.total` 为总数

#### Scenario: 添加成员

- **WHEN** 管理员 `POST /console/teams/alpha/users`，body `{"user_ids":[10,11],"role_ids":[1]}`
- **THEN** 在 `user_role` 表为每个 user_id × role_id 写入关联记录

#### Scenario: 批量删除成员

- **WHEN** 管理员 `DELETE /console/teams/alpha/users/batch/delete`，body `{"user_ids":[10,11]}`
- **THEN** 删除 `user_role` 中这些用户在该 team 的所有角色绑定

#### Scenario: 转让 owner

- **WHEN** 当前 owner `POST /console/teams/alpha/pemtransfer`，body `{"user_id":11}`
- **THEN** `tenant_info.creator` 改为 11；原 owner 仍保留普通成员身份

### Requirement: Team 角色与权限管理

kuship-console SHALL 提供 team 角色 CRUD 与 role-perm / user-role 关联管理：

- `GET/POST /console/teams/{team_name}/roles`
- `GET/PUT/DELETE /console/teams/{team_name}/roles/{role_id}`
- `GET /console/teams/{team_name}/roles/perms`（所有 role 的 perms 矩阵）
- `GET/PUT /console/teams/{team_name}/roles/{role_id}/perms`
- `GET /console/teams/{team_name}/users/roles`（user-role 矩阵）
- `PUT/DELETE /console/teams/{team_name}/users/{user_id}/roles`

#### Scenario: 创建自定义角色

- **WHEN** 管理员 `POST /console/teams/alpha/roles`，body `{"name":"developer","perm_codes":["app_overview","app_create"]}`
- **THEN** 在 `role_info` 写入新角色，并在 `role_perms` 写入对应权限码关联

#### Scenario: 修改角色权限

- **WHEN** 管理员 `PUT /console/teams/alpha/roles/5/perms`，body `{"perm_codes":["app_overview"]}`
- **THEN** 删除该 role 在 `role_perms` 的所有旧关联，写入新关联；同时 evict 缓存 key=`tenant:alpha:role:5`

#### Scenario: 修改用户角色后权限缓存失效

- **WHEN** 管理员 `PUT /console/teams/alpha/users/10/roles`，body `{"role_ids":[5,6]}`
- **THEN** 删除该用户在该 team 的所有 user_role 行，写入新行
- **AND** evict `user-team-perms` 缓存 key=`10:alpha`，下次该用户调任意 team-scoped 端点会重查

### Requirement: 权限码注解（@RequirePerm）与 AOP 拦截

kuship-console SHALL 提供 `@RequirePerm("perm_code")` 与 `@RequireEnterpriseAdmin` 两个方法级注解 + 对应 Spring AOP `@Aspect` 切面，自动从 `RequestContext.userId` + `RequestContext.tenantName` 查权限。

#### Scenario: 拥有权限的用户通过

- **WHEN** controller 方法标注 `@RequirePerm("app_create")`
- **AND** 调用方在 path `/teams/alpha/...`，且该用户在 team alpha 拥有 `app_create` 权限
- **THEN** 切面校验通过，方法正常执行

#### Scenario: 缺少权限触发 403

- **WHEN** 同一方法被无权限的用户调用
- **THEN** 切面抛 `ServiceHandleException(403, "no permission", "您无操作此功能的权限")`，由 `GlobalExceptionHandler` 包装为 `general_message` 响应

#### Scenario: enterprise admin 注解

- **WHEN** controller 方法标注 `@RequireEnterpriseAdmin`
- **AND** `RequestContext.enterpriseId` 与 `enterprise_user_perm` 表中该用户的 `identity='admin'` 行匹配
- **THEN** 校验通过

#### Scenario: 缓存读权限矩阵

- **WHEN** 同一用户 + 同一 team 在 60 秒内连续调多个 `@RequirePerm` 方法
- **THEN** 仅第一次查询数据库，后续从 Spring Cache `user-team-perms` 直接读

### Requirement: Enterprise 基本信息端点（含未授权可访问）

kuship-console SHALL 提供 `/console/enterprise/info`（公开，登录页用）与 `/console/enterprises`、`/console/enterprise/{enterprise_id}`、`/console/enterprise/{enterprise_id}/teams`、`/console/enterprise/{enterprise_id}/myteams` 等端点。

`/console/enterprise/info` SHALL 加入 SecurityConfig 的 permitAll 白名单。

#### Scenario: 未登录访问 enterprise/info

- **WHEN** 客户端不带 Authorization 调 `GET /console/enterprise/info`
- **THEN** 响应 `code=200`、`data.bean` 含 `enterprise_id`、`enterprise_alias`、`logo`、`is_active`（仅平台默认 enterprise 的脱敏信息，不含 token / 用户列表 / 权限）

#### Scenario: 当前用户的所有 enterprise

- **WHEN** 已登录用户调 `GET /console/enterprises`
- **THEN** 响应 `data.list` 为该用户所属所有 enterprise（通过 `enterprise_user_perm` 关联）

#### Scenario: enterprise 内 team 列表

- **WHEN** 已登录用户调 `GET /console/enterprise/ent-x/teams?page=1&page_size=10`
- **THEN** 响应 `data.list` 为该 enterprise 下所有 team；`data.bean.total` 为总数

### Requirement: Enterprise 用户管理与跨 team 角色

kuship-console SHALL 提供 enterprise 内用户管理与 admin 管理端点：

- `GET/POST /console/enterprise/{enterprise_id}/users`
- `PUT/DELETE /console/enterprise/{enterprise_id}/user/{user_id}`
- `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`
- `GET/POST/DELETE /console/enterprise/{enterprise_id}/admin/user[/{user_id}]`
- `GET /console/enterprise/{enterprise_id}/admin/roles`
- `GET/PUT /console/enterprise/{enterprise_id}/users/{user_id}/teams/{tenant_name}/roles`
- `POST /console/enterprise/admin/add-user`
- `POST /console/enterprise/admin/join-team`

#### Scenario: 企业管理员创建用户

- **WHEN** enterprise admin `POST /console/enterprise/ent-x/users`，body `{"user_name":"bob","email":"bob@example.com","password":"abcd1234"}`
- **THEN** 写入 `user_info` 表，`enterprise_id="ent-x"`；密码经 `LegacyPasswordEncoder.encode` 写入

#### Scenario: 普通用户无权创建

- **WHEN** 非 admin 用户调用同端点
- **THEN** 响应 `code=403`、`msg_show="您无操作此功能的权限"`

#### Scenario: 跨 team 修改用户角色

- **WHEN** enterprise admin `PUT /console/enterprise/ent-x/users/10/teams/alpha/roles`，body `{"role_ids":[5]}`
- **THEN** 重写该用户在该 team 的角色绑定（即使调用方不是该 team 成员，凭 enterprise admin 身份也可操作）

### Requirement: 权限元数据端点

kuship-console SHALL 提供 `GET /console/perms`（公开，前端权限树渲染用）与 `POST /console/init/perms`（启动时确保 `perm_info` / `role_info` 默认数据存在）。

`GET /console/perms` SHALL 加入 SecurityConfig 的 permitAll 白名单。

#### Scenario: 列出所有权限码

- **WHEN** `GET /console/perms`
- **THEN** 响应 `data.bean` 形如 rainbond-console 输出的嵌套结构 `{"enterprise":{"admin":{"perms":[...]}},"team":{"owner":{"perms":[...]},...},"app":{...},...}`，至少含 170+ 权限码

#### Scenario: 权限码与 rainbond 一致

- **WHEN** 启动时 `PermsInitService` 执行
- **THEN** `perm_info` 表中所有 rainbond `console.utils.perms.py` 中定义的权限码均存在（按 `code` 主键 upsert）

### Requirement: 12 张账户/团队/权限表的 JPA Entity

kuship-console SHALL 为以下 12 张表提供 `@Entity` + `Repository`，与 `console` 数据库（rainbond-console 拥有 schema 演进权）共享：`user_info`、`tenant_info`、`tenant_enterprise`、`enterprise_user_perm`、`console_sys_perm_group`、`console_sys_perms_info`、`role_info`、`role_perms`、`user_role`、`tenant_user_perm`、`user_access_key`、`team_region`。

`hibernate.ddl-auto` 保持 `validate`；启动时 schema 不一致 SHALL 立即抛错并阻止启动。

#### Scenario: 启动时 schema 校验

- **WHEN** 启动时 `tenant_info` 表少了 rainbond 必备字段（如 `enterprise_id`）
- **THEN** Hibernate 抛 `SchemaManagementException`，应用启动失败

#### Scenario: 不发任何 DDL

- **WHEN** kuship-console 启动连上 console DB
- **THEN** 整个启动期间数据库 binlog 没有 `CREATE TABLE` / `ALTER TABLE` 语句

## MODIFIED Requirements

### Requirement: JWT 认证（兼容 djangorestframework-jwt 1.11.0）

kuship-console SHALL 实现 `JwtAuthenticationFilter`，能解析由 rainbond-console（Django + djangorestframework-jwt 1.11.0）签发的 token；支持 Authorization header 形如 `GRJWT <token>` 与 `jwt <token>` 两种前缀（不区分大小写）；使用 HS256 算法 + 与 Django 同源的 `SECRET_KEY`；解析 payload 时直接读取 Django 风格字段名（`user_id`、`username` 或 `nick_name`、`email`、`exp`、`orig_iat`），不做名字转换；过期校验启用，可配 `leeway-seconds`（默认 0）；解析成功后 SHALL 通过 `userRepository.findById(payload.user_id)` 真实加载 user 写入 Spring `SecurityContext` 与 `RequestContext`，user 不存在时 SHALL 拒绝请求（`code=401`、`msg_show="未认证或 token 失效"`、`msg="user not found"`）。

#### Scenario: GRJWT 前缀解析合法 token

- **WHEN** 客户端发送 `GET /console/anything` 携带 `Authorization: GRJWT <valid-token>`
- **THEN** Filter 解析成功，从 user_info 表加载该用户的完整记录，把 `userId`、`username`、`email`、`enterpriseId`、`isSysAdmin` 注入 `RequestContext`，请求继续向下分发

#### Scenario: jwt 前缀（小写）也兼容

- **WHEN** 客户端发送 `Authorization: jwt <valid-token>`（外部 portal 风格）
- **THEN** Filter 同样解析成功

#### Scenario: 缺失 Authorization header 触发 401

- **WHEN** 客户端访问需要认证的路径但未带 `Authorization` 头
- **THEN** 响应 HTTP 401、响应体 `{"code":401,"msg":"...","msg_show":"未认证或 token 失效","data":{"bean":{},"list":[]}}`

#### Scenario: 过期 token

- **WHEN** 客户端携带的 token 的 `exp` claim 已过去
- **THEN** 响应 HTTP 401，响应体 `code=401`、`msg_show="未认证或 token 失效"`
- **AND** 任何 profile 下 `msg` 字段都包含具体原因（如 `token expired`、`invalid signature`、`missing token`），用于联调与运维排查；`msg_show` 始终保持统一文案，不向最终用户泄露细节

#### Scenario: 篡改的 token

- **WHEN** 客户端携带的 token 签名校验失败
- **THEN** 响应 HTTP 401

#### Scenario: SECRET_KEY 通过环境变量注入

- **WHEN** 启动应用时未提供 `JWT_SECRET_KEY` 环境变量且 profile 不是 local
- **THEN** 应用启动失败，给出明确错误：`JWT_SECRET_KEY must be set in non-local profiles`

#### Scenario: token 中的 user_id 在数据库中已被删

- **WHEN** 客户端持有合法签名但 `user_id` 对应的用户已从 `user_info` 表删除
- **THEN** 响应 HTTP 401，`msg="user not found"`、`msg_show="未认证或 token 失效"`

### Requirement: 请求上下文（RequestContext）

kuship-console SHALL 提供 `RequestContext`（`@RequestScope` Spring bean），暴露当前请求的 `user_id`、`username`、`team_name`、`region_name`、`enterprise_id`、`is_sys_admin` 六个字段；`JwtAuthenticationFilter` 在认证成功后通过 `userRepository.findById` 加载真实用户，写入 `userId` / `username` / `email` / `enterpriseId` / `isSysAdmin` 字段；`TenantContextInterceptor` 在 controller 执行前从 path variable 提取 `{team_name}` / `{region_name}` 写入；业务层通过 Spring 注入直接获取，不得再手动从 `HttpServletRequest` / `Authentication` 解析。

#### Scenario: path 中的 team_name 被自动注入

- **WHEN** 客户端访问 `GET /console/teams/myteam/apps`
- **AND** 该路径定义为 `@GetMapping("/console/teams/{team_name}/apps")`
- **THEN** 在 controller 方法内注入的 `RequestContext.getTeamName()` 返回 `"myteam"`

#### Scenario: JWT user_id 写入上下文

- **WHEN** 一个携带合法 token 的请求成功通过认证
- **THEN** `RequestContext.getUserId()` 返回 token payload 中的 `user_id`（数值类型保持原样不做字符串化）
- **AND** `RequestContext.getEnterpriseId()` 返回从 `user_info.enterprise_id` 字段加载的真实值
- **AND** `RequestContext.isSysAdmin()` 返回 `user_info.sys_admin` 字段（boolean）

#### Scenario: 路径变量名严格 snake_case

- **WHEN** 业务 controller 添加新路径
- **THEN** path variable 名必须保留 `team_name`、`region_name`、`service_alias`、`app_id` 等 Django 原始命名
- **AND** RequestContext 字段命名同样使用 snake_case 暴露给响应（如序列化为 JSON 时）

### Requirement: Spring Security 配置（JWT 认证生效）

kuship-console SHALL 配置 Spring Security 启用 `JwtAuthenticationFilter`，requireAll 默认要求 JWT 认证；并保留以下端点 permitAll 白名单：

1. `/console/healthz`、`/actuator/**`（运维探针）
2. `POST /console/users/login`、`POST /console/users/register`（登录注册）
3. `GET /console/enterprise/info`（登录页平台信息）
4. `GET /console/perms`（权限元数据公开供前端权限树渲染）
5. `POST /console/init/perms`（仅 `kuship.security.allow-public-init=true` 时；默认 false）

未授权访问受保护路径 SHALL 返回 HTTP 401 + `general_message` 形状；CSRF 关闭（与 rainbond-console 一致）；session 关闭（stateless）。

#### Scenario: 未登录访问 healthz

- **WHEN** 客户端不带 Authorization 调 `GET /console/healthz`
- **THEN** 响应 HTTP 200，正常返回健康信息

#### Scenario: 未登录访问 enterprise/info

- **WHEN** 客户端不带 Authorization 调 `GET /console/enterprise/info`
- **THEN** 响应 HTTP 200，返回脱敏的 enterprise 基本信息

#### Scenario: 未登录访问 users/details

- **WHEN** 客户端不带 Authorization 调 `GET /console/users/details`
- **THEN** 响应 HTTP 401，`msg_show="未认证或 token 失效"`

#### Scenario: allow-public-init 默认关闭

- **WHEN** `kuship.security.allow-public-init` 未设或 `false`
- **AND** 客户端不带 Authorization 调 `POST /console/init/perms`
- **THEN** 响应 HTTP 401
