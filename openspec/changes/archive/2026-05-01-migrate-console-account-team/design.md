## Context

- 截至本 change，kuship-console 已具备：响应/异常/JWT/分页 契约层（`migrate-console-response-contract`）+ region API 客户端基础设施（`migrate-console-region-client`）。但 `JwtAuthFilter` 解析出的 `RequestContext.user` 只有 `userId`、`username`、`email` 三个 stub 字段，没有真正去 user_info 表读用户。
- rainbond-console 的账户体系约束：
  - JWT：HS256 + `GRJWT` 前缀 + payload 使用 `user_id`、`username`(=`nick_name`)、`email`、`exp`、`orig_iat`，**SECRET_KEY 与 Django settings.SECRET_KEY 同源**（10 年过期 ≈ 永久）
  - 密码：自定义 `encrypt_passwd(email + raw)` = `SHA-224(c7+input+c5+'goodrain'+c2/7)[:16]`，**16 char hex 弱哈希**，但要求严格复刻
  - 多对多权限：通过 `tenant_perms` / `enterprise_user_perms` / `role_perms` / `user_role` 等关联表，权限码集中在 `console/utils/perms.py`（170+ 条）
  - "team" = "tenant"：rainbond 的核心数据模型把这两个概念等同，DB 表 `tenant_info`
- kuship-ui 已在 `services/user.js` / `services/team.js` 内有完整调用链，登录页直接 `POST /console/users/login` 拿 token。

## Goals / Non-Goals

**Goals:**

1. 用户能用 rainbond-console 既有用户名/密码，通过 kuship-console 登录拿 JWT，再带 JWT 调任意 `/console/*` 端点
2. 完整 RBAC：用户 → 角色 → 权限码 三层映射，controller 上加 `@RequirePerm` 注解即可拦截
3. team / enterprise / member / role / perm 五大对象的核心增删改查
4. **跨服务互认**：rainbond-console 签发的 token 可直接用于 kuship-console（共用 SECRET_KEY），反之亦然
5. JPA entity 全部按"共享只读 schema"方式落，不发任何 DDL

**Non-Goals:**

1. OAuth（独立 change `migrate-console-oauth`）—— 范围 767+ LOC + 多 provider，独立性强
2. 邮件/短信注册重置（依赖外部网关，留给 `migrate-console-misc`）
3. 邀请链接 / 备份恢复 / 操作日志 / 企业激活 license（各自留给对应 change）
4. team_overview / team_resources（涉及大量 region 资源聚合，留给 `migrate-console-application-core`）
5. 不切换密码哈希算法到 bcrypt（兼容性优先）
6. 不重新设计权限元数据 schema（沿用 rainbond `console.utils.perms` 的 170+ 权限码）

## Decisions

### 决策 1：密码哈希——直接复刻 rainbond 自定义算法（不上 bcrypt）

**问题**：rainbond `encrypt_passwd` 是带固定 salt 的 SHA-224 截断 16 字符算法，弱于 bcrypt。引入 bcrypt 能提升强度，但 rainbond-console 写入的 user 在 kuship 不可读、kuship 写入的在 rainbond 不可读，跨服务登录直接断。

**选择**：
- 在 `cn.kuship.console.account.password.LegacyPasswordEncoder`（实现 `org.springframework.security.crypto.password.PasswordEncoder`）中，逐字符复刻 Python 算法：
  ```java
  String input = email + rawPassword;
  String word = (int) input.charAt(7) + input + (int) input.charAt(5) + "goodrain" + ((int) input.charAt(2) / 7);
  byte[] hash = MessageDigest.getInstance("SHA-224").digest(word.getBytes(UTF_8));
  return HexFormat.of().formatHex(hash).substring(0, 16);
  ```
- `matches(raw, encoded)` = `encode(raw).equals(encoded)`
- 注意：`input.charAt(7)` 要求 input 长度 ≥ 8。rainbond 强制密码 ≥ 8 字符 + email 前缀，这个前提可保证。`encode()` 收到非法输入抛 `IllegalArgumentException`（在校验层先挡）

**Rationale**：
- 跨服务一致性 > 哈希强度（rainbond-console 整体已运行多年，未来切 bcrypt 是另一次独立的安全升级 change）
- Spring Security 的 `DelegatingPasswordEncoder` 模式留口子：将来可以在 prefix 加 `{bcrypt}` / `{rainbond}` 两种策略平滑过渡

**Alternatives considered**：
- ❌ 双写（同时存 rainbond hash + bcrypt hash 两列）—— 改 schema 导致 rainbond-console 不识别，violates "schema 演进权归 rainbond" 原则
- ❌ 单向：kuship 写 bcrypt，rainbond 仍读 rainbond hash —— 用户在 kuship 改密码后 rainbond 登录失败

### 决策 2：JWT 签发与跨服务互认

**SECRET_KEY 来源**：从 `application.yaml` `kuship.security.jwt.secret-key` 读，**部署时与 rainbond-console `SECRET_KEY` 配同一个值**（已在 `migrate-console-response-contract` 落地的环境变量同源策略）。

**签发逻辑**（`JwtIssuer`）：
```java
Map<String, Object> claims = new LinkedHashMap<>();
claims.put("user_id", user.getUserId());          // Integer
claims.put("username", user.getNickName());        // = nick_name
claims.put("email", user.getEmail());
long now = Instant.now().getEpochSecond();
claims.put("exp", now + 3650 * 86400L);            // 10 年（与 rainbond 一致）
claims.put("orig_iat", now);
return Jwts.builder().claims(claims).signWith(hmacKey).compact();
```

**Header 格式**：保持已实现的 `GRJWT` / `jwt`（大小写不敏感）双 prefix；新签的 token 默认用 `GRJWT` 输出（rainbond 历史习惯），response 体里塞 `data.bean.token`。

**`JwtAuthFilter` 升级**：之前从 payload 直接构造 stub user；本 change 改为 `userRepository.findById(payload.user_id)` 真正加载 → 写入 `RequestContext`。如果 user 不存在（删了 / 错 token）→ 401 + `general_message(error="invalid token")`。

**Rationale**：完全沿用 rainbond payload 字段名 + SECRET_KEY 同源 → 双向 token 互认零成本，前端无感。

### 决策 3：JPA Entity 数量与映射策略

**12 张表纳入 JPA 管理**（共享 console DB，hibernate.ddl-auto=validate）：

| Entity | 表名 | 用途 |
|---|---|---|
| `UserInfo` | `user_info` | 用户主表 |
| `Tenants` | `tenant_info` | team / tenant（rainbond 等同概念）|
| `TenantEnterprise` | `tenant_enterprise` | enterprise 主表 |
| `EnterpriseUserPerm` | `enterprise_user_perm` | 企业级用户角色（admin 等）|
| `PermGroup` | `console_sys_perm_group` | 权限分组元数据 |
| `PermsInfo` | `console_sys_perms_info` | 权限码元数据 |
| `RoleInfo` | `role_info` | 团队角色 |
| `RolePerms` | `role_perms` | 角色-权限关联 |
| `UserRole` | `user_role` | 用户-角色关联 |
| `TenantUserPerm` | `tenant_user_perm` | 团队级用户特殊权限（个别用户单独授权用）|
| `UserAccessKey` | `user_access_key` | PAT |
| `Migrations` | `console_sys_user_favorite` | 用户收藏（仅基础字段，本 change 只读）|

**字段命名**：通过 `PhysicalNamingStrategyStandardImpl`（已配置）自动 camelCase ↔ snake_case；遇 rainbond 表名不规则的（如 `console_sys_*`）用 `@Table(name="...")` 显式覆盖。

**关联**：尽量用 `@ManyToOne(fetch=LAZY)` + `@JoinColumn`；避免 `@OneToMany` 导致的 N+1 查询风险。多对多关系（user-role / role-perm）用关联表 entity 显式建模（不用 `@ManyToMany`），方便 QueryDSL 拼接复杂查询。

**Rationale**：rainbond schema 早期由 Django ORM 生成，含较多历史包袱（`console_sys_*` 前缀混乱、字段命名混用）。`@Table(name=...)` 显式覆盖 + 不依赖 `@JoinTable` 自动推断 → 可控性强。

### 决策 4：权限模型——双层（enterprise admin + team RBAC）

rainbond 的权限模型有两个独立维度：

```
┌─────────────────────────────────┐
│  Enterprise Level (企业管理员)   │
│  enterprise_user_perm 表        │
│  identity: admin / common       │
│  → sys_admin / 平台后台         │
└─────────────────────────────────┘
              ↑
              │
┌─────────────────────────────────┐
│  Team / Tenant Level (团队 RBAC)│
│  user_role + role_perms         │
│  permissions[] in JWT path      │
│  → /teams/{name}/* 鉴权用       │
└─────────────────────────────────┘
```

**实现**：
- `@RequirePerm("team_member_perms")` 注解 + Spring AOP `@Aspect`：
  - 从 `RequestContext.tenantName`（路径变量 `team_name` 提取）+ `RequestContext.userId` → 查 `user_role` + `role_perms` → 拿到该用户在该 team 下的所有权限码 → 校验是否包含注解值
  - 缓存：`@Cacheable("user-team-perms")` key=`userId+tenantName`，TTL 60s（rainbond 每次 view init 都重查，相当于无缓存；我们加轻缓存）
- `@RequireEnterpriseAdmin` 注解：从 `enterprise_user_perm` 查 `identity = 'admin'`
- 权限码常量：用 `RequiredPerm` 枚举把 rainbond `console.utils.perms.py` 的 170+ 权限码全部翻译成 Java 常量；运行时由 `PermsInitService.init()`（启动时执行一次）确保 `perm_info` 表中每条权限码都存在（`upsert by code`）

**Rationale**：rainbond Django 的 `decorator + base view` 模式不可直接搬到 Spring；用 AOP 注解让控制器代码"看起来像 rainbond"。

### 决策 5：team / tenant 的命名歧义如何处理

rainbond 的核心歧义：DB 表 `tenant_info` 同时承担"团队"和"K8s 命名空间宿主"两重职责。Python 代码里随处可见 `tenant_name` 与 `team_name` 互换使用。

**kuship-console 命名约定**：
- Java 类名一律用 `Tenants` / `TenantsRepository` / `TenantsService`（贴近 DB 表名）
- 业务接口的 path 变量用 `team_name`（保持与 rainbond URL 完全一致）
- 在 `RequestContext` 中暴露 `tenantName`（=team_name），不引入 `teamName` 别名
- DTO 字段尊重 rainbond JSON 输出原状（`team_name` / `team_alias` / `tenant_id`）

**Rationale**：保持 URL 与 JSON 形状 100% 兼容是硬约束；只在 Java 内部统一为 `tenant`。

### 决策 6：邀请、找回密码等"未做"端点的占位策略

本 change punt 的端点（`send_reset_email`、`register-by-phone`、`/users/invite/*` 等）—— 暂不在 controller 中声明，前端调到这些路径会得到 `404 + general_message(error="endpoint not implemented yet")`（由 `GlobalExceptionHandler` 兜底的 `NoHandlerFoundException`，已在 `migrate-console-response-contract` 落地）。

**不暴露占位 stub 接口**，防止前端误以为可用。后续 change 在自己 capability 内补完。

### 决策 7：`@WebMvcTest` 还是 `@SpringBootTest`？

- 单元测试用 `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters=false)` 验证序列化与异常映射（已沿用 contract 阶段经验）
- 集成测试用 `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@Transactional` + Testcontainers MySQL（**首次引入** Testcontainers），针对：
  - 跨 entity 的 user → role → perm 真实查询
  - JWT 签发后再 stub 一次 HTTP 调用，验证 filter 端到端
- 测试 schema 用 Flyway baseline + 手写 `V1__schema.sql`（仅本测试 module 用），脚本含本 change 涉及的 12 张表的最小可用 DDL（不含 rainbond 完整 schema，避免维护负担）

**Rationale**：账户/团队/权限的关联查询非常密集，纯 mock 很容易掩盖 join 错误，必须真库验证。

### 决策 8：未授权可访问的端点白名单

明确 4 类路径**无需 JWT**：
- `POST /console/users/login`、`POST /console/users/register`
- `GET /console/enterprise/info`（登录页用，展示企业 logo / 平台名）
- `GET /console/perms`（权限元数据，登录前权限树渲染）
- `POST /console/init/perms`（仅启动一次，但用户可手动触发；用 `kuship.security.allow-public-init=false` 默认关闭）

`SecurityConfig.requestMatchers(...).permitAll()`，其余 `authenticated()`。

## Risks / Trade-offs

- **[弱密码哈希]** rainbond 的 SHA-224 截断 16 字符 hex 在现代标准下属于弱哈希，且无每用户随机盐 → kuship 沿用直接增加了"全平台密码强度差"的可见度。
  - **Mitigation**：本 change 文档化此选择 + 在 `kuship-console/CLAUDE.md` 标注"安全升级是独立 change"；后续 `migrate-console-security-hardening` 用 `DelegatingPasswordEncoder + bcrypt` 双格式平滑迁移
- **[10 年过期 token]** rainbond 的 token 等同永久 → 一旦泄露危害大。
  - **Mitigation**：本 change 同 rainbond 默认；提供 `kuship.security.jwt.expiration-days` 配置项，部署时可下调（默认仍 3650）
- **[共享 schema 演进风险]** rainbond Django 升级时若改 user_info / tenant_info 表结构，kuship-console JPA 启动 `validate` 会失败。
  - **Mitigation**：`hibernate.ddl-auto=validate` 启动期硬阻断 → 立刻发现；CI 中加 schema 一致性检查（脚本读 rainbond `migrations/*.py` + kuship `@Entity` 字段双向比对）—— 不在本 change 实现，记入 Open Questions
- **[Testcontainers 引入]** 本 change 首次让构建依赖 Docker daemon，本地无 Docker 的开发环境会跑不通集成测试。
  - **Mitigation**：测试用 Maven profile `-Pintegration-tests` 隔离，默认 `mvn test` 跳过；CI 中显式跑该 profile
- **[权限缓存窗口]** 60s TTL 内修改了角色权限的用户仍按旧权限放行。
  - **Mitigation**：`PUT/DELETE /teams/{}/users/{}/roles` 等修改端点显式 `@CacheEvict("user-team-perms", key="#userId+#teamName")`
- **[170+ 权限码翻译人工成本]** Python `console.utils.perms.py` 是 dict 嵌套，逐个翻译枯燥易错。
  - **Mitigation**：写一个一次性脚本（`scripts/extract-perms.py`）解析原文件 → 输出 Java enum 源码；commit 该脚本以备 rainbond 后续新增权限码时复用

## Migration Plan

1. 先按 entity 落（PR 1：12 个 `@Entity` + Repository，含基本单测）
2. 再落密码加密 + JWT 签发（PR 2：`LegacyPasswordEncoder` + `JwtIssuer` + `/users/login` + `/users/details`）
3. 再落 team / member / role / perm（PR 3：team CRUD + member + role + perm AOP）
4. 最后落 enterprise（PR 4：enterprise 信息 + 用户管理 + admin + cross-team role）
5. 全程保持 rainbond-console 不动，部署时两 console 共用 SECRET_KEY 即可平滑共存

无需 DB 迁移；本 change 不发任何 DDL。

## Open Questions

- **schema 一致性 CI 校验**何时落？建议：放到独立 `add-schema-consistency-ci` change（不依赖业务代码，可独立加固）
- **超级管理员（sys_admin）** 是否应触发独立的 IAM 视图？rainbond 把 sys_admin 视作"在所有 enterprise 都是 admin"，但前端没有专门 sys_admin UI；本 change 沿用，sys_admin 用户在 `RequestContext` 暴露 `isSysAdmin` 字段供 controller 自行判断
- **PAT (UserAccessKey) 校验路径**：rainbond 的 `/openapi/v1/*` 用 PAT；本 change 仅落 PAT 的 CRUD（`/users/access-token`），不接管 `/openapi/v1` 的鉴权 filter（留给 `migrate-openapi-v1` change）
- **OAuth identity 反查**：rainbond 在 user_info 表外有 `oauth_user` 关联表；OAuth 登录后会拿 oauth_user_id 反查 user_id。本 change 不实现；OAuth change 会拓展 JwtAuthFilter 支持 oauth user id 注入
