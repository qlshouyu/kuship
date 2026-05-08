# kuship-console — Java/Spring Boot 控制台后端

## 模块定位

kuship-console 是 `reference/rainbond-console`（Python/Django）的 Java 替代实现，逐步迁移其
全部 `/console/*` 与 `/openapi/v1/*` 路由能力，对接 `kuship-ui`（baseURL 保持 `/console/*`）
与 Rainbond Go 集群（通过 Region API HTTP 调用）。

## 技术栈

- Java 21 / Spring Boot 4.0.6 / Maven
- Spring Data JPA + Hibernate 6.x + QueryDSL（已配 apt）
- Spring Security 6（本骨架阶段 permitAll，JWT 由下个 change 接入）
- Flyway（仅 baseline，不放业务表 SQL）
- Spring Boot Actuator
- Lombok（编译期）/ MapStruct（编译期）
- 占位依赖：`io.kubernetes:client-java`（rke2 阶段用）、Apache HttpComponents 5（region client 用）

## 包结构

```
cn.kuship.console
├── KuShipConsoleApplication       Spring Boot 启动类
├── config/                        Spring 配置
│   ├── SecurityConfig             permitAll 占位（下个 change 接 JWT）
│   ├── JpaConfig                  PhysicalNamingStrategyStandardImpl 显式声明
│   └── WebMvcConfig               扩展点占位
├── common/
│   ├── response/
│   │   ├── ApiResult              { code, msg, msg_show, data:{bean,list,...} }
│   │   └── GeneralMessage         静态工厂，对齐 rainbond-console general_message
│   ├── exception/
│   │   └── ServiceHandleException 业务异常基类（占位）
│   └── util/                      （留空）
├── infrastructure/                基础设施层（本骨架仅留空包）
│   ├── jpa/                       BaseEntity/审计字段，下个 change 引入
│   ├── region/                    Region API client，独立 epic
│   └── k8s/                       kubernetes-client 封装，rke2 阶段用
├── modules/                       业务模块根（本骨架不进任何业务代码）
│   ├── account/  team/  application/  region/  plugin/  market/  misc/
└── healthz/
    └── HealthzController          GET /console/healthz（唯一交付端点）
```

## 关键约束

### 共享 rainbond-console 数据库

- 默认 JDBC URL：`jdbc:mysql://127.0.0.1:3306/console`，dev 凭据 `root/123456`
- `hibernate.ddl-auto=validate`，**任何环境都不允许 Hibernate 输出 DDL**
- Flyway `baseline-on-migrate=true`、`baseline-version=0`，`db/migration` 目录刻意为空
- schema 演进权属于 rainbond-console（Django migrations）；kuship-console 只做反向校验
- **不要在 entity 上擅自加 `@Version` 列** —— Django 端不认识这个字段，会破坏写入
- 凭据通过 `application-local.yaml`（gitignored）或环境变量 `DB_URL/DB_USERNAME/DB_PASSWORD` 注入

### URL 契约

- **不使用 `server.servlet.context-path`** —— 根域下还有 `/openapi`、`/app-server`、`/api`、`/install-cluster.sh` 等路径
- 每个 controller 显式声明完整路径前缀，例如 `@RequestMapping("/console/...")`
- 路径变量名严格保留 Django 原始命名：`{team_name}`、`{region_name}`、`{service_alias}`、`{app_id}`
  —— **不得改写为驼峰**，会破坏 kuship-ui 的客户端调用
- trailing slash 兼容：在每个 controller 注解里同时列出 `/path` 与 `/path/`
  （Spring 6 已不支持全局 trailing slash 匹配；本项目选择"controller 显式列出"方案）

### 响应格式契约（自动包装已就绪）

所有 `@RestController` 方法的返回值由 `GeneralMessageResponseBodyAdvice` **自动**包装为 `ApiResult` 形状，业务 controller 直接 `return user;` / `return userList;` / `return page;` 即可。

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [],
    "...任意 kwargs": "..."
  }
}
```

- 顶层字段顺序：`code` → `msg` → `msg_show` → `data`
- `msg_show` 使用 `@JsonProperty("msg_show")` 强制 snake_case，**不要**改成驼峰
- `data` 节点必含 `bean`（缺省 `{}`）与 `list`（缺省 `[]`）
- 自动包装映射：POJO/Map → `data.bean`；`List<T>` → `data.list`；`Page<T>` → `data.list = content` + `data.bean.total = totalElements`（不输出 page/page_size）；`ApiResult` 幂等不重复包装
- 转义阀：`@SkipResponseWrapper` 注解（方法级或类级）跳过包装，用于 SSE / 文件下载
- 例外：`String` 返回类型不自动包装（Spring 对 String 有特殊 cast 逻辑）；如要包装请显式 `return GeneralMessage.ok(Map.of("value", str))`

### 全局异常映射

`GlobalExceptionHandler` 把以下异常类型自动映射为 `general_message` 形状：

| 异常 | code | msg_show |
|------|------|----------|
| `ServiceHandleException` | 透传 | 透传 |
| `MethodArgumentNotValidException` / `ConstraintViolationException` | 400 | 参数校验失败 |
| `HttpMessageNotReadableException` | 400 | 请求体解析失败 |
| `MissingRequestHeaderException` / `MethodArgumentTypeMismatchException` | 400 | 请求参数不正确 |
| `IllegalArgumentException` | 400 | 参数校验失败 |
| 兜底 `Exception` | 500 | 系统异常（响应体含 `data.bean.trace_id`） |

HTTP 状态码与业务 `code` **对齐**（与 rainbond-console DRF 行为一致，`align-error-http-status` change 起）：业务异常 HTTP 状态码 = 业务 `code`（如 `ServiceHandleException(404,...)` → HTTP 404）；Region 异常优先用其 `httpStatus`，缺失退回 `code` 或 500；body 仍是 `general_message` 形状（`{code, msg, msg_show, data}`）。这样 kuship-ui 直接复用 rainbond-ui 的 `request.js`（按 HTTP 状态进入 axios catch + 全局 toast），无需为 kuship 单独改造前端。

### JWT 认证（兼容 djangorestframework-jwt 1.11.0）

- Authorization 前缀：**接受 `GRJWT`（主）和 `jwt`（小写，外部 portal 兼容）两种**，不区分大小写
- 算法：HS256，与 Django 端 `JWT_AUTH.JWT_ALGORITHM` 一致
- SECRET_KEY：从 `kuship.security.jwt.secret-key`（默认 `${JWT_SECRET_KEY}` 环境变量）读取，**必须与 rainbond-console Django 进程同源**；非 local profile 启动时为空将拒绝启动
- payload claims 直接使用 Django 风格：`user_id`、`username`/`nick_name`、`email`、`exp`、`orig_iat`，不做名字转换
- 401 响应 `msg` 始终暴露具体原因（`missing token`/`token expired`/`invalid signature`/`malformed token`），`msg_show` 始终为统一文案 `"未认证或 token 失效"`
- 请求中通过 `RequestContext`（@RequestScope bean）拿到 `user_id`、`username`、`email`、`enterpriseId`、`sysAdmin`
- token 中的 `user_id` 必须真实存在于 `user_info` 表；不存在时 401 + `msg="user not found"`（`migrate-console-account-team` 起强制）
- 跨服务互认：rainbond-console 与 kuship-console 配置同一 `JWT_SECRET_KEY` → 双向 token 互认零成本，前端无感

### 请求上下文（RequestContext）

`@RequestScope` bean，业务层 `@Autowired` 即可：
- `userId` (Integer) / `username` / `email` —— 由 `JwtAuthenticationFilter` 解析 token 后通过 `userInfoRepository.findById` 真实加载
- `teamName` / `regionName` —— 由 `TenantContextInterceptor` 从 path variable `{team_name}`/`{region_name}` 提取写入（必须保留 snake_case 命名）
- `enterpriseId` (String, char(32)) —— 来自 `user_info.enterprise_id`
- `sysAdmin` (boolean) —— 来自 `user_info.sys_admin`，`@RequirePerm` 切面下直接放行
- 异步任务（@Async / 虚拟线程）必须显式传递这些字段

### 分页

- 输入：query 参数 `page`、`page_size` **一基**（page=1 是第一页），缺省 `page=1`、`page_size=10`，上限 `page_size=200`；非法输入触发 400
- 转换工具：`PageRequestAdapter.toPageable(page, pageSize)` 内部转 0 基传给 Spring Data
- 输出：controller 直接 `return page;`（`Page<T>`），advice 自动注入 `data.list = content` + `data.bean.total = totalElements`，**不输出顶层 `page`、`page_size`**（kuship-ui `HttpTable/TcpTable/EnvironmentVariable/ClusterMgtInfo` 等组件按 `data.bean.total` 读取）

### TraceId

- 每个请求生成 UUID，写入 SLF4J MDC（key: `traceId`）+ 响应头 `X-Trace-Id`
- 异常日志 logback pattern 含 `%X{traceId}`
- 兜底 Exception 响应体的 `data.bean.trace_id` 等于响应头，便于用户复制后报障

### Security

- `permitAll`：`/actuator/**`、`/error`、`/console/login`、`/console/oauth/**`、`/console/healthz`（含 trailing slash）
- `authenticated`（要求 JWT）：其他 `/console/**`、`/openapi/**`
- Filter 链：`TraceIdFilter`（最高优先级） → `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter`
- 401/403 走自定义 `GeneralMessageAuthenticationEntryPoint` / `GeneralMessageAccessDeniedHandler`，响应体仍是 general_message 形状
- Session `STATELESS`，CSRF / formLogin / httpBasic 全部关闭

### 账户 / 团队 / 权限（migrate-console-account-team）

`cn.kuship.console.modules.account` 落地用户认证、团队管理、企业管理、RBAC 全套（共 ~30 endpoint）。

**Controllers**：
- `UserAuthController` — `/console/users/login`、`logout`、`register`、`changepwd`
- `UserSelfController` — `/console/users/details`、`team_details`、`query`
- `UserAccessTokenController` — `/console/users/access-token` PAT CRUD
- `TeamController` — `/console/teams/init`、`/console/teams/{team_name}` PUT/DELETE、`/exit`
- `TeamMemberController` — `/console/teams/{team_name}/users`、`notjoinusers`、`pemtransfer`
- `TeamRoleController` — `/console/teams/{team_name}/roles{,/{role_id}{,/perms}}`、`users/roles`、`users/{user_id}/roles`
- `EnterpriseController` — `/console/enterprise/info`（公开）、`enterprises`、`enterprise/{id}{,/teams,/myteams}`
- `EnterpriseUserController` — `/console/enterprise/{id}/{users,user/{uid},admin/user[/{uid}],admin/roles,users/{uid}/teams/{tname}/roles}`
- `PermsController` — `/console/perms`（公开）、`/console/init/perms`

**JPA Entity**（`modules/account/entity/`）：
- 12 个共享 schema entity：`UserInfo`、`Tenants`、`TenantEnterprise`、`EnterpriseUserPerm`、`PermRelTenant`、`UserRole`、`RoleInfo`、`RolePerms`、`PermsInfo`、`PermGroup`、`UserAccessKey`、`TenantRegionInfo`
- **PK 类型必须是 `Integer`**（Django INT 4 字节），用 `Long` 会触发 `Schema validation: wrong column type`
- `Tenants.creater` 保留 rainbond 历史 typo（不要重命名为 `creator`）

**RBAC 注解**：
```java
@RequirePerm("app_create")          // 团队级权限码（OR：可传多个）
@RequireEnterpriseAdmin              // 企业管理员（不要求在某个 team 内）
```
- `PermAspect` 拦截：从 `RequestContext.userId/teamName/enterpriseId` 校验
- `RequestContext.sysAdmin=true` 直接放行
- 60s Caffeine 缓存（`user-team-perms` / `user-enterprise-admin`）；修改角色 / 权限的端点显式 evict

**密码哈希**：
- `LegacyPasswordEncoder` 复刻 rainbond `www/utils/crypt.py::encrypt_passwd` —— `SHA-224(c7+input+c5+'goodrain'+c2/7).hex[:16]`
- **不切换到 bcrypt**：保证 user_info.password 字段在 rainbond-console 与 kuship-console 之间二进制兼容；跨服务登录前提
- 安全升级（DelegatingPasswordEncoder + bcrypt 双格式）属于独立 change，不在本 change 范围内

**JWT 签发**：
- `JwtIssuer.issue(UserInfo)` —— TTL 默认 3650 天（rainbond 历史选择，可调 `kuship.security.jwt.expiration-days`）
- payload 100% 沿用 Django：`user_id`、`username`、`email`、`exp`、`orig_iat`
- SECRET_KEY 同源时 rainbond ↔ kuship 双向互认

**SecurityConfig 公开端点白名单**：
- `POST /console/users/{login,register,logout}`
- `GET /console/{enterprise/info,perms}`
- `POST /console/init/perms`（仅 `kuship.security.allow-public-init=true` 时；默认 false）

### 应用创建（migrate-console-app-create）

`cn.kuship.console.modules.appcreate` 落地"从零创建组件 + 检查 + 构建 + 删除"全套（~12 endpoint，3 种来源）。

**Controllers**：
- `AppImageCreateController` — `POST /console/teams/{team_name}/apps/docker_run`（基于镜像创建）
- `AppSourceCodeCreateController` — `POST /console/teams/{team_name}/apps/source_code`（基于 Git 创建）
- `AppThirdPartyCreateController` — `POST /console/teams/{team_name}/apps/third_party`（外部 endpoint，不调 region）
- `AppCheckController` — `/console/teams/{team_name}/apps/{service_alias}/{check, get_check_uuid, check_update}` 异步三段式
- `AppBuildController` — `.../{build, code/branch, compile_env}`
- `AppDeleteController` — `POST .../{service_alias}/delete`（软删除归档）

**JPA Entity**（2 张新表）：
- `ServiceSourceInfo`（`service_source`，存创建参数 git/image/dockerfile 等独立留底）
- `TenantServiceInfoDelete`（`tenant_service_delete`，组件软删除归档）

**写两阶段策略**（与 application-core 不同）：
- **创建**：先 console 后 region —— service_id 由 console 生成 → 写 tenant_service / service_source / service_group_relation → 调 region createService（事务包裹，region 失败 rollback console 写入）
- **删除**：先 region 后 console —— 调 region deleteService 释放 K8s 资源 → 写 tenant_service_delete 归档 → 删本地 tenant_service / service_source / service_group_relation / 子资源（envs/ports/volumes/dependency/probe）行（事务包裹）
- **third_party**：不调 region createService（无 K8s deployment）

**RegionServicePayloadBuilder**：把 `TenantService` + `ServiceSourceInfo` 字段统一拼成 region createService body；3 种来源都用同一份转换逻辑。

**14 接口骨架进度**：本 change 完成 `ServiceOperations` 6 method（createService / updateService / deleteService / buildService / codeCheck / getServiceLanguage）。

### 应用与组件管理（migrate-console-application-core）

`cn.kuship.console.modules.application` 落地"应用主体（service_group）+ 组件查询 + 6 类配置子资源"的 ~38 endpoint。

**Controllers**：
- `GroupController` — `/console/teams/{team_name}/groups` 应用 CRUD + status + component_names + governancemode
- `ComponentController` — `/console/teams/{team_name}/apps/{service_alias}/{detail,brief,group,keyword}`
- `AppEnvController` — `/console/teams/{team_name}/apps/{service_alias}/envs`（仅本地写）
- `AppPortController` — `/console/teams/{team_name}/apps/{service_alias}/ports`（先 region 后本地）
- `AppVolumeController` — `/console/teams/{team_name}/apps/{service_alias}/volumes`（先 region 后本地）
- `AppDependencyController` — `/console/teams/{team_name}/apps/{service_alias}/{dependency,dependency-list,dependency-reverse}`
- `AppProbeController` — `/console/teams/{team_name}/apps/{service_alias}/probe`（同 mode 软去重）

**JPA Entity**（8 张表，`modules/application/entity/`）：
- `ServiceGroup`（service_group）/ `ServiceGroupRelation`（service_group_relation 应用-组件 N:N）
- `TenantService`（tenant_service 50+ 列大表，本 change 一次性映射避免反复扩 entity）
- `TenantServiceEnvVar` / `TenantServicesPort` / `TenantServiceVolume` / `TenantServiceRelation`（依赖）/ `ServiceProbe`

**写两阶段策略**：env 仅本地（rainbond 历史选择）；port/volume/dependency/probe 先调 region API → 再写本地表（事务包裹，region 失败本地不写）。region 与本地状态可能因 region 写成功本地写失败而不一致，记入独立 reconciliation hardening。

**14 接口骨架进度**：本 change 完成 `ServiceOperations.getServiceInfo` + `ServicePortOperations` 5 method + `ServiceVolumeOperations` 3 method + `ServiceDependencyOperations` 2 method + `ServiceProbeOperations` 3 method。`ServiceEnvOperations` 保持 unsupported（rainbond env 通过本地 + 重启同步）。

### 应用运行时（migrate-console-app-runtime）

`cn.kuship.console.modules.appruntime` 落地"组件跑起来"全套：生命周期 / 扩缩容 / 状态 / Pod / 拓扑 / 事件 / 日志 / 监控 / 弹性伸缩 / 批量动作 共 ~40 endpoint。

**Controllers**：
- `AppLifecycleController` — `/start /stop /pause /unpause /vm_web /restart /deploy /rollback /upgrade`
- `AppScalingController` — `/vertical /horizontal /scaling /extend_method`
- `AppPropertyController` — `PUT /deploytype /change/service_name /set/is_upgrade`
- `AppStatusController` — `GET /apps/{alias}/status`
- `AppPodController` — `GET /apps/{alias}/pods` / `pods/{pod_name}` / `groups/{app_id}/pods/{pod_name}` + `POST /pods/detail`
- `AppTopologyController` — `GET /groups/{group_id}/topological{,/internet}`
- `AppVisitController` — `GET /apps/{alias}/visit` / `groups/{group_id}/visit`
- `AppEventController` — `GET /apps/{alias}/{events,event_log}` / `GET /teams/{team}/events{,/{eventId}/log}`
- `AppLogController` — `GET /apps/{alias}/{log,log_instance,history_log,logs}`
- `LogProxyController` — `POST /log_proxy`
- `AppMonitorController` — `GET /apps/{alias}/{monitor/query,monitor/query_range,resource}` + `groups/{group_id}/monitor/batch_query`
- `AppTraceController` — `GET / POST / DELETE /apps/{alias}/trace`
- `AppAutoscalerController` — xparules CRUD + xparecords
- `AppBatchActionsController` — `POST /teams/{team}/batch_actions`
- `AppBatchDeleteController` — `DELETE /teams/{team}/{batch_delete,again_delete}`
- `AppGroupDeleteController` — `DELETE /teams/{team}/groupapp/{group_id}/delete`

**JPA Entity**（2 张：仅 autoscaler 域有本地表）：
- `AutoscalerRule`（autoscaler_rules，PK Integer 自增 + rule_id 32-char UUID + service_id + enable + xpa_type + min_replicas + max_replicas）
- `AutoscalerRuleMetric`（autoscaler_rule_metrics，rule_id 逻辑关联）
- 注意：实际 schema 没有 `create_time` 列，entity 不含此字段；ddl-auto=validate 会报错

**写策略**：
- 生命周期 8 端点：调 region → 本地 update_time + update_version+1（不写新表）
- 扩缩容 4 端点：事务内本地 update tenant_service.{min_cpu,min_memory,min_node,...} + 调 region；region 失败回滚
- xparules CRUD：本地 + region 双写（创建/更新先本地后 region；删除先 region 后本地）；列表纯本地
- 批量删除：循环复用 `AppDeleteService.delete()`（in appcreate 模块）
- 整组删除：列出 group → 逐个软删除 → 全成功后删 service_group 自身

**14 接口骨架进度**：本 change 完成 `ServiceLifecycleOperations` 10 method + `ServiceStatusOperations` 6 method + `ServiceLogOperations` 3 method + `EventOperations` 3 method = **22 method**；新增 2 个非骨架接口 `MonitorOperations`（4 method）/ `AutoscalerOperations`（4 method） in appruntime 模块。

**响应透传**：监控 / 事件 / 日志 / Pod 详情全部透传 region JSON（不重新包 ApiResult.bean）；advice 自动包成 general_message 形状。

### 应用市场（migrate-console-app-market）

`cn.kuship.console.modules.appmarket` 落地"应用模板 / Tag / 远程市场 / 单组件版本 / 整组升级 / 服务分享 / 整组备份 / Helm Chart / image_tags" 9 子域共 ~50 endpoint。

**子域结构**：
```
appmarket/
├── api/RegionApiSupport               共享 region 调用 helper
├── controller/TenantImageTagsController  公网 hub registry tags 列表
├── market/   (Center App + Tag + AppMarket + market_create + cmd_create)
├── version/  (单组件版本 + 快照 + 回滚)
├── share/    (service_share_record 全异步流程，6-step / 3-status 状态机透传)
├── upgrade/  (app_upgrade_record + parent_id 升级回滚父子链)
├── backup/   (groupapp_backup + 整组 copy/migrate + 企业级备份列表 + BackupOperations)
└── helm/     (helm_repo + AesGcmEncryptor 密码加密 + 5 个 helm_* 端点)
```

**新增 Entity**（10 张本地表 JPA 映射）：
- market：`RainbondCenterApp`（19 列含 `is_ingerit` 历史拼写）/ `RainbondCenterAppVersion`（25 列 longtext app_template）/ `CenterAppTag`（**rainbond_center_app_tag**）/ `CenterAppTagRelation`（**rainbond_center_app_tag_relation**）/ `AppMarket`
- share：`ServiceShareRecord`（19 列）/ `ServiceShareRecordEvent`
- upgrade：`AppUpgradeRecord`（17 列含 `record_type` + `parent_id`）
- backup：`ServiceGroupBackup`（17 列含 `backup_size` bigint + `total_memory`）
- helm：`HelmRepo`（password 列 AES-GCM 加密落盘）

**新增 Region API**：
- `HelmOperations` 6 method 完整实现（`HelmOperationsImpl` @Primary）：getChartInformation / checkHelmApp / getYamlByChart / getUploadChartInformation / getUploadChartValue / importUploadChartResource
- 新增非骨架接口 `BackupOperations`（4 method：backup / backupStatus / restore / export）+ `BackupOperationsImpl`

**Helm Repo 密码加密**：
- `AesGcmEncryptor` AES-256-GCM 加密器；密钥从 `kuship.helm.repo-password-key` 配置项读取
- prod profile 缺密钥启动失败；dev / local / contract-test profile 退化为明文（带告警）
- 加密格式：`AES:` 前缀 + Base64 编码（IV+密文）；解密自动识别前缀

**14 接口骨架进度**：本 change 完成 `HelmOperations` 6 method = **12/14 接口完整**；新增 `BackupOperations` 4 method（非骨架）

**Schema 真相检查**：每张表入库前用 `docker exec kuship-mysql mysql ... DESC <table>` 确认列存在与类型；列名 `describe`、`is_ingerit`（拼写错）保留 rainbond 历史。

### 插件系统（migrate-console-plugin）

`cn.kuship.console.modules.plugin` 落地"插件 CRUD / 版本构建 / 配置组 / 组件挂载 / 插件分享 / 应用市场插件 / Rainbond 平台插件代理" 6 子域共 ~40 endpoint。

**子域结构**：
```
plugin/
├── api/                       共享 RegionApiSupport + 2 region API（Plugin + RainbondPlugin）
├── service/PluginContextLoader  按 team_name + plugin_id 取 Tenant + TenantPlugin
├── team/        (TenantPlugin + PluginBuildVersion + PluginConfigGroup/Items + TenantPluginShare + 4 controller)
├── comp/        (TenantServicePluginRelation + Attr + ConfigVar + 1 controller)  ★ 注意是 comp/ 不是 service/，避免命名冲突
├── market/      (RainbondCenterPlugin + 1 controller，应用市场插件 + 一键安装)
└── platform/    (Region + Proxy 2 controller，平台插件代理 + 静态资源/后端透传)
```

**新增 Entity**（10 张本地表 JPA 映射）：
- team：`TenantPlugin`(17 列含 `desc` 反引号 + origin/origin_share_id) / `PluginBuildVersion`(16 列双状态) / `PluginConfigGroup`(6 列) / `PluginConfigItems`(12 列含 longtext) / `TenantPluginShare`(17 列含 varchar(4096) config) / `PluginShareRecordEvent`(10 列)
- comp：`TenantServicePluginRelation`(8 列) / `TenantServicePluginAttr`(17 列含 dest_service_id) / `ServicePluginConfigVar`(11 列含 longtext attrs)
- market：`RainbondCenterPlugin`(20 列含 longtext plugin_template/details + `desc` 反引号)

**新增 Region API**（非 14 接口骨架）：
- `PluginOperations` 10 method（@Primary）：createPlugin / updatePlugin / deletePlugin / buildPlugin / getPluginBuildStatus / installToService / uninstallFromService / openOnService / syncFromMarket / installFromMarket
- `RainbondPluginOperations` 8 method：listPlugins / listPlatformPlugins / listOfficialPlugins / listObservablePlugins / installPlatformPlugin / getPluginStatus / proxyStaticResource / proxyBackend
  - `proxyStaticResource` / `proxyBackend` 返回 `byte[]` + Content-Type，最大 10MB（超出 413）
  - 静态资源代理用 `@SkipResponseWrapper` 跳过 ApiResult 自动包装

**插件挂载流程**：组件 + 插件三表关联（relation + attr + config_var），先本地 INSERT/DELETE 后 region 通知；region 失败不阻塞本地写入（避免幽灵数据）。

**插件分享状态机**：与第 9 阶段 service-share 同构（6-step / 3-status），controller 类名 `PluginShareController` 区分 `ServiceShareController`；`tenant_plugin_share` 用 `share_version` 字段拼 `_COMPLETE` 后缀标记完成（无独立 status 列）。

**保留字 `desc`**：`tenant_plugin` / `tenant_plugin_share` / `rainbond_center_plugin` 三张表都有 `desc` 列；entity 用 `@Column(name = "\`desc\`")` 反引号转义，Java 字段统一命名 `describe`。

### 杂项收尾（migrate-console-misc）

`cn.kuship.console.modules.misc` 落地剩余 14 个 view 的 ~50 endpoint：消息中心 / Webhook / MCP / 文件上传 / 登录事件 / 操作审计 / Console 升级 / 企业配置 / SMS / KubeBlocks / API Gateway / 占位收尾。

**子域结构**：
```
misc/
├── message/      UserMessage entity + 2 endpoint（GET/PUT 消息）
├── webhook/      ServiceWebhooks entity + 7 endpoint（git/image/custom + 管理 4）
├── mcp/          MCPQueryController（HTTP JSON-RPC 占位，SSE 推迟）
├── upload/       FileUploadController（本地磁盘 5MB 上限 + byte[] 下载）
├── audit/        LoginEvents + OperationLog entities + 4 endpoint（登录事件 + 3 级审计）
├── upgrade/      ConsoleUpgradeController 4 endpoint（占位返回固定版本）
├── config/       EnterpriseConfigController + EnterpriseActiveController（复用 ConsoleConfig）
├── sms/          SmsVerificationCode entity + 5 endpoint（dev profile 打印 code）
├── kubeblocks/   KubeBlocksController 8 endpoint（透传占位）
├── gateway/      ApiGatewayController 4 endpoint（透传占位）
└── other/        MiscOtherController：platform-settings / task-guidance / errlog / team-overview / team-resources / k8s_attribute / k8s_resource
```

**新增 Entity**（5 张本地表 JPA 映射）：
- `UserMessage`（user_message，11 列含 announcement_id + level）
- `ServiceWebhooks`（service_webhooks，5 列）
- `LoginEvents`（login_events，10 列含 client_ip/user_agent/duration）
- `OperationLog`（operation_log，14 列含 longtext old/new_information）
- `SmsVerificationCode`（sms_verification_code，6 列，**PK 是小写 `id`**）

**OperationLog 列表查询优化**：用 `OperationLogSummary` 投影 record 不返 longtext old/new_information；详情 endpoint 才查完整行。
**`ConsoleConfigRepository`**：扩展 `findByKey` / `findByKeyStartingWith` / `deleteByKey` 三个 method，企业配置复用此表（key 命名 `{eid}.{name}`）。
**SMS 安全约束**（升级，见 `add-aliyun-sms`）：dev profile 走 LoggingSmsProvider（控台打印 code），prod profile 切 AliyunSmsProvider（aliyun-dysmsapi 2.0 SDK），叠加 60s 单手机号限流 + 5min/5次失败暴破防护。详见下面"SMS 集成"段落。
**Webhook 校验**（升级，见 `harden-webhook-hmac`）：trigger 三端点优先 header 签名（HMAC / token / bearer），secret query 作 fallback 兼容期保留并发 WARN 日志。详见下面"Webhook HMAC 签名"段落。
**File upload**：默认 `${kuship.upload.dir:/tmp/kuship}` 本地磁盘 + 5MB 上限；S3/MinIO 集成留作 hardening。

**14 接口骨架进度不变**：本 change 未触及 14 接口骨架（misc 端点不需要 region 调用，或直接在 controller 内 RestClient 调用，未引入新接口）。

### OpenAPI v1（migrate-openapi-v1）

`cn.kuship.console.modules.openapi` 落地面向第三方 / CLI / 自动化集成的 `/openapi/v1/**` ~30 endpoint。与 console UI 后端有 3 个本质差异：认证模式 / 响应格式 / 错误风格。

**模块结构**：
```
openapi/
├── auth/           OpenApiAuthFilter（仅匹配 /openapi/**，X-Internal-Token + PAT 双模）
├── exception/      OpenApiExceptionHandler（detail/code 格式 + HTTP 状态码与业务码一致）
├── docs/           SpringDocConfig 占位（springdoc 集成留作 hardening）
└── v1/
    ├── region/        3 endpoint（regions list / detail / grctl ip）
    ├── user/          7 endpoint（users / currentuser / changepwd / close / delete）
    ├── admin/         2 endpoint（administrators，仅 sys_admin 用户）
    ├── team/          11 endpoint（teams / app_model / certificates / regions / events 等）
    ├── enterprise/    9 endpoint（overview + monitor 系列 + instances）
    ├── app/           13 endpoint（list / port / deploy / smart-deploy / import / chart / delete / helm + 4 灰度占位）
    └── other/         3 endpoint（httpdomains / gray-releases / mcp/query）
```

**认证（OpenApiAuthFilter）**：
- 仅匹配 `/openapi/**` 路径
- `X-Internal-Token` 头与 `${kuship.openapi.internal-token}`（默认 env `INTERNAL_API_TOKEN`）比对 → 注入虚拟 admin（user_id=0, sysAdmin=true）
- `Authorization` 头作为 PAT 在 `user_access_key` 表查询 → 加载 UserInfo（要求 `sys_admin = true`）
- 双模都失败 → 401 + `{"detail": "...", "code": 401}`
- Filter 在 Spring Security FilterChain 中加在 `JwtAuthenticationFilter` 之前；`/openapi/**` 在 SecurityConfig 白名单 permitAll（filter 内部自己鉴权）

**响应格式分流**：
- `GeneralMessageResponseBodyAdvice.supports()` 在 `OPENAPI_PACKAGE_PREFIX` 检查跳过包装
- OpenAPI 返回业务对象 JSON 直接（不包 `{code, msg, msg_show, data}` 外壳）
- 错误用 `OpenApiExceptionHandler` 映射成 `{detail, code}` + HTTP 状态码（**与 console 一律 200 不同**）

**复用而非重写**：50 endpoint 全部直接读 console 已有 entity / repository（zero new business table），仅做"读 → map 到 OpenAPI bean"的转换层。

**未实现 / hardening 范围**：
- Springdoc Swagger UI 集成（兼容性需验证；占位实现）
- Monitor 4 个聚合端点（performance / resource_overview / service_overview / component_memory_overview）返回固定占位数据
- ~~App 灰度发布 4 endpoint 全部占位~~ —— 已升级，详见下面"灰度发布（add-gray-release）"段落
- App deploy / smart-deploy / import 流程占位（深度集成第 9 阶段 HelmOperations 留作 hardening）

**OpenAPI 配置项**：
- `kuship.openapi.internal-token` 内部服务调用 token（默认从 env `INTERNAL_API_TOKEN` 读取，dev profile 可在 application-local.yaml 覆盖）
- `kuship.openapi.docs.enabled` Swagger UI 开关（默认 prod 关闭）

**`team_id` 路径参数**：同时接受 `tenant_id`（32-char UUID）/ `id`（Integer 主键）/ `tenant_name`（人可读）三种形式，`OpenApiTeamController.requireTeam` 三路 fallback 解析。

### MCP SSE（add-mcp-sse）

`/console/mcp/query/*` 三端点提供 MCP（Model Context Protocol，2024-11-05）服务器，让 LLM 客户端
（Claude Desktop / Cursor / Cline 等）通过 SSE 长连接 + JSON-RPC 调用 kuship 集群操作。

**协议握手**（标准 MCP SSE 流程）：

```
client                                          server (kuship-console)
  |                                                   |
  |---  GET /sse  Authorization: Bearer <PAT>  ------>|
  |                                                   |
  |<--  event: endpoint                               |
  |     data: https://host/console/mcp/query/message?session_id=<sid>
  |                                                   |
  |---  POST /message?session_id=<sid>  ------------->|
  |     {"jsonrpc":"2.0","id":1,"method":"initialize",...}
  |                                                   |
  |<--  HTTP 202 (no body)                            |
  |                                                   |
  |<--  event: message  (over the SSE channel)        |
  |     data: {"jsonrpc":"2.0","id":1,"result":{...}}
  |                                                   |
  |  ... (tools/list / tools/call / ping) ...         |
  |                                                   |
  |<--  : keep-alive (every 25s, comment frame)       |
```

**5 个核心 method**（`McpProtocolHandler`）：

| method | params | result | 说明 |
|---|---|---|---|
| `initialize` | `{protocolVersion, capabilities, clientInfo}` | `{protocolVersion, capabilities:{tools:{}}, serverInfo}` | 协议握手 |
| `notifications/initialized` | `{}` | 无（notification） | client 完成初始化通知 |
| `tools/list` | `{cursor?: string}` | `{tools:[{name, description, inputSchema}]}` | 列工具 |
| `tools/call` | `{name, arguments}` | `{content:[{type,text}]}` | 调工具 |
| `ping` | `{}` | `{}` | 心跳 |

未识别 method → `-32601 Method not found`；params 错 → `-32602 Invalid params`；tool 内部异常 → `-32603 Internal error`。

**8 个 MVP tool**（`modules/misc/mcp/tool/impl/`）：

| name | input | 输出 | 数据源 |
|---|---|---|---|
| `get_current_user` | `{}` | `{user_id, nick_name, email, enterprise_id, sys_admin}` | UserInfoRepository |
| `list_regions` | `{}` | `{regions: [...]}` | RegionInfoEntityRepository |
| `list_teams` | `{}` | `{teams: [...]}` | PermRelTenantRepository + TenantsRepository |
| `list_apps` | `{team_name, region_name}` | `{apps: [...]}` | ServiceGroupRepository |
| `list_components` | `{app_id}` | `{components: [...]}` | ServiceGroupRelationRepository + TenantServiceRepository |
| `get_component_detail` | `{service_id}` | TenantService 全字段 | TenantServiceRepository |
| `get_component_pods` | `{service_id}` | region API 透传 | ServiceStatusOperations.getServicePods |
| `get_component_logs` | `{service_id, lines? = 100}` | region API 透传 | ServiceLogOperations.getServiceLogs |

剩余 285 个 rainbond MCP tool 留作后续 batch hardening：`add-mcp-tools-batch1` (apps lifecycle / pods detail / events) → `batch2` (deploy / build / scaling) → `batch3` (share / market / autoscaler) → ...

**SSE 鉴权矩阵**：

| 场景 | 客户端 | 鉴权方式 |
|---|---|---|
| 推荐：原生 / curl | Claude Desktop / curl | `Authorization: Bearer <PAT>` |
| 兼容：浏览器 EventSource | 浏览器 LLM 集成 | `?access_token=<PAT>` query 参数（仅 SSE GET 端点接受） |
| POST `/message` | 任何 | 仅 header（POST 客户端可设 header） |
| POST `/console/mcp/query` | 任何 | 仅 header |

**Nginx access_token 日志剥离**（防 PAT 泄露 access log）：

```nginx
log_format mcp_safe '$remote_addr - $remote_user [$time_local] '
                    '"$request_uri_safe" $status $body_bytes_sent';
map $request_uri $request_uri_safe {
    "~^(?<base>[^?]*)\?(.*&)?access_token=[^&]+(.*)$"  "$base?$2[REDACTED]$3";
    default $request_uri;
}
location /console/mcp/query/sse {
    access_log /var/log/nginx/mcp.access.log mcp_safe;
    proxy_pass http://kuship_console_backend;
}
```

**多副本部署 nginx ip_hash**（SSE session 是 in-memory，不跨副本共享）：

```nginx
upstream kuship_console_backend {
    ip_hash;     # 同一客户端 IP 始终打到同一副本，保证 GET /sse + POST /message 落同一副本
    server kuship-console-1:8080;
    server kuship-console-2:8080;
}
```

如果未来需要真正跨副本共享 session（如 SLB 不支持 ip_hash），走 `add-distributed-mcp-sessions` hardening 引入 Redis Streams 后端。

**配置项**：

| 配置项 | 默认值 | 用途 |
|---|---|---|
| `kuship.mcp.protocol-version` | `2024-11-05` | MCP spec 版本字符串（initialize 返回） |
| `kuship.mcp.server-name` / `server-version` | `kuship-console` / `0.1.0` | initialize.serverInfo |
| `kuship.mcp.max-sessions` | `200` | Caffeine cache maxSize；与 `server.tomcat.threads.max` 同步调 |
| `kuship.mcp.session-ttl-minutes` | `30` | session expireAfterAccess |
| `kuship.mcp.heartbeat-seconds` | `25` | SSE keep-alive comment 间隔 |

**未来 hardening 路径**：
- `add-mcp-tools-batch1/2/3/...` —— 逐批迁移 rainbond Python 端 285 个 tool
- `add-distributed-mcp-sessions` —— Redis Streams 后端，支持任意 LB 路由
- `add-mcp-resources` —— MCP resources / prompts / sampling capability（当前只 tools）
- `add-mcp-async-tools` —— 长时 tool 异步化（当前同步阻塞 servlet 线程，单 RPC 默认 5s 内）
- `add-mcp-schema-validation` —— 统一 JSONSchema Draft 7 args 校验（当前 tool 自校验）

**与 OpenApiAuthFilter 区别**：
- OpenApiAuthFilter 仅匹配 `/openapi/**`，需 `sys_admin = true`
- McpAuthFilter 仅匹配 `/console/mcp/query/**`，仅需 `is_active = true`（普通用户也能用 MCP）
- 两者鉴权链彼此独立，不冲突

### 灰度发布（add-gray-release）

应用级灰度发布的控制平面，承接 rainbond-console 的 4 个 OpenAPI v1 端点 + 1 个 console 端点。
ApisixRoute 后端权重操作通过 RegionClient 委托给 rainbond-go core 的 `/api-gateway/v1/{tenant_name}/routes/http`，
**kuship-console 不直接持有 K8s client**。

**状态机**：

```
ACTIVE ────► COMPLETED       (ratio=100 + 显式 promote 端点；本 change 暂未提供 promote 端点)
   │
   └──► CANCELLED             (rollback 端点)
```

`ACTIVE → ACTIVE` 比例可改（update-gray-ratio），但 status 一旦推进到 COMPLETED / CANCELLED 不可逆；
同 `(tenant_id, app_id)` 不允许并行 2 条 ACTIVE（应用层校验 + 409 拒绝；DB 层不加 unique index，留作
独立 hardening `enforce-grayrelease-uniqueness`）。

**端点表**：

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release` | 创建灰度（template_id + domain_name + gray_ratio） | OpenApiAuthFilter |
| PUT | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-ratio` | 调整比例 | OpenApiAuthFilter |
| POST | `/openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-rollback` | 回滚（apisix 100:0 + record CANCELLED） | OpenApiAuthFilter |
| GET | `/openapi/v1/gray-releases?tenant_id=&status=&page=&page_size=` | 列表（DB 分页） | OpenApiAuthFilter |
| POST | `/console/teams/{team_name}/regions/{region_name}/apps/{app_id}/gray-release-info` | 查 service / upgrade_group_id 是否参与灰度（前端按钮态判断） | JWT |

**模块结构**：
```
modules/grayrelease/
├── entity/         GrayReleaseRecord (gray_release_record 表，18 字段) + GrayReleaseStatus enum
│                   + ServiceMappingEntry record + ServiceMappingsConverter (Jackson 3 序列化往返)
├── repository/     GrayReleaseRecordRepository (含 8 个 finder + JpaSpecificationExecutor)
├── service/        GrayReleaseService (状态机 + 业务编排，@Transactional)
│                   + ApisixRouteWeightUpdater (调 GatewayOperations.apiGatewayProxy)
│                   + GrayReleaseTemplateInstaller (stub，详见下方)
├── dto/            CreateGrayReleaseRequest / UpdateGrayRatioRequest / GrayRollbackRequest / GrayReleaseRecordDto
└── controller/     GrayReleaseInfoController (/console 端点)
                    OpenAPI v1 端点直接挂在既有 OpenApiAppController (3 端点) + OpenApiOtherController (列表)
```

**新增 region API method**：`GatewayOperations.apiGatewayProxy(regionName, enterpriseId, tenantName, path, body)` —— 通用 api-gateway 代理 POST，由 `GatewayOperationsImpl` 实现（非 2xx 抛 RegionApiException）。

**ApisixRoute 调用 body 形状**（`ApisixRouteWeightUpdater.buildBody`）：
```json
{
  "name": "<route-name>",
  "app_id": <region_app_id>,
  "namespace": "<tenant_namespace>",
  "section_name": "default",
  "gateway_name": "default",
  "gateway_namespace": "rbd-system",
  "match": {...},
  "rules": [...],
  "backends": [
    {"service_name": "<orig-svc>", "service_port": 80, "weight": 100 - ratio},
    {"service_name": "<gray-svc>", "service_port": 80, "weight": ratio}
  ],
  "plugins": [...],
  "websocket": false,
  "authentication": {...}
}
```
hosts / rules / plugins / authentication 4 字段从既有 domain config 透传（不修改），仅替换 `backends`。

**模板实例化（当前 stub）**：
- `GrayReleaseTemplateInstaller.installGrayServiceGroup` 仅生成合成 service_id / upgrade_group_id，**不**调用真实模板安装链路（`AppInstallService` 在 kuship-console 还未迁移）
- 集成测试 + `kuship.gray-release.skip-apisix-update=true` 配合，验证状态机 + Repo + 端点契约
- prod 环境若启用 add-gray-release 端点会落 GrayReleaseRecord 但**不会真实创建灰度 service group**；待 `migrate-console-app-install` change 落地后会自动透明替换 stub 行为
- WARN 日志 `[GrayRelease][stub] template install bypassed` 标记 stub 调用点，运维监控 `grep "GrayRelease.*stub"` 可统计 prod 环境实际触达灰度的频率

**配置项**：

| 配置项 | 默认值 | 用途 |
|---|---|---|
| `kuship.gray-release.max-active-per-app` | `1` | 同 app 最多 active 记录数（应用层校验，未来 DB 唯一索引） |
| `kuship.gray-release.skip-apisix-update` | `false` | 集成测试 / 无 region 环境下跳过 ApisixRoute HTTP 调用 |

**跨服务事务限制**：`createGrayRelease` 用 `@Transactional` 包 record 写入；ApisixRoute 调用在事务内**先于** record 写入（成功才落库）。但 `rollback` 路径下 ApisixRoute 调用失败仍把 record.status 写为 CANCELLED + WARN 日志，因为：
- ApisixRoute 失败可能由网络瞬断 / go core 5xx 引起，但用户已表达 rollback 意图
- 不让 record 卡在 ACTIVE 阻塞下次 create
- 运维需手动检查 `grep "rollback apisix update failed"` 确认 ApisixRoute 真实状态，必要时手动 kubectl 恢复

**OpenAPI v1 端点不走 general_message 包装**（与既有 OpenApiAppController 一致）：
- 4 个端点返回业务对象直接（POST /gray-release / PUT /gray-ratio 返 `GrayReleaseRecordDto`，POST /gray-rollback 返 `{app_id, rolled_back, record}`，GET /gray-releases 返 `{list, total, page, page_size}`）
- 错误走 `OpenApiExceptionHandler` 映射成 `{detail, code}` + HTTP 状态码

**console 端点走 general_message 包装**（与 appruntime 一致）：
- POST `/gray-release-info` 返 `Map<String, Object>` 由 advice 自动包成 `data.bean`

**未来 hardening 路径**：
- `migrate-console-app-install` —— 落地真实 AppInstallService，让 GrayReleaseTemplateInstaller 调真实模板实例化
- `add-grayrelease-promote-endpoint` —— 添加 ratio=100 + COMPLETED 推进端点
- `add-grayrelease-header-routing` —— 基于 header / cookie 的灰度匹配规则（rainbond Python 不支持，需扩 ApisixRoute body）
- `add-grayrelease-analytics` —— 灰度版本流量画像 + A/B 实验埋点
- `enforce-grayrelease-uniqueness` —— DB 加 `(tenant_id, app_id, status)` unique index 强化并发约束
- `add-distributed-grayrelease-coordination` —— 跨实例并发 active 创建去重（Redis 锁）

### SMS 集成（add-aliyun-sms）

`/console/sms/send-code` / `/console/users/register-by-phone` / `/console/users/login-by-phone` 三个端点的 SMS 发送 + 验证码校验升级。

**Provider 切换矩阵**：

| Profile | `kuship.sms.provider` | 行为 | 凭据要求 |
|---------|------------------------|------|----------|
| dev / local / contract-test | logging（默认） | 控台打印 `[SMS-MVP] phone=... code=...` | 无 |
| prod | `aliyun` | 通过 aliyun-dysmsapi 2.0 SDK 发送真实 SMS | 4 项必需配置项 |

**阿里云接入步骤**：
1. 阿里云控台 → 短信服务 → **签名管理** 创建签名（如 `kuship`）→ 等审核通过
2. 短信服务 → **模板管理** 创建模板（如 `您的验证码是${code}，5分钟内有效`）→ 拿到 templateCode（如 `SMS_123456789`）
3. RAM 控台 → 创建子账号 → 仅授权 `AliyunDysmsFullAccess` 策略 → 拿 access-key-id + access-key-secret
4. K8s Secret：
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata: { name: kuship-sms }
   stringData:
     ALIYUN_SMS_ACCESS_KEY_ID: <id>
     ALIYUN_SMS_ACCESS_KEY_SECRET: <secret>
   ```
5. Deployment `envFrom: { secretRef: { name: kuship-sms } }`
6. application-prod.yaml：
   ```yaml
   kuship:
     sms:
       provider: aliyun
       aliyun:
         sign-name: kuship
         template-code: SMS_123456789
       rate-limit:
         enabled: true
   ```

**启动时校验**：`AliyunSmsProvider.@PostConstruct` 检查 4 项配置（access-key-id / access-key-secret / sign-name / template-code），缺任一项 → IllegalStateException 拒绝启动。

**限流参数**：
- 60s 单手机号（`SmsRateLimiter`，Caffeine cache，`kuship.sms.rate-limit.enabled` 控制；prod 默认开 / dev 默认关）
- 5min 5 次失败锁定（`SmsVerifyFailureLimiter`，无 enable 开关；防 6 位 code 暴破）
- 验证成功后失败计数 reset（`SmsVerifyFailureLimiter.reset` 在 verifyCode 通过路径调用）
- 攻击空间分析：5 次窗口 × 1 个 5min code 命中率 = 5/10⁶ = 0.0005%（每 5 分钟）

**Enterprise 级模板覆盖**（部分实现）：
- `/console/enterprises/{eid}/sms-config` GET/PUT 写 `console_config` (key=`enterprise.{eid}.SMS_CONFIG`) 存 JSON
- 首版 controller 只写不读：`AliyunSmsProvider.send` 仍用全局 `kuship.sms.aliyun.*` 配置
- 完整 per-tenant runtime 切换留作 `add-multi-tenant-sms` hardening

**SecurityConfig**：3 个 SMS 相关端点 permitAll（用户登录前要能发短信）。

**未来 hardening**：
- `add-tencent-sms` —— 腾讯云 SMS provider（同 SmsProvider 接口）
- `add-distributed-sms-rate-limit` —— Redis 后端跨实例限流
- `add-sms-callback-webhook` —— 阿里云回执 webhook 落 DB
- `add-multi-tenant-sms` —— enterprise SMS_CONFIG runtime 路由
- `enable-recaptcha-sms-login` —— 在前端登录页加 reCAPTCHA / 滑块，5min/5次空间外再加一层

**SMS 端点未实现部分**：
- `register-by-phone` / `login-by-phone` 仍是 stub（验证码通过即返成功 + notice），完整 `user_info INSERT + JWT 签发`留作 `add-phone-auth-flow` change

### Webhook HMAC 签名（harden-webhook-hmac）

`/console/webhooks/{service_id}` / `/console/image/webhooks/{service_id}` / `/console/custom/deploy/{service_id}` 三个 trigger 端点的认证升级。

**4 种 header 签名格式**：

| 来源 | 端点 | header | 算法 | delivery 头 |
|------|------|--------|------|-------------|
| GitHub | git | `X-Hub-Signature-256: sha256=<hex>` | HMAC-SHA256(secret, body) | `X-GitHub-Delivery: <uuid>` |
| GitLab | git | `X-Gitlab-Token: <secret>` | 直接 token 比对（不签 body） | `X-Gitlab-Event-UUID: <uuid>` |
| Harbor | image | `Authorization: Bearer <secret>` | bearer token 比对 | （无） |
| custom | custom | `X-Kuship-Signature: sha256=<hex>` | HMAC-SHA256(secret, body) | `X-Kuship-Delivery: <uuid>` |

**curl 示例**（运行时由 `WebhookManageController.getUrl` 的 `signature_examples` 字段返回）：
```bash
# GitHub
curl -X POST https://<host>/console/webhooks/<service_id> \
  -H 'X-Hub-Signature-256: sha256=<hmac_sha256(secret, body)>' \
  -H 'X-GitHub-Delivery: <uuid>' --data '<body>'

# GitLab
curl -X POST https://<host>/console/webhooks/<service_id> \
  -H 'X-Gitlab-Token: <secret>' -H 'X-Gitlab-Event-UUID: <uuid>'

# Harbor
curl -X POST https://<host>/console/image/webhooks/<service_id> \
  -H 'Authorization: Bearer <secret>'

# custom (kuship)
curl -X POST https://<host>/console/custom/deploy/<service_id> \
  -H 'X-Kuship-Signature: sha256=<hmac_sha256(secret, body)>' \
  -H 'X-Kuship-Delivery: <uuid>' --data '<body>'
```

**反重放（5 分钟去重）**：
- `WebhookDeliveryDeduper`（Caffeine cache，maxSize=1024 / TTL=5min）按 `<service_id>:<delivery_id>` 去重
- 同 delivery_id 命中重复 → 200 + `{triggered:false, dedup:true}`，不调下游 region API
- delivery_id 缺失或 blank → 直接放行（向后兼容老客户端）
- 多实例集群 in-memory cache **不共享**：分布式 dedup 留作 `add-distributed-webhook-dedup` change（Redis 后端）

**secret query fallback（deprecated）**：
- 老客户端 `?secret=<x>` 仍 work，触发时打 WARN 日志：`webhook <kind> for service <service_id> using deprecated query secret; switch to header signature`
- 运维监控：`grep "using deprecated query secret" application.log` 找出未迁移的 service
- **deprecation 时间表**：本 change 起 **6 个月**警告期；之后独立 `enforce-webhook-signatures` change 删除 fallback，强制 header 签名

**`getUrl` 输出 v1 + v2 双 URL**：
- v1（兼容）：`git_webhook_url` / `image_webhook_url` / `custom_webhook_url`（带 `?secret=`）
- v2（推荐）：`git_webhook_url_v2` / `image_webhook_url_v2` / `custom_webhook_url_v2`（不带 query）
- 前端应在用户 webhook 配置页展示 v2 URL + 把 service.secret 提示用户填到 GitHub/GitLab webhook 的 Secret 输入框

**header 签名错误不退回 query**：任意 header 命中（值非空）即按 header 模式校验；签名失败立即 401，不再尝试 query。这是为了避免"头存在但故意发错值"成为 secret query 后门。

**SecurityConfig 白名单**：`POST /console/webhooks/*`、`POST /console/image/webhooks/*`、`POST /console/custom/deploy/*` 已加 permitAll（GitHub/GitLab 调用方不带 JWT，由 controller 自行 HMAC 验签）。

**未来 hardening**：
- `enforce-webhook-signatures` —— 6 个月后移除 secret query fallback
- `add-distributed-webhook-dedup` —— Redis 后端跨实例 dedup
- `add-webhook-audit` —— 专用 webhook 审计表（trigger 来源 IP / delivery_id / 是否签名通过）

### OpenAPI 文档（add-openapi-swagger-ui）

`/openapi/v1/**` 47 个端点的交互式 API 文档，由 Springdoc 2.x 自动生成。仅暴露 v1 公开端点，console UI 后端（`/console/**`）刻意不出现在文档中。

**访问路径**：
- JSON 规范：`http://localhost:8080/openapi/v3/api-docs`（默认组）/ `/openapi/v3/api-docs/v1`（v1 group，含完整 paths）
- Swagger UI：`http://localhost:8080/openapi/swagger-ui/index.html`（含 Try-It-Out 与 Authorize 输入框）
- swagger-config：`/openapi/v3/api-docs/swagger-config`（UI bootstrap 配置，匿名可访问）

**dev/prod 启用矩阵**：

| Profile | `kuship.openapi.docs.enabled` | swagger-ui | api-docs JSON | 用途 |
|---------|-------------------------------|------------|---------------|------|
| `local` | true（application-local.yaml 覆写） | ✓ | ✓ | dev 浏览端点 + Try-It-Out |
| `contract-test` | true（application-contract-test.yaml 覆写） | ✓ | ✓ | 集成测试断言 JSON |
| 默认（prod / 缺省） | false | ✗ | ✗ | prod 安全 |
| 手动 prod 暴露 | `KUSHIP_OPENAPI_DOCS_ENABLED=true` 环境变量 | ✓ | ✓ | 临时给 SDK 生成器 |

**双鉴权 Try-It-Out**：
- 在 Swagger UI 点 "Authorize" 按钮，弹出两个输入框：
  - **InternalToken** —— 输入 `INTERNAL_API_TOKEN` 值；底层映射到 `X-Internal-Token` 头
  - **BearerAuth** —— 输入有效 PAT；底层映射到 `Authorization: Bearer <pat>` 头
- 任一鉴权完成后，所有 Try-It-Out 调用自动携带相应 header
- 不鉴权 Try-It-Out 调用业务端点会返回 401 + `{"detail":"...","code":401}`（OpenApiAuthFilter 仍生效）

**Native 兼容**：
- 默认 `mvn -Pnative package` 已通过 `-H:IncludeResources=META-INF/resources/webjars/swagger-ui/.*` 把 webjar 打入 binary（约 8-10MB）
- 体积敏感场景（边缘节点 / IoT）可走 `mvn -Pnative-no-swagger package`：剥离 webjar、强制 `kuship.openapi.docs.enabled=false`

**Spring Boot 4 / Springdoc 2.x 已知 shim**：
- `SpringdocQuerydslIncompatibilityShim`（`BeanDefinitionRegistryPostProcessor`）—— 移除 Springdoc 2.x 的 `queryDslQuerydslPredicateOperationCustomizer` bean，绕过 Spring Data 4 中已移除的 `org.springframework.data.util.TypeInformation` 反射路径
- `kotlin-reflect` runtime 依赖 —— Springdoc 检测到 Spring Framework 7 transitively 拉的 `kotlin-stdlib` 后会用 `kotlin.reflect.full.KClasses`，本项目纯 Java 但仍需该依赖避免 `NoClassDefFoundError`
- 上述 shim 在 Springdoc 发布 Spring Boot 4 / Spring Data 4 兼容版后可删除（提交 PR 跟踪）

**Production Hardening 警示**：
- prod 生产环境**不要**开 swagger-ui（信息泄露 47 个端点目录）
- 仅暴露 `/openapi/v3/api-docs` JSON 给 SDK 生成器场景，且加反代鉴权
- swagger-ui 默认是匿名可访问（OpenApiAuthFilter SKIP_PATH_PREFIXES 列表）—— 这是 dev 体验的妥协，不是 bug

**未来 hardening**：
- `enrich-openapi-annotations` —— 给 47 个端点加 `@Operation(summary, description)` / `@Parameter(description)` / `@ApiResponse` 注释，让 JSON 含完整描述（首版未做）
- ReDoc 集成 —— Swagger UI 之外的另一种渲染（首版只 swagger-ui）

### GraalVM Native Image（enable-graalvm-native）

13 阶段路线终点：把 fat jar (~150MB / 8s) 编译为 GraalVM Native binary (~80MB / < 2s)。

**6 种启动方式对比**：

| 启动方式 | 启动时间 | 内存 | 用途 |
|----------|----------|------|------|
| `java -jar fat.jar` | ~8s | ~600MB | dev / 已有 prod |
| `java -jar -Dspring.profiles.active=prod fat.jar` | ~9s | ~600MB | prod fat-jar |
| `./kuship-console`（native + dev profile） | ~1.5s | ~250MB | local 验证 native |
| `./kuship-console -Dspring.profiles.active=prod` | ~1.5s | ~250MB | **推荐 prod 部署** |
| `docker run kuship-console-native` | ~2s | ~280MB | k8s 部署 |
| `bash scripts/native-test.sh` | n/a（跑测试） | n/a | **native 模式跑 JUnit 5 测试套件** |

**触发 native build**：
```bash
# 前置：GraalVM 21 community 已装（macOS: sdk install java 21.0.2-graalce）
bash scripts/native-build.sh        # 仅 native binary
bash scripts/native-build.sh docker # native + docker image
```

**Native 兼容性约束**（前 12 阶段已铺路）：
- Hibernate 字节码增强已关闭（`hibernate.jakarta.persistence.bytecode.strategy = none`）—— 5% 性能损失，可接受
- Spring AOT 通过 `spring.aot.enabled` 环境变量控制，默认 false（fat jar），native build 时 plugin 自动设 true
- `KuShipConsoleRuntimeHints` 用 ClassPathScanner 自动注册 ~58 entity 反射，避免手写
- BouncyCastle 通过 `--initialize-at-build-time=org.bouncycastle` 在 build 时初始化
- 资源文件（YAML / SQL）通过 `-H:IncludeResources=...` plugin buildArg 显式注册

**已知 hardening 范围**：
- Springdoc Swagger UI native 集成（独立 hardening change）
- standalone 镜像默认仍 fat-jar；用户主动 `--build-arg NATIVE=true` 才切换

**Docker 多阶段构建**：`kuship-console/Dockerfile.native` —— Stage 1 GraalVM community 21 编译；Stage 2 distroless base 装载 binary，最终镜像 ~80MB（vs fat-jar 镜像 ~350MB）。

**测试套件**：现有 102 fat-jar 测试用例继续走 `mvn test` 不受影响；native 测试覆盖见下面 "Native Test 运行指南"。

### Native Test 运行指南（harden-native-tests）

`mvn test`（JVM）保持唯一必过门禁；`bash scripts/native-test.sh` 是 GraalVM Native 下的等价测试通道，验证生产 native binary 的反射 / 资源 / Mockito hint 不缺。

**触发 native test**：
```bash
# 前置：GraalVM 21 community 已装 + scripts/native-test.sh
bash scripts/native-test.sh             # 全量 native 测试
bash scripts/native-test.sh --quick     # 仅 NativeSmokeTest + Hints registrar 单测
```

**Maven profile 矩阵**：
- `mvn test` —— JVM 模式，必过 102/102（hardening 不破现有）
- `mvn -Pnative package` —— 仅产 native binary，跳过 surefire（`skipTests=true`）
- `mvn -Pnative,native-test test` —— native 模式跑测试，新增 hint 自动注册 + Mockito run-time init 配置

**RuntimeHints 自动注册**：
- 主 binary：`KuShipConsoleRuntimeHints` 扫 entity（位于 `src/main/java`，58 个 entity）
- test 阶段：`NativeTestRuntimeHintsRegistrar` 扫 controller（含 healthz / contract test 控制器） / DTO / common.response / entity（位于 `src/test/java`）
- **当前基线扫描数：179 个类型**（harden-native-tests 完工时）；如未来该值显著缩水（例如降到 < 150），意味着 controller/DTO 包名约定漂移或扫描规则失效，需补 hint 注册或调整 registrar 包过滤
- test registrar 通过 `src/test/resources/META-INF/spring/aot.factories` 接到 Spring AOT SPI；不污染 `mvn package` 产出的生产 binary

**何时加 `@DisabledInNativeImage`（3 类规则）**：
1. **mock final class** —— Mockito inline mock maker 不能 mock final 类
2. **mock static method** —— `Mockito.mockStatic(...)` 在 native image 下不可用
3. **反射访问私有字段** —— `ReflectionUtils.setField(..., true)` 类调用，可加 `@DisabledInNativeImage(value="reflection on private field not supported")` 标注
- 当前代码库 0 个用例命中（5 个 `@MockitoBean` 全部 mock interface）；新增测试时如果命中以上任一条，加注解 + 在 PR 描述里写明原因

**新增测试时的 hint 注册检查清单（5 步）**：
1. 新加了 controller？—— `NativeTestRuntimeHintsRegistrar` 自动扫描 `cn.kuship.console.modules.**` 下 `@RestController/@Controller`，无需手动注册
2. 新加了 DTO？—— 放到 `**.dto.**` 包下，registrar 会自动 pick 起来
3. 新加了 Mockito 用法？—— 如果是 final class / static method，加 `@DisabledInNativeImage`
4. 新加了 ClassPath 资源？—— 在 `pom.xml` `native` profile `<buildArgs>` 里加 `-H:IncludeResources=<pattern>`
5. 新加了反射访问内部 API（含 Mockito 内部）？—— 在 `NativeTestRuntimeHintsRegistrar.MOCKITO_INTERNAL_CLASSES` 数组里加 FQCN

**Hint 缺失诊断**：`scripts/native-test.sh` 自动 grep `ClassNotFoundException|NoSuchMethodException|MissingResourceException`，输出 `[HINT-MISSING] <fqcn>` 行。补 hint 后重跑直至清零。

**CI 集成**：`.github/workflows/native-test.yml` 用 `graalvm/setup-graalvm@v1` action 安装 GraalVM 21 community → 调 `bash scripts/native-test.sh`；初版标 `continue-on-error: true`，pass rate ≥ 90% 持续 2 周后移除。





### 集群管理（migrate-console-region-cluster）

`cn.kuship.console.modules.region` 落地集群生命周期 / License / 团队-集群 / 镜像仓库 共 ~25 endpoint。

**Controllers**：
- `EnterpriseRegionsController` — `/console/enterprise/{enterprise_id}/regions` CRUD（添加/列表/详情/修改/删除）
- `TeamRegionController` — `/console/teams/{team_name}/region/{query,unopen,POST}`
- `RegionLicenseController` — `/console/enterprise/{eid}/{licenses,regions/{r}/license/{cluster-id,activate,status}}`
- `RegionQueryController` — `/console/regions`、`/console/teams/{t}/regions/{r}/{publickey,features}`、`/console/teams/{t}/protocols`
- `ClusterNamespacesController` — `/console/teams/cluster/namespaces`、`/console/enterprise/{eid}/regions/{rid}/{namespace,resource,tenants,tenants/{tn}/limit}`
- `HubRegistryController` — `/console/hub/registry`（平台级，sys_admin 才可写）
- `TeamRegistryAuthController` — `/console/teams/{t}/registry/auth`（团队级，`@RequirePerm("team_registry_auth")`）

**JPA Entity**（`modules/region/entity/`）：
- `RegionInfo` —— `region_info` 表（21 列），业务层读写；与 `infrastructure/region/repository/RegionInfoRepository`（JdbcTemplate 只读，给 `RegionClientFactory` 装配 mTLS）共存，同一表两个访问路径
- `TeamRegistryAuth` —— `team_registry_auths` 表（注意末尾 `s`，rainbond 历史拼写）；同时承载平台级（hub）和团队级凭据，通过 `tenant_id="" + region_name=""` 区分平台级

**Token 解析**：`RegionService.parseToken` 用 snakeyaml 复刻 rainbond `parse_token` —— 接受 kubectl-format YAML（含 `ca.pem` / `client.pem` / `client.key.pem` / `apiAddress` / `websocketAddress` / `defaultDomainSuffix` / `defaultTCPHost` 7 个字段），错误消息中文化对齐 rainbond。

**删除集群强制 evict client cache**：`RegionService.deleteRegion` 在 entity 删除后显式调 `RegionClientFactory.evict(enterpriseId, regionName)`，否则缓存里的旧 RestClient 仍可用于已删 region。

**14 接口骨架进度**：本 change 完成 `ClusterOperations` 8 method（getClusterId / activateLicense / getLicenseStatus / getRegionFeatures / getRegionNamespaces / getRegionResources / setTenantLimit / listTenantsInRegion）；其余继续等待业务 change 落地。

### Region API client（调用 Rainbond Go 集群）

业务 service 调用 region API 通过 14 个资源域接口（位于 `cn.kuship.console.infrastructure.region.api`）：

| 接口 | 实现 change | 说明 |
|------|-------------|------|
| `TenantOperations` | **本 change（已实现 5 method 示范）** | tenant CRUD / publickey / resources / labels |
| `ServiceOperations` | `migrate-console-app-create` | service CRUD / build / code_check / language |
| `ServiceDependencyOperations` | `migrate-console-application-core` | 服务依赖 |
| `ServiceEnvOperations` | `migrate-console-application-core` | 环境变量 |
| `ServicePortOperations` | `migrate-console-application-core` | 端口管理 |
| `ServiceVolumeOperations` | `migrate-console-application-core` | 存储卷 |
| `ServiceProbeOperations` | `migrate-console-application-core` | 健康探针 |
| `ServiceLifecycleOperations` | `migrate-console-app-runtime` | 启停/重启/扩缩容 |
| `ServiceStatusOperations` | `migrate-console-app-runtime` | 状态/Pod 信息 |
| `ServiceLogOperations` | `migrate-console-app-runtime` | 日志（WS 单独） |
| `EventOperations` | `migrate-console-app-runtime` | 事件 |
| `HelmOperations` | `migrate-console-app-market` | Helm chart / app |
| `GatewayOperations` | `migrate-console-application-core` / `migrate-console-region-cluster` | 证书/ingress |
| `ClusterOperations` | `migrate-console-region-cluster` | 集群元信息/节点 |

**注入示例**：
```java
@Service
public class MyBusinessService {
    private final TenantOperations tenantOps;
    public MyBusinessService(TenantOperations tenantOps) { this.tenantOps = tenantOps; }

    public void provisionTeam(String regionName, String entId, ...) {
        tenantOps.createTenant(regionName, entId, new CreateTenantReq(...));
    }
}
```

**未实现 method**：14 个接口的 method 默认抛 `UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-* change")`。后续业务 change 落地时新建 `@Service @Primary` 实现 bean 替换默认占位 bean。

**异常族**（13 个，均位于 `cn.kuship.console.infrastructure.region.exception`）：
- `RegionApiException`（根，业务码透传）
- `RegionApiFrequentException`（429 频率限制）
- `RegionApiSocketException`（503 网络不可达）
- `InvalidLicenseException`（10400 集群授权）
- `ClusterLackOfMemoryException` / `TenantLackOfMemoryException` / `TenantLackOfCpuException` / `TenantQuotaCpuLackException` / `TenantQuotaMemoryLackException` / `ClusterAuthLackOfMemoryException` / `ClusterAuthLackOfNodeException` / `ClusterAuthLackOfLicenseException` / `ClusterAuthLackOfLicenseExpireException`（412 资源/授权不足）
- 全部由 `GlobalExceptionHandler` 自动映射为 general_message 形状响应

**mTLS 配置**：
- `region_info` 表的 `ssl_ca_cert` / `cert_file` / `key_file` 字段支持「PEM 内联文本」与「文件路径」两种形式（与 rainbond-console Python 端一致）；内联 PEM 在内存构造 KeyStore，**不落盘**
- `kuship.region.ssl-verify=false`（默认，与 Python `REGION_SSL_VERIFY=false` 一致）—— 生产部署必须设为 `true`
- `kuship.region.timeout-seconds=5`（connect/socket）
- 客户端按 `(enterpriseId, regionName)` 缓存（懒加载），可调 `regionClientFactory.evict()` 主动失效

**响应消息汉化**：`RegionErrorMsgEnricher` 自动处理 Helm 接管冲突 / 域名冲突 / 频繁操作短语；其他错误消息透传。**优先使用 region 自带的 `msg_show`**（Go 后端已汉化），仅缺失时由 enricher 兜底。

## 测试约定

- 集成测试用 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles({"local","contract-test"})`，连本机真实 MySQL（与 application-local.yaml 一致），不读写业务表
- `contract-test` profile 启用 `ContractDemoController`（仅 src/test）作为契约层端到端验证用 controller
- 测试断言响应形状时，必须断言 `code/msg/msg_show/data.bean/data.list` 五项
- JWT 测试通过 `JwtTokenService.encode(JwtClaims, Duration)` 构造合法/过期 token

## 关键依赖事实（Spring Boot 4 与 Jackson 3）

- **Spring Boot 4 用 Jackson 3.x**：包名是 `tools.jackson.databind.*`（不再是 `com.fasterxml.jackson.databind.*`）。注解（`@JsonProperty`/`@JsonPropertyOrder` 等）仍在 `com.fasterxml.jackson.annotation.*`
- `@WebMvcTest` 切片下不会装 `@Service`/`@Component`，遇到 SecurityConfig 引用的 filter 链路时会因依赖缺失启动失败；本项目集成测试统一用 `@SpringBootTest`
- `WebMvcTest` 类的全限定名是 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`（位于 `spring-boot-starter-webmvc-test`），与 Spring Boot 3 不同

## 迁移路线图

13 阶段路线见 OpenSpec change [`init-kuship-console`](../openspec/changes/init-kuship-console/design.md)
的"决策 8"。本 change 是后续 12 个迁移 change 的母体，所有业务 change 必须建立在这个骨架之上。

每个业务 change 落地前，请确认：
1. 是否已经走过 `migrate-console-response-contract`（全局响应/异常/JWT/TenantHeader 拦截器）
2. 涉及 Region API 调用的，必须先走完 `migrate-console-region-client`
3. 涉及 K8s 直连的（仅 rke2 模块），必须先走 `migrate-console-region-cluster`

## 与上层文档的关系

- 仓库根 [`CLAUDE.md`](../CLAUDE.md)：项目总览、目录结构
- 本文件：kuship-console 模块内部约束与开发指南
- [`README.md`](./README.md)：面向开发者的本地启动与构建命令
