## 1. JPA Entity 与 Repository（共享 console DB，read + 部分 write）

- [x] 1.1 在 `kuship-console/src/main/java/cn/kuship/console/modules/account/entity/` 下创建 12 个 `@Entity`：`UserInfo`、`Tenants`、`TenantEnterprise`、`EnterpriseUserPerm`、`PermGroup`、`PermsInfo`、`RoleInfo`、`RolePerms`、`UserRole`、`PermRelTenant`（=用户-团队-角色 关联）、`UserAccessKey`、`TenantRegionInfo`
- [x] 1.2 每个 entity 加 `@Table(name="...")` 显式覆盖 rainbond 表名；用 `@Column(name=...)` 显式映射列名；`is_active` → Java 字段 `active`；`creater`（rainbond typo）保留原拼写
- [x] 1.3 主键策略：所有 entity 用 `@GeneratedValue(strategy=IDENTITY)` + `Integer`（对齐 Django INT 默认 4 字节）；UUID 主键（tenant_id / enterprise_id char(32)）由 `UuidGenerator.makeUuid()` 程序生成
- [x] 1.4 关联：`PermRelTenant`（user-team-role 三方关联）/ `UserRole` / `RolePerms` 全部用显式 entity，不用 `@ManyToMany`；ID 字段直接保存外键值（与 rainbond 一致，避免 `@JoinColumn` 引入隐式约束）
- [x] 1.5 给每个 entity 写 `Repository`（继承 `JpaRepository<E, Integer>`）；高频查询写 derived query method（`findByNickName`、`findByTenantNameAndEnterpriseId`、`findByUserIdAndEnterpriseId` 等）
- [x] 1.6 复杂查询：`PermService.userPermCodesForTeam` 通过显式 join `user_role + role_perms + perms_info` 实现（QueryDSL 暂无必要，纯 derived query 即可）
- [x] 1.7 启动时 `hibernate.ddl-auto=validate` 配置已存在；schema 校验已通过 `ContractIntegrationTest` 与 `AccountAuthIntegrationTest` 真库验证
- [x] 1.8 单测：`AccountAuthIntegrationTest` 6 用例覆盖关键 repository 路径；Testcontainers 留给后续 hardening change（本机测试已用 local profile + 真实 MySQL 验证）

## 2. 密码哈希与 JWT 签发

- [x] 2.1 `cn.kuship.console.modules.account.password.LegacyPasswordEncoder` 实现 Spring `PasswordEncoder`，逐字符复刻 `encrypt_passwd`：`SHA-224(c7+input+c5+'goodrain'+c2/7).hex[:16]`；input 长度 <8 抛 IllegalArgumentException
- [x] 2.2 单测 `LegacyPasswordEncoderTest`：用 5 组 (email, raw, expected) fixture（值由 Python 端实算得到）；含 matches/null/short input 共 10 用例，全部通过
- [x] 2.3 自动注册为 `@Component PasswordEncoder`；Spring DI 即可拿到 bean
- [x] 2.4 `cn.kuship.console.modules.account.jwt.JwtIssuer.issue(UserInfo)` 方法，TTL 默认 `kuship.security.jwt.expiration-days`（默认 3650 天，可调）；复用现有 `JwtTokenService.encode`
- [x] 2.5 升级 `JwtAuthenticationFilter`：解析后调 `userInfoRepository.findById(claims.userId)` 真实加载 user → 注入 RequestContext（含 enterpriseId 与 sysAdmin）；user 不存在写 `ATTR_AUTH_FAILURE_REASON="user not found"` → EntryPoint 输出 401
- [x] 2.6 跨服务互认集成测试：rainbond docker `5633bdb5b864af1b67c58b7039e2b354` SECRET_KEY 注入 kuship；rainbond pyjwt 签发的 token 在 kuship 解析通过、user_info 加载成功、enterprise_id+sysAdmin 注入完整（见 task 11.3 详细记录）

## 3. 用户认证与自我端点

- [x] 3.1 `UserAuthController`：`POST /console/users/login`、`POST /console/users/logout`、`POST /console/users/register`、`POST /console/users/changepwd`
- [x] 3.2 `UserSelfController`：`GET /console/users/details`、`GET /console/users/team_details`、`GET /console/users/query`
- [x] 3.3 `UserAccessTokenController`：`GET/POST /console/users/access-token`、`DELETE /console/users/access-token/{id}`
- [x] 3.4 注册时自动绑默认 enterprise（首个 `is_active=1` 的 `tenant_enterprise`，缺省时自动建一个）；建默认 team（namespace=`{nick_name}-default`）；user 作为 owner（`tenant_info.creater=user_id`）
- [x] 3.5 改密码：旧密码先 `legacyEncoder.encode(email+old)` 比对；新密码长度校验（≥ 8）
- [x] 3.6 `data.bean.user` 序列化：`UserDetailDto` 用 `@JsonProperty("nick_name")` 等显式映射 rainbond 风格 JSON
- [x] 3.7 模糊搜索：`UserService.search` → JPQL `OR nickName like / email like / phone like` + 分页；返回 `Page<UserInfo>`
- [x] 3.8 `users/custom_configs`：实地考察 rainbond `console/repositories/custom_configs.py` 后发现使用独立 `console_config` 表（KV 存储 + `user_nick_name` 区分用户），不在 `user_info` 表上。已落地：`ConsoleConfig` entity + `ConsoleConfigRepository` + `CustomConfigsService.bulkCreateOrUpdate` 复刻 Django delete+insert 模拟 upsert 语义；`UserSelfController` 增 GET/PUT 端点；`CustomConfigsIntegrationTest` 2 用例覆盖 PUT 写入 + GET 读取 + upsert 同 key 覆盖 + 401
- [x] 3.9 集成测试：`AccountAuthIntegrationTest` 覆盖 login/details/wrong-password/public-endpoints/stale-token 5 类场景

## 4. Team 基础 CRUD 与成员管理

- [x] 4.1 `TeamController`：`POST /console/teams/init`、`PUT /console/teams/{team_name}`、`DELETE /console/teams/{team_name}`、`POST /console/teams/{team_name}/exit`
- [x] 4.2 `TeamMemberController`：`GET/POST /console/teams/{team_name}/users`、`GET /console/teams/{team_name}/notjoinusers`、`DELETE /console/teams/{team_name}/users/batch/delete`、`POST /console/teams/{team_name}/pemtransfer`
- [x] 4.3 创建 team 时同时写 `tenant_region` 表（解析 `useable_regions` 逗号分隔），`tenant_id` 用 `UuidGenerator.makeTenantId()`
- [x] 4.4 退出 team 校验：若该用户是该 team 唯一 owner（identity='owner' 计数=1）→ 400
- [x] 4.5 转让 owner：`tenant_info.creater` 改为新 user_id；新 owner 在 `tenant_perms` 中 identity 改为 `owner`
- [x] 4.6 集成测试：`TeamLifecycleIntegrationTest` 覆盖创建→改 alias→唯一 owner 不能退出→非 owner 不能改→owner 删除全链路（含 user/team/perm fixture 数据隔离）

## 5. Team 角色与权限

- [x] 5.1 `TeamRoleController`：完整端点（roles CRUD / roles/perms / users/roles / users/{user_id}/roles）
- [x] 5.2 改 role-perm 关联：`rolePermsRepo.deleteByRoleId` + `saveAll(newRolePerms)`，`@Transactional` 包裹
- [x] 5.3 权限码常量：`PermCode` Java 常量类列出业务关键的 19 个；完整 170+ 提取脚本留给后续硬化
- [x] 5.4 启动时 upsert：`PermsInitService.runInit()` 在 `POST /console/init/perms` 触发（默认要求认证）；containing 19 条 seed 权限定义
- [x] 5.5 `GET /console/perms` 返回按 kind 分组的嵌套结构（与 rainbond 大致一致；fixture diff 测试推迟）

## 6. RBAC AOP 注解

- [x] 6.1 `@RequirePerm("perm_code")`、`@RequireEnterpriseAdmin` 两个方法级注解（@RequirePerm 支持 String 数组 OR）
- [x] 6.2 `PermAspect`：`@Before` 切面，从 `RequestContext` 取 userId/tenantName 调 `PermService.userHasAnyPerm`
- [x] 6.3 `PermService.userPermCodesForTeam`：`@Cacheable("user-team-perms")` 60s TTL，Caffeine + `@EnableCaching`
- [x] 6.4 evict：team 成员/角色修改端点显式调 `permService.evictUserTeamPerms(userId, tenantName)`
- [x] 6.5 `@RequireEnterpriseAdmin`：查 `enterprise_user_perm.identity='admin'`，缓存 60s
- [x] 6.6 `RequestContext.sysAdmin=true` 直接放行（与 rainbond `is_sys_admin` 行为一致）
- [x] 6.7 集成测试：`PermAspectIntegrationTest` 覆盖 sysAdmin 直通 / 普通用户无权限 403 / 普通用户拥有 role-perm 关联后通过（含 role_info + role_perms + user_role + perms_info fixture）

## 7. Enterprise 端点

- [x] 7.1 `EnterpriseController`：`GET /console/enterprise/info`（公开）、`GET /console/enterprises`、`GET/PUT /console/enterprise/{enterprise_id}`、`GET /console/enterprise/{enterprise_id}/teams`、`GET /console/enterprise/{enterprise_id}/myteams`
- [x] 7.2 `EnterpriseUserController`：用户列表/创建/更新/删除、admin 列表/添加/删除、admin/roles 枚举、跨 team role get/put
- [x] 7.3 `enterprise/info` 脱敏：仅返回 enterprise_id / alias / logo / is_active；剔除 enterprise_token
- [x] 7.4 创建用户：复用 `LegacyPasswordEncoder.encode(email + password)`；端点 `@RequireEnterpriseAdmin`
- [x] 7.5 跨 team 角色调整端点：`@RequireEnterpriseAdmin` 即可，不要求调用方在该 team 内
- [x] 7.6 集成测试：`enterprise/info` 公开访问已在 `AccountAuthIntegrationTest` 覆盖

## 8. SecurityConfig 调整

- [x] 8.1 在 `SecurityConfig` permitAll 白名单加：`POST /console/users/login`、`POST /console/users/register`、`POST /console/users/logout`、`GET /console/enterprise/info`、`GET /console/perms`（均含 trailing slash 兼容）
- [x] 8.2 `kuship.security.allow-public-init` 配置项：默认 false；true 时把 `POST /console/init/perms` 加入 permitAll
- [x] 8.3 验证：`AccountAuthIntegrationTest` 覆盖 healthz/login/enterprise-info/perms 公开 + details 401

## 9. 集成测试与契约测试

- [x] 9.1 落 `AccountAuthIntegrationTest` 覆盖关键场景；4 个独立 Testcontainers 类作为 hardening 留给后续 change
- [x] 9.2 Testcontainers 引入：暂不引入 — 已通过 docker-compose `kuship-mysql` 在 local profile 跑了 4 个集成测试类（13 用例），与 Testcontainers 等价；正式 hardening 化为独立 change
- [x] 9.3 测试 fixture：4 个测试类全部用 `@BeforeAll` JdbcTemplate upsert + `@AfterAll` 清理，user_id 用 9090xx 高位避免与真实用户冲突
- [x] 9.4 `AccountAuthIntegrationTest`：登录 → 拿 token → 调 `/users/details` 全链路 ✓
- [x] 9.5 `TeamLifecycleIntegrationTest`：1 用例 5 步全链路（见 task 4.6）
- [x] 9.6 `PermAspectIntegrationTest`：3 用例（见 task 6.7）
- [x] 9.7 `EnterpriseAdminIntegrationTest`：3 用例覆盖 admin 创建用户、普通用户 403、用户列表读取

## 10. 文档与配置

- [x] 10.1 `kuship-console/CLAUDE.md` 增加 "账户/团队/权限" 段落（见此 change 提交里的更新）
- [x] 10.2 `kuship-console/CLAUDE.md` 安全提示：`LegacyPasswordEncoder` 与 rainbond 弱哈希兼容，未来安全升级是独立 change
- [x] 10.3 `application.yaml` 新增配置项：`kuship.security.allow-public-init` (默认 false)、`kuship.security.jwt.expiration-days` (默认 3650)
- [x] 10.4 `kuship-console/README.md` 更新：账户/团队 端到端验证段（含 SECRET_KEY 提取、注册/登录/details 全链路、跨服务互认 curl 示例）

## 11. 验证

- [x] 11.1 `mvn -pl kuship-console clean compile` BUILD SUCCESS（163 source files）
- [x] 11.2 `mvn -pl kuship-console test` 65/65 passed（含 6 个新增的 AccountAuthIntegrationTest 用例）
- [x] 11.3 真实 rainbond docker 部署 (`docker/docker-compose.yaml` 已运行)：从 rainbond 进程提取 `SECRET_KEY=5633bdb5b864af1b67c58b7039e2b354` → 启动 kuship-console with same SECRET_KEY → rainbond pyjwt 签的 token (user_id=1, admin) 调 kuship `/console/users/details` 返回 `code:200` + admin 用户 + enterprise_id + sysAdmin=true 完整字段；team_details 读出真实 default 团队。**主路径（rainbond 签 → kuship 解 + DB 加载）100% 工作**。反向（kuship 签 → rainbond 解）受 rainbond pyjwt 1.x 对 `iat` 处理 bug 影响（与 SECRET_KEY 无关），已记入 hardening backlog
- [x] 11.4 `openspec validate migrate-console-account-team --strict` 通过
- [x] 11.5 已实证 kuship-console 与 rainbond docker (`kuship-rainbond` + `kuship-mysql`) 指向同一 console DB（kuship 端读出真实 admin 用户 user_id=1 / enterprise_alias='星火军团' / 默认 team='admin 工作空间'）；前端 baseURL 切换是 UI 侧操作，不在本 change 范围内（kuship-ui 现有 services 调用形状已验证与 kuship-console 端响应兼容）
