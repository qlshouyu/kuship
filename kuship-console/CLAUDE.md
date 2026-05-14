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

### Helm Release 域（migrate-console-helm-release）

`cn.kuship.console.modules.team` 首次落地：rainbond-console `console/views/team_resources.py:210-291` 中 5 个 helm release view 的 9 个 HTTP 端点全部迁入 kuship-console。

**Controllers**：
- `HelmReleasesController` — `/console/teams/{team_name}/regions/{region_name}/helm/releases{,/{release_name}{,/history,/rollback}}` + `/chart-preview`，9 个 HTTP 方法（list/install/preview/getDetail/upgrade/uninstall/getHistory/rollback）

**新增 Entity**（1 张本地表 JPA 映射）：
- `TeamHelmReleaseSource`（`team_helm_release_source`，14 列，PK `ID` Integer + `(region_name, namespace, release_name)` 唯一键 + `values_yaml` TEXT）—— rainbond Django migration `0004_teamhelmreleasesource.py` + `0005_teamhelmreleasesource_values_yaml.py` 拥有 schema

**扩充 Region API**：
- `HelmOperations` 新增 8 method（基于 `migrate-console-app-market` 已有的 6 method）：getTenantHelmReleases / installTenantHelmRelease / previewTenantHelmChart / getTenantHelmReleaseDetail / upgradeTenantHelmRelease / uninstallTenantHelmRelease / getTenantHelmReleaseHistory / rollbackTenantHelmRelease，全部由 `HelmOperationsImpl`（`@Primary`）实现，转发 region 后端 `/v2/tenants/{tenant_name}/helm/*`

**业务规则迁移**（service 层 5 个 helper，对齐 Python `team_resources.py:20-161`）：
- `resolveNamespace(team_name)` — `Tenants.namespace` → `tenant_name` → 404
- `buildInstallBody(raw, ns)` — `source_type=store` 且 `repo_name` 存在时查 `helm_repo` 改为 `source_type=repo` + 注入 `repo_url/username/password`
- `enrichReleaseList / enrichReleaseDetail` — 用 `team_helm_release_source` 注入 `source_info`（store_locked / manual_select），detail 还会用本地 `values_yaml` 覆盖 region 返回的 `summary.values`
- `persistReleaseSource` — install/upgrade 成功后落库；**保留原始 `raw_body.source_type`** 而非转换后的 `repo`（与 Python 行为一致）

**写两阶段策略**：
- install/upgrade：先调 region → 再 `save_or_update`；落库失败仅 ERROR 日志（不抛给用户）
- uninstall：先调 region 释放 K8s 资源 → 再 `deleteByRegionNameAndNamespaceAndReleaseName`；删行失败仅 ERROR 日志

**测试覆盖**：`HelmReleaseServiceTest` 21 用例（纯 Mockito）+ `HelmReleaseIntegrationTest` 10 用例（@SpringBootTest + @MockitoBean HelmOperations，需要本地 MySQL）。

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

### 第三方组件运行时（migrate-console-third-party-runtime）

`cn.kuship.console.modules.thirdparty` 落地路线图 P0 #8 —— rainbond `console/views/app_create/source_outer.py:ThirdPartyAppPodsView,ThirdPartyHealthzView` 6 endpoint 全部迁入 kuship-console，承接 `migrate-console-app-create` 已落地的第三方组件创建后的运行时管理（endpoint CRUD + 健康探针配置）。

**Controllers**（2 个，6 endpoint）：
- `ThirdPartyEndpointsController` — `/console/teams/{team_name}/apps/{service_alias}/third_party/pods` GET/POST/PUT/DELETE
- `ThirdPartyHealthController` — `/console/teams/{team_name}/apps/{service_alias}/3rd-party/health` GET/PUT
- 路径段 `third_party`（下划线）与 `3rd-party`（连字符 + 数字简写）拼写不一致是 rainbond 历史遗留，本 change 严格保留 URL 不修复

**Region API**（新接口非 14 接口骨架）：
- `modules/thirdparty/api/ThirdPartyServiceOperations.java` 6 method（getEndpoints / postEndpoints / putEndpoints / deleteEndpoints / getHealth / putHealth）
- `ThirdPartyServiceOperationsImpl @Primary` 实现：
  - URL 模板 `/v2/tenants/{namespace}/services/{alias}/{endpoints|3rd-party/probe}`，`namespace` 取自 `Tenants.namespace || tenant_name`（缺失时 fallback）
  - **关键约束**：POST/PUT/DELETE endpoints 三个 method 在 RestClient 链上显式 `.header("Resource-Validation", "true")`（与 rainbond `_set_headers(token, resource_validation="true")` 一致）；GET endpoints / GET health / PUT health 不加该 header
  - DELETE with body 用 `c.method(HttpMethod.DELETE).uri(url).contentType(JSON).body(body)` 模式（Spring 6 RestClient 写法）

**业务规则**（`ThirdPartyEndpointService` facade）：
- 公共 helper `validateThirdPartyService(teamName, alias)` 先 `tenantsRepo.findByTenantName` → `serviceRepo.findByTenantIdAndServiceAlias` → 校验 `serviceSource == "third_party"`
- 不存在 → `ServiceHandleException(404, "service not found", "组件不存在")`
- 非 third_party → `ServiceHandleException(400, "service is not a third-party service", "组件不是第三方组件")`（**比 rainbond 严一档**：rainbond 端 view 未做该校验，本 change 显式拦截内部组件误调）
- region_name 来自 `service.serviceRegion`，无需 path 变量

**Body 透传规则**：
- POST/PUT endpoints body=`{"address":"<ip:port>","is_online":true}` 单条 或 `{"endpoints":[{...}]}` 批量；controller 不强 typed DTO，`@RequestBody Map<String, Object>` 透传
- DELETE endpoints body=`{"ep_id":"<id>"}`；同样 Map 透传
- PUT health body=`{"mode":"tcp","scheme":"http","path":"/","port":80,"period":30,"timeout":3}` 透传

**权限**：
- 读端点 `@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
- 写端点 `@RequirePerm(PermCode.APP_CREATE_PERMS)`（rainbond 端无独立 manage_team_app perm code，沿用 app_create_perms）

**测试覆盖**：
- 单测 `ThirdPartyServiceOperationsImplTest`（9 用例）：6 method × happy + Resource-Validation header 在/不在断言 + namespace fallback + team 不存在 + 5xx 透传
- 集成测试 `ThirdPartyRuntimeIntegrationTest`（11 用例）：endpoint CRUD 4 用例 + 批量 POST + health GET/PUT + 内部组件 400（pods + health 各一次）+ 不存在的 alias 404 + region 5xx 透传

**14 接口骨架进度不变**：本 change 新接口在 `modules/thirdparty/api/`（业务域接口路径），不影响 14 接口骨架计数。

### 集群基础信息透传（migrate-console-cluster-extras）

`cn.kuship.console.modules.region.controller.cluster` 子包落地路线图 P0 #1 热身项 ——
`ClusterOperations` 接口中既存的 5 个 default unsupported method 全部清桩，4 个新 controller 暴露给 UI。

**新增 Controllers**（4 个，5 endpoint，按 design.md 锚点）：
- `ClusterInfoController` — `GET /console/enterprise/{enterprise_id}/regions/{region_name}/info` (`@RequireEnterpriseAdmin`)
- `ClusterEventsController` — `GET /console/enterprise/{enterprise_id}/regions/{region_name}/cluster-events?...` query 透传
- `ClusterNodesController` — `GET .../nodes` 列表 + `GET .../nodes/{node_name}` 详情；后续 `migrate-console-cluster-nodes` 子 change 在同 controller **追加** action / labels / taints / container 端点，不替换 GET 路径
- `TenantResourcesController` — `GET /console/teams/{team_name}/resources?region_name=&enterprise_id=`（`@RequirePerm(TEAM_REGION_DESCRIBE)`）；接管原 `MiscOtherController.teamResources` 占位（已删除）

**ClusterOperationsImpl 5 method**（直接在原 @Primary impl 上追加，不新建文件）：
- `getResources(rn, tn, eid)` — `tenant_name` 路径段替换为 `Tenants.namespace`（缺失回退 `tenant_name`）
- `getClusterInfo(rn)` — region 端 `/v2/cluster/info` 不支持时（404）降级为读本地 `region_info` entity，不抛异常
- `getClusterEvents(rn, body)` — `Map<String, Object>` body 序列化为 URL query string（TreeMap 字典序排序，全部 URL encode；空 value 跳过）
- `getNodes(rn)` / `getNodeDetail(rn, nodeName)` — 1:1 透传；`nodeName` URL encode 防点号节点名出错

**关键决策**：
- region 路径锚点参考 design.md "Region API URL 表"；`getResources` 路径段差异（tenant_name vs namespace）与 helm-release / gateway-certificate 一致
- `getClusterEvents` 接口签名 `body` 实际是 query map（rainbond `get_cluster_resource(rn, "events", params)` 行为），不发 request body；保留接口签名不破坏 default 占位兼容性
- `/v2/cluster/info` 在 region Go 端是否真实存在需联动验证；本地降级路径用 `RegionInfoEntityRepository.findByRegionName` 输出 `region_name/region_alias/url/tcpdomain/httpdomain/status/scope/provider` 字段子集
- 与后续子 change 解耦边界：本 change 只做 region 透传不做业务聚合；business-level 富化（节点 metrics / Pod 数 / cordon/drain）由 `ClusterNodeOperations` 新接口承载，不污染 `ClusterOperations`

**Conflict 解决**：`/console/teams/{team_name}/resources` 原由 `MiscOtherController.teamResources`（misc 阶段占位，仅返本地 DB 汇总）持有；本 change 启动后路径冲突，按 design 决策删除占位，由 `TenantResourcesController` 接管 region 透传职责。响应 shape 改为 region 实时 `cpu/memory/disk` 等字段（替换 `used_memory/limit_memory/component_count` 占位 shape）。


<!-- ↓↓↓ 6 路并行 P0 子 change 整合产物（leader 合并自 6 worktree） ↓↓↓ -->

<!-- migrate-console-dependency-extras -->

> 此文件由子 change 工程师生成，供 leader 合并到 kuship-console/CLAUDE.md 的「应用与组件管理」段落。

#### 批量组件依赖与旧版卷依赖（migrate-console-dependency-extras）

`ServiceDependencyOperations` 3 个 default-unsupported method 已在 `ServiceDependencyOperationsImpl` 覆盖：

| method | HTTP | region 路径 | 说明 |
|--------|------|-------------|------|
| `addDependencies(rn, tn, alias, body)` | POST | `/v2/tenants/{ns}/services/{alias}/dependencys` | 批量添加（**`dependencys` 保留 rainbond 历史拼写**） |
| `addVolumeDependency(rn, tn, alias, body)` | POST | `/v2/tenants/{ns}/services/{alias}/volume-dependency` | 旧版挂载依赖（仅内部调用） |
| `deleteVolumeDependency(rn, tn, alias, body)` | DELETE | `/v2/tenants/{ns}/services/{alias}/volume-dependency` | 旧版删除挂载依赖（仅内部调用） |

### 新增 controller endpoint

`AppDependencyController` 追加：

- `POST /console/teams/{team_name}/apps/{service_alias}/dependency-list`（trailing slash 兼容）
  - 权限：`@RequirePerm(PermCode.APP_CREATE_PERMS)`
  - body：`{"dep_service_ids": ["id1", "id2", ...]}`
  - 委托 `AppDependencyBatchService.addBatch` 处理

### 新增 service

`AppDependencyBatchService`（`modules/application/service/`）：

- `@Transactional public Map<String, Object> addBatch(teamName, serviceAlias, body)`
- 两阶段写：
  1. 去重（已存在跳过，不报错）
  2. 循环依赖 BFS 检测（抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`）
  3. 本地批量 INSERT `tenant_service_relation`
  4. 调 region `addDependencies`（body 注入 `tenant_id = Tenants.namespace`）
  5. region 失败 → 事务自动回滚 step 3

### 关键约束

- region 路径 `dependencys` 拼写**不得修改**（rainbond region 端历史）
- `addVolumeDependency` / `deleteVolumeDependency` **无 console controller URL**（rainbond 5.0+ 前端已不直调），仅供 helm-install / app-import 子 change 内部调用
- `volume-dependency` region method 的接线由 `migrate-console-helm-install` / `migrate-console-app-import-export` 负责

<!-- migrate-console-volume-extras -->

`cn.kuship.console.modules.application`（application 模块扩展）落地"组件挂载依赖存储"（mnt）全套端点，
以及 `ServiceVolumeOperations` 接口 6 个 region API method 的完整实现。

**新增 Entity**（1 张本地表 JPA 映射）：
- `TenantServiceMountRelation`（tenant_service_mnt_relation，5 列：tenantId / serviceId / depServiceId / mntName / mntDir）

**新增 Repository**：
- `TenantServiceMountRelationRepository`：5 个 finder / deleter

**新增 Service**：
- `AppMntService`：getMounted / getUnmounted / addMnt / deleteMnt

**新增 Controller**：
- `AppMntController`：
  - `GET  /console/teams/{team_name}/apps/{service_alias}/mnt?type=mnt|unmnt`
  - `POST /console/teams/{team_name}/apps/{service_alias}/mnt`
  - `DELETE /console/teams/{team_name}/apps/{service_alias}/mnt/{dep_vol_id}`

**补全 ServiceVolumeOperationsImpl（6 个 method）**：
- `getVolumeOptions` → `GET /v2/volume-options`（无 tenant 前缀）
- `getVolumes` → `GET /v2/tenants/{t}/services/{a}/volumes`
- `getVolumeStatus` → `GET /v2/tenants/{t}/services/{a}/volumes-status`
- `getDepVolumes` → `GET /v2/tenants/{t}/services/{a}/depvolumes`
- `addDepVolumes` → `POST /v2/tenants/{t}/services/{a}/depvolumes`
- `deleteDepVolumes` → `DELETE /v2/tenants/{t}/services/{a}/depvolumes`（含 JSON body）

**挂载写策略**：
- `addMnt`：组件 `create_status=complete` 时调 region addDepVolumes；region 失败仅记 WARN，本地 mnt_relation 仍写入
- `deleteMnt`：region deleteDepVolumes → 404 时忽略，直接删本地行
- config-file volume 类型挂载返回 400（依赖 service_config_file 表，本 change 不支持）

**未挂载过滤规则**（对齐 Python `mnt_service.get_service_unmount_volume_list`）：
- 排除当前组件自身存储
- 仅保留 access_mode=RWX
- 排除 config-file / local-path 类型
- 排除有状态组件（extend_method in {state, singleton}）的存储
- 排除已在 mnt_relation 中的存储

<!-- migrate-console-gateway-certificate -->

> 本文件补充 `kuship-console/CLAUDE.md` 的"网关证书 CRUD"子域说明。
> 不修改 `kuship-console/CLAUDE.md`，内容由归档流程合并。

#### 网关证书 CRUD（migrate-console-gateway-certificate）

`cn.kuship.console.modules.gateway.cert` 落地网关证书 CRUD 与域名校验能力，
对齐 rainbond `console/views/app_config/app_domain.py:61-298,490-498`。

### Controllers（4 个，~7 endpoint）

| Controller | 路径 | 方法 | rainbond 锚点 |
|---|---|---|---|
| `TenantCertificateController` | `/console/teams/{team_name}/certificates` | GET / POST | `urls.py:630 TenantCertificateView` |
| `TenantCertificateManageController` | `/console/teams/{team_name}/certificates/{certificate_id}` | PUT / DELETE | `urls.py:631-632 TenantCertificateManageView` |
| `CalibrationCertificateController` | `/console/teams/{team_name}/calibration_certificate` | POST | `urls.py:655 CalibrationCertificate` |
| `EnterpriseCertificateController` | `/console/enterprise/team/certificate` | POST（占位） | `urls.py:932 CertificateView` |

- `TenantCertificateManageController` 仅 PUT / DELETE，无 GET（对齐 rainbond 无 get 方法行为）
- `EnterpriseCertificateController` 永远返回 `{is_certificate: 1}`，不调 region，预留给 enterprise 子 change

### service_domain_certificate 表说明

8 列映射（`ServiceDomainCertificate` entity）：

| 列名 | 类型 | 说明 |
|---|---|---|
| `ID` | INT（PK）| 大写，JPA `@Column(name="ID")` |
| `tenant_id` | varchar(32) | 租户 UUID |
| `certificate_id` | varchar(50) | console 生成的 32-char UUID |
| `private_key` | longtext | **原文 PEM，不编码** |
| `certificate` | longtext | **PEM 经 Base64 编码后存储** |
| `certificate_type` | longtext | 类型字符串，`"gateway"` 触发双写 |
| `create_time` | datetime | 创建时间 |
| `alias` | varchar(64) | 租户范围内唯一，≤64 字符 |

### Base64 编码约束（决策 1）

与 rainbond Python 端互操作的硬约束：

- **写入**：`certificate` 列 = `Base64.getEncoder().encodeToString(pemBytes)`
- **读取**：`Base64.getDecoder().decode(cert.getCertificate())` → PEM 明文
- `private_key` 列**直存原文 PEM**（不编码），与 rainbond 端跨服务读写兼容
- 日志和异常消息中**不**暴露 `private_key` / `certificate` 全文（安全约束）

### gateway 类型双写顺序（决策 5）

与 rainbond Python 端 **相反**（kuship 改进）：

| 操作 | kuship 顺序 | 说明 |
|---|---|---|
| 创建 | 先本地 INSERT（`@Transactional`）后 region `createCertificate` | region 失败 → 事务回滚本地行，无孤儿数据 |
| 更新 | 先 region → 后本地 UPDATE | 同事务，region 失败回滚 |
| 删除 | 先 region `deleteCertificate` → 后本地 DELETE | 释放 K8s GatewayTLS 资源后才删本地行 |

类型切换规则（`updateCertificate`）：
- 普通 → gateway：调 region `createCertificate`
- gateway → 普通：调 region `deleteCertificate`
- gateway → gateway：调 region `updateCertificate`

### CertificateAnalyzer（X.509 工具）

纯 JDK + BouncyCastle（项目已有 `bcprov-jdk18on` / `bcpkix-jdk18on`）：

- PKCS#8 私钥：`KeyFactory.getInstance("RSA"/"EC").generatePrivate(PKCS8EncodedKeySpec)`
- PKCS#1 RSA 私钥：BouncyCastle `RSAPrivateKey.getInstance(der)` → `RSAPrivateKeySpec`
- RSA 公私钥匹配：比对 `RSAPublicKey.getModulus()` 与 `RSAPrivateKey.getModulus()`
- ECDSA 公私钥匹配：BouncyCastle 标量乘法 `G × s` 与证书公钥点比对
- SAN 提取：`cert.getSubjectAlternativeNames()` type=2（dNSName）/ type=7（iPAddress）

### GatewayOperations 5 method 实现

`GatewayOperationsImpl`（已 `@Primary`）新增 5 个 override：

| method | HTTP | 路径 |
|---|---|---|
| `getCertificate` | GET | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `createCertificate` | POST | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `updateCertificate` | PUT | `/v2/tenants/{tenant_name}/gateway-certificate` |
| `deleteCertificate` | DELETE | `/v2/tenants/{tenant_name}/gateway-certificate?namespace=&name=` |
| `updateIngressesByCertificate` | PUT | `/v2/tenants/{region_tenant_name}/gateway/certificate`（namespace 路径段） |

注意 `updateIngressesByCertificate` 路径段用 `tenant.namespace`（region_tenant_name），
从 `TenantsRepository.findByTenantName(tenantName).getNamespace()` 取。

### 测试覆盖

| 测试类 | 类型 | 用例数 |
|---|---|---|
| `CertificateAnalyzerTest` | 纯单测 | 9 |
| `CertGenerator` | 测试夹具（无测试方法） | — |
| `CertificateServiceTest` | Mockito 单测 | 9 |
| `GatewayOperationsImplTest` | MockRestServiceServer 单测 | 6 |
| 集成测试（6.1-6.3） | 需用户联动（真实 DB） | 待运行 |

所有 24 个单测均通过（`mvn -DskipTests package` + `mvn -Dtest=... test`）。

<!-- migrate-console-cluster-nodes -->

`cn.kuship.console.modules.region.controller.cluster` 落地 K8s 集群节点查询与操作的 7 个端点，
对齐 rainbond-console `GetNodes / GetNode / NodeAction / NodeLabelsOperate / NodeTaintOperate`。

#### 端点表

| 方法 | 路径 | Python 锚点 | 鉴权 |
|------|------|------------|------|
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes` | `GetNodes.get` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}` | `GetNode.get` | `@RequireEnterpriseAdmin` |
| POST | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action` | `NodeAction.post` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` | `NodeLabelsOperate.get` | `@RequireEnterpriseAdmin` |
| PUT  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` | `NodeLabelsOperate.put` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` | `NodeTaintOperate.get` | `@RequireEnterpriseAdmin` |
| PUT  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` | `NodeTaintOperate.put` | `@RequireEnterpriseAdmin` |

#### 模块结构（新增/修改）

```
modules/region/
├── controller/cluster/
│   └── ClusterNodesController.java    [新建] 7 个端点
└── service/
    └── ClusterNodeService.java        [新建] 节点状态转换 + action 白名单校验

infrastructure/region/api/
└── ClusterOperations.java             [修改] 追加 7 个节点 method 声明

modules/region/api/
└── ClusterOperationsImpl.java         [修改] 追加 7 个节点 method 实现（未动已有 8 个）
```

#### 节点状态计算（对齐 Python）

```java
// conditions 数组推导：type=Ready && status=True → "Ready"；否则 "NotReady"
// unschedulable=true → 追加 ",SchedulingDisabled"
```

#### NodeAction 白名单

```java
Set.of("unschedulable", "reschedulable", "down", "up", "evict")
```

未知 action → `ServiceHandleException(400, ...)` → HTTP 400

#### Region API 路径

```
GET  /v2/cluster/nodes
GET  /v2/cluster/nodes/{node_name}/detail
POST /v2/cluster/nodes/{node_name}/action/{action}
GET  /v2/cluster/nodes/{node_name}/labels
PUT  /v2/cluster/nodes/{node_name}/labels
GET  /v2/cluster/nodes/{node_name}/taints
PUT  /v2/cluster/nodes/{node_name}/taints
```

#### 响应格式

- `GET /nodes`：`bean={cluster_role_count}，list=[节点列表]`（对齐 Python `bean=cluster_role_count, list=nodes`）
- `GET /nodes/{node_name}`：`bean={节点详情 14 个字段}`
- `POST .../action`：`bean={}`
- `GET/PUT .../labels`：`bean={labels:{...}}`（透传 region bean）
- `GET/PUT .../taints`：`list=[{key,effect,...}]`（透传 region list）

#### 幂等性说明

- `cordon（unschedulable）`：幂等，已 cordon 的节点再次 cordon region 返回 200
- `evict`：幂等，region API 自身保证幂等
- 危险操作（drain/evict）权限：严格要求 `@RequireEnterpriseAdmin`，无团队内成员权限可绕过

#### ClusterOperations 接口新增 method（不影响已有 8 个 method）

```java
// interface 追加（NODES_CHANGE = "migrate-console-cluster-nodes"）
Map<String, Object> getClusterNodes(String regionName, String enterpriseId);
Map<String, Object> getNodeDetail(String regionName, String enterpriseId, String nodeName);
Map<String, Object> operateNodeAction(String regionName, String enterpriseId, String nodeName, String action);
Map<String, Object> getNodeLabels(String regionName, String enterpriseId, String nodeName);
Map<String, Object> updateNodeLabels(String regionName, String enterpriseId, String nodeName, Map<String, Object> labels);
List<Object> getNodeTaints(String regionName, String enterpriseId, String nodeName);
List<Object> updateNodeTaints(String regionName, String enterpriseId, String nodeName, List<Object> taints);
```

#### 测试

- 单测：`ClusterOperationsNodeTest`（MockRestServiceServer，8 个 case）
- 集成测试：`ClusterNodesIntegrationTest`（@SpringBootTest + @MockitoBean，13 个 case）
  - 注意：mock 配置在 `@BeforeEach` 中（因为 @MockitoBean 每次测试方法后 reset）

<!-- migrate-console-resource-center -->

`cn.kuship.console.modules.region.resource` 落地"命名空间资源管理 / Helm Release 生命周期 / 资源中心工作负载/Pod/事件/日志" 共 20 个端点。

**子域结构**：
```
modules/region/resource/
├── api/ResourceCenterOperationsImpl.java   # @Primary 实现（委托 RegionClientFactory）
├── controller/
│   ├── NsResourceController.java          # NS 资源 CRUD（6 端点）
│   ├── TeamComponentsController.java      # 团队组件列表（1 端点，纯本地）
│   ├── HelmReleaseController.java         # Helm Release CRUD + 历史 + 回滚 + 预览（9 端点）
│   ├── ResourceCenterController.java      # 工作负载/Pod/事件/日志 SSE（4 端点）
│   └── ResourceCenterWsInfoController.java # WsInfo（1 端点）
├── entity/TeamHelmReleaseSource.java      # team_helm_release_source 表（16 列）
├── repository/TeamHelmReleaseSourceRepository.java
└── service/HelmReleaseSourceService.java  # enrich list/detail + saveOrUpdate + deleteByRelease
```

**新增 region API 接口**：`infrastructure/region/api/ResourceCenterOperations`（含 18 个 method）
+ `ResourceCenterOperationsDefaultImpl`（占位）+ `ResourceCenterOperationsImpl`（@Primary 实现）。

**辅助表 team_helm_release_source**：
- 记录每次 helm install/upgrade 的来源信息（source_type / repo_name / chart_name 等）
- 唯一约束 `(region_name, namespace, release_name)`
- 安装时 upsert，卸载时 delete；并发冲突由 catch DataIntegrityViolationException + retry 兜底

**store→repo 来源转换**（build_helm_install_body）：
- 对齐 rainbond Python `build_helm_install_body`
- `source_type=store` 时从 `helm_repo` 表查 `repo_url/username` 替换 body；`password` 不透传

**NS 资源 POST/PUT Content-Type 透传**：
- controller 接收 `@RequestBody byte[]`（`consumes = "*/*"`），读取 `request.getContentType()` 传给 ops
- 支持 `application/yaml` / `application/json` / 任意自定义类型

**Pod 日志 SSE**：
- `@SkipResponseWrapper` 跳过 GeneralMessageResponseBodyAdvice
- `StreamingResponseBody` 先发 `: heartbeat\n\n` 帧，再 loop 读 InputStream chunk 写出
- Content-Type: text/event-stream + Cache-Control: no-cache

**WsInfo event_websocket_url 逻辑**：
- 从 `region_info.wsurl` 取值，拼 `/event_log` 后缀
- wsurl 为空或 `auto` 时 fallback：`ws://<Host-header-host>:6060/event_log`

**测试**：
- `ResourceCenterIntegrationTest`（`@SpringBootTest + @MockitoBean ResourceCenterOperations + 真实 DB seed`）：验证 8 个端点 200 / source 持久化 / WsInfo 格式
- `HelmReleaseSourceServiceTest`（Mockito 单元测试）：验证 saveOrUpdate 去重逻辑 / legacy 默认 / listSourceInfoByReleases key 格式

<!-- migrate-console-gateway-domain -->

#### Controller 清单（15 个，落地到 `modules/gateway/controller/`）

| Controller | 路径 | 方法 |
|---|---|---|
| `ServiceDomainController` | `/console/teams/{team_name}/apps/{service_alias}/domain` | GET/POST/DELETE |
| `HttpStrategyController` | `/console/teams/{team_name}/httpdomain` | GET/POST/PUT/DELETE |
| `DomainController` | `/console/teams/{team_name}/domain` | GET |
| `DomainQueryController` | `/console/teams/{team_name}/domain/query` | GET |
| `ServiceTcpDomainController` | `/console/teams/{team_name}/tcpdomain` | GET/POST/PUT/DELETE |
| `ServiceTcpDomainQueryController` | `/console/teams/{team_name}/tcpdomain/query` | GET |
| `AppServiceDomainQueryController` | `/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/domain` | GET |
| `AppServiceTcpDomainQueryController` | `/console/enterprise/{enterprise_id}/team/{team_name}/app/{app_id}/tcpdomain` | GET |
| `GatewayCustomConfigurationController` | `/console/teams/{team_name}/domain/{rule_id}/put_gateway` | GET/PUT |
| `GetPortController` | `/console/teams/{team_name}/domain/get_port` | GET |
| `GetSeniorUrlController` | `/console/teams/{team_name}/domain/get_senior_url` | GET |
| `GatewayRouteController` | `/console/teams/{team_name}/gateway-http-route` | GET/POST/PUT/DELETE |
| `GatewayRouteBatchController` | `/console/teams/{team_name}/batch-gateway-http-route` | GET |
| `AppApiGatewayController` | `/api-gateway/v1/{tenant_name}/**` | GET/POST/PUT/DELETE |
| `AppApiGatewayConvertController` | `/api-gateway/convert` | POST |

#### gateway_custom_configuration 表说明

- `ID` 自增主键
- `rule_id` VARCHAR(128) UNIQUE — 对应 HTTP 规则 ID（http_rule_id）
- `value` LONGTEXT — JSON 序列化的高级路由参数（set_headers / connection_timeout / proxy_buffering 等 5.1+ 字段）
- 业务层通过 `GatewayCustomConfigurationService.getValue/setValue` 序列化/反序列化

#### API Gateway 透传 SecurityConfig 配置

`/api-gateway/v1/**` 与 `/api-gateway/convert` 在 `SecurityConfig` 中标记为 `authenticated`（需要 JWT），不 `permitAll`。
已在 `SecurityConfig.securityFilterChain` 的 `authorizeHttpRequests` 链显式添加。

#### 两阶段写策略

- **HTTP bind**：`@Transactional` 内 INSERT → region bindHttpDomain；region 失败 → 事务回滚（本地 INSERT 撤销）
- **HTTP unbind**：先 region deleteHttpDomain → 成功后本地 DELETE；region 失败抛异常，本地不删
- **TCP bind**：同 HTTP bind 策略
- **高级配置 setValue**：先 region upgradeConfiguration → 成功后本地 INSERT/UPDATE；region 失败不写本地

#### Entity 扩字段情况

- `ServiceDomain`：19 列全字段（含 http_rule_id UNIQUE、domain_heander 保留历史拼写）
- `ServiceTcpDomain`：14 列全字段（含 tcp_rule_id UNIQUE）
- `GatewayCustomConfigure`（新增）：3 列

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
| `ClusterOperations` | `migrate-console-region-cluster` (8) + `migrate-console-cluster-extras` (5) | 集群元信息/节点（13/13 完成） |

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

### KubeBlocks 数据库托管（migrate-console-kubeblocks）

`cn.kuship.console.modules.misc.kubeblocks` 落地路线图 P1 #1 —— `KubeBlocksController` 8 个 stub endpoint 全部接线 + 5 个新 HTTP method（PUT detail / POST manualBackup / DELETE deleteBackups / PUT updateBackupConfig / POST updateClusterParameters），共 12 个真实 endpoint。

**新接口（业务自治，非 14 接口骨架）**：
- `cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations`（13 method）
- `KubeBlocksOperationsDefaultImpl`（throw UnsupportedOperationException 占位）
- `KubeBlocksOperationsImpl`（`@Primary @Service`）—— 调 `clientFactory.getClient(rn, "")` + `RegionApiResponseProcessor`

**Region URL 表（13 method）**：

| method | HTTP | 路径 |
|---|---|---|
| listSupportedDatabases / listStorageClasses / listBackupRepos | GET | `/v2/cluster/kubeblocks/{supported-databases\|storage-classes\|backup-repos}` |
| getClusterDetail / listClusterParameters / listClusterBackups / getClusterPodDetail | GET | `/v2/cluster/kubeblocks/clusters/{service_id}{/parameters\|/backups\|/pods/{pod_name}/details}` |
| createCluster / expansionCluster / deleteCluster | POST/PUT/DELETE | `/v2/cluster/kubeblocks/clusters[/{service_id}]` |
| createManualBackup / deleteClusterBackups | POST/DELETE | `/v2/cluster/kubeblocks/clusters/{service_id}/backups` |
| updateBackupConfig | PUT | `/v2/cluster/kubeblocks/clusters/{service_id}/backup-schedules` |
| updateClusterParameters | POST | `/v2/cluster/kubeblocks/clusters/{service_id}/parameters` |

**Controller 路径前缀**：
- `/console/teams/{team_name}/regions/{region_name}/kubeblocks/{supported_databases\|storage_classes\|backup_repos}`（snake_case 历史拼写）
- `/console/teams/{team_name}/apps/{service_alias}/kubeblocks/{detail\|backup-config\|backups\|parameters\|restore}`

**关键决策**：
- 决策 4：`createCluster` / `deleteCluster` 仅落地 Operations method，**不暴露独立 controller endpoint**（rainbond 实际通过 `KubeBlocksComponentCreateView` 复合流程，留给 `add-kubeblocks-create-flow` hardening）
- 决策 6：删除 `GET /backup-config`（rainbond 端无此 GET，UI 改用 `getDetail` bean 的 `backup_config` 字段）
- 决策 7：保留 `restore` endpoint stub（INFO 日志 + TODO 注释指向 `add-kubeblocks-restore`）；`add-kubeblocks-restore` / `add-kubeblocks-cluster-events` / `add-kubeblocks-cluster-actions` / `add-kubeblocks-connect-info` 4 个 hardening change 在 `KubeBlocksOperations` 同接口上扩展 method

**鉴权**：region-level GET 用 `@RequirePerm(TEAM_REGION_DESCRIBE)`；component-level 读用 `@RequirePerm(APP_OVERVIEW_DESCRIBE)`、写用 `@RequirePerm(APP_CREATE_PERMS)`。`PermCode` 实际包路径是 `cn.kuship.console.modules.account.perm.PermCode`（design.md 决策 5 笔误"`.constant.`"已修正）

**14 接口骨架进度**：本 change 不影响 14 接口骨架计数，新接口位于 `modules/misc/kubeblocks/api/`

### 应用分享 6-step 状态机 region 接线（migrate-console-app-share）

`cn.kuship.console.modules.appmarket.share.api` 与 `modules.plugin.team.controller` 落地路线图 P1 #2 —— `ServiceShareController` / `PluginShareController` 内部 region 调用接线 + 2 个新 controller endpoint。

**新接口**：`cn.kuship.console.modules.appmarket.share.api.ShareOperations`（7 method）+ `ShareOperationsImpl(@Primary)`

**Region URL 表（7 method）**：

| method | HTTP | 路径 | 锚点 |
|---|---|---|---|
| shareCloudService | POST | `/v2/tenants/{ns}/cloud-share` | `regionapi.py:975` |
| shareService / getShareServiceResult | POST/GET | `/v2/tenants/{ns}/services/{alias}/share[/{region_share_id}]` | `regionapi.py:987,996` |
| sharePlugin / getSharePluginResult | POST/GET | `/v2/tenants/{ns}/plugins/{plugin_id}/share[/{region_share_id}]` | `regionapi.py:1006,1015` |
| getServicePublishStatus | GET | `/v2/builder/publish/service/{service_key}/version/{app_version}`（**唯一不带 namespace**） | `regionapi.py:1331` |
| listAppReleases | GET | `/v2/tenants/{ns}/apps/{region_app_id}/releases` | `regionapi.py:2389` |

**6-step 状态机 region 注入点**：

| step | endpoint | region method |
|---|---|---|
| 0-2 | record / info | （无 region） |
| 3 | POST `/share/{share_id}/events/{event_id}[/plugin]` | `shareService` / `sharePlugin` |
| 4 | GET `/share/{share_id}/events/{event_id}/status`（轮询，本 change 新增） | `getShareServiceResult` / `getSharePluginResult` |
| 5 | POST `/share/{share_id}/complete` | （无 region，仅校验全部 event_status=success） |

**新增 controller endpoint**：
- `ServiceShareController.eventStatus` GET `/share/{share_id}/events/{event_id}/status`
- `PluginShareController.eventStatus` GET `/plugin-share/{share_id}/events/{event_id}/status`
- `ServicePublishStatusController` GET `/console/teams/{team_name}/apps/{service_alias}/publish/status?service_key=&app_version=`
- `AppReleasesController` GET `/console/teams/{team_name}/groups/{group_id}/releases`（`group_id` 是 `service_group.id` int PK；`region_app_id` 通过 `RegionAppRepository.findFirstByAppId(...)` 反查；缺失返 200 + 空 list）

**事务边界**：`addEvent` 在 `@Transactional` 内，本地 INSERT event 行 → 调 region `shareService` → region 失败 Spring 自动回滚（删除已 INSERT event 行）；`event.regionShareId = bean.share_id`、`event.eventStatus = "start"`。`complete` 不调 region（rainbond fire-and-forget 模式）

**新增 finder（共 3 个 repo 扩展）**：
- `ServiceShareRecordEventRepository.findByRecordIdAndEventId`
- `PluginShareRecordEventRepository.findByRecordIdAndEventId`
- `RegionAppRepository.findFirstByAppId`

**决策 2 边界**：`shareCloudService` 接口保留但 controller **不暴露**（UI v3.5+ 已移除入口，留给后续 marketplace OAuth 子 change 复用）；与 `migrate-console-app-import-export` 的 app_template 序列化解耦（本 change 不实现序列化）

### 组件监控指标透传（migrate-console-monitor-extras）

`cn.kuship.console.modules.appruntime` 落地路线图 P1 #3 —— `MonitorOperations` 既有 4 method 不动，扩 4 个 default unsupported method 由 `MonitorOperationsImpl @Primary` 实现；`AppMonitorController` 追加 3 个新 endpoint（`metrics` / `sortDomainQuery` / `sortServiceQuery`）。

**新增 region method（接口暴露 4/8，加上既有 4 个共 8）**：

| method | URL | 锚点 |
|---|---|---|
| getMonitorMetrics(rn, tenantId, target, appId, componentId) | `/v2/monitor/metrics?target=&tenant=&app=&component=`（**全局端点，不带 tenant 路径**） | `regionapi.py:2356` |
| getResourceCenterEvents | `/v2/tenants/{tenantName}/resource-center/events?<sorted query>`（TreeMap 字典序 + URL encode） | `regionapi.py:3830` |
| queryDomainAccess | `/api/v1/query?<query string>`（PromQL host 维度聚合） | `regionapi.py:1322` |
| queryServiceAccess | `/api/v1/query?<query string>`（PromQL service 维度聚合） | `regionapi.py:1313` |

**新增 controller endpoint（3 个，路径段保留 rainbond 单数 `/region/`）**：
- `metrics` GET `/console/teams/{team_name}/apps/{service_alias}/metrics` —— controller 拿 `tenant.tenantId` + `service.serviceId` 调 `getMonitorMetrics(rn, tid, "component", "", sid)`
- `sortDomainQuery` GET `/console/teams/{team_name}/region/{region_name}/sort_domain/query?page=&page_size=&repo=` —— PromQL `sort_desc(sum(ceil(increase(gateway_requests{namespace="<tid>"}[1h]))) by (host))`，客户端分页 + 累计 `total_traffic`，响应用 `GeneralMessage.okWithExtras(bean, paged, null)`（避免 advice 嵌套包装）
- `sortServiceQuery` GET `/console/teams/{team_name}/region/{region_name}/sort_service/query` —— 两次 PromQL（outer `gateway_requests` by service / inner `app_request` by service_id）合并去重 top 10

**新增 entity**：`cn.kuship.console.modules.appruntime.entity.ServiceMonitor`（`tenant_service_monitor` 表）+ `ServiceMonitorRepository`。**真实 schema 仅 8 列**（`ID` / `name` / `tenant_id` / `service_id` / `path` / `port` / `service_show_name` / `interval`），**无 `create_time` 列**（design.md 决策 2 笔误已修正）；`interval` 列名用 `@Column(name = "\`interval\`")` 反引号转义

**实施期决策（路径冲突）**：design.md 决策 1 设计的 `resourceCenterEvents` endpoint（`/console/teams/{team_name}/regions/{region_name}/resource-center/events`）与既有 `ResourceCenterController`（`migrate-console-region-resource-center` 已落地）路径冲突，触发 Spring `Ambiguous mapping` 启动失败 → 删除 AppMonitorController 中的 endpoint，保留 `MonitorOperations.getResourceCenterEvents` 接口供后续 hardening 复用

**14 接口骨架进度**：`MonitorOperations` 由 4/4 → 8/8（接口扩展 4 个 default method）；entity 新增 `ServiceMonitor`（rainbond `console/models/main.py:1086-1097` 锚点）

### 构建版本与多语言版本管理（migrate-console-build-versions）

`cn.kuship.console.modules.application` 落地路线图 P1 #4（最大子 change，15 method）—— `ServiceOperations` 在 7 既有 method 之后追加 9 个 default method 声明 + 由 `ServiceOperationsImpl @Primary` 内部追加 9 个 override；新增 2 个业务自治接口；新增 3 个 controller。

**新增 region method（共 15）**：
- `ServiceOperations` +9：`getBuildVersions` / `getBuildVersionById` / `updateBuildVersion` / `deleteBuildVersion` / `getServiceDeployVersion` / `getTeamServicesDeployVersion` / `serviceSourceCheck` / `getServiceCheckInfo` / `getBuildStatus`
- `cn.kuship.console.modules.application.api.LangVersionOperations`（5 method）+ `LangVersionOperationsImpl @Primary`：getLangVersion / createLangVersion / updateLangVersion / deleteLangVersion / getCnbFrameworks
- `cn.kuship.console.modules.application.api.BatchServiceOperations`（1 method）+ `BatchServiceOperationsImpl @Primary`：batchOperationService（带 `Resource-Validation: true` header）

**新增 controller**：
- `AppVersionsController`（8 endpoint）—— `/console/teams/{team_name}/apps/{service_alias}/{build-versions[/{version_id}] \| deploy-version \| source-check[/{uuid}] \| build-status}`；DELETE 时自动从 `RequestContext.username` 注入 `operator` 字段
- `BatchDeployVersionController`（1 endpoint）—— POST `/console/teams/{team_name}/deploy-version` body `{service_ids: [...]}`，region_name 通过 `serviceRepo.findByServiceIdIn(...)` 反查或从 body 取
- `LangVersionController`（5 endpoint）—— `/console/enterprise/{enterprise_id}/regions/{region_name}/{lang-version \| cnb/frameworks}`，全部 `@RequireEnterpriseAdmin`

**实施期决策（schema 真相 + scope 推迟）**：
- §2 entity 跳过：`docker exec mysql DESC service_build_version / lang_version` 实测两表**不存在**（仅 `plugin_build_version` / `service_build_source`），design.md 决策 2 / 决策 3 假设的 21 列 / 10 列 schema 与真实数据库不符 → `ServiceBuildVersion` / `LangVersion` entity 不创建，避免 `hibernate.ddl-auto=validate` 启动失败；本地缓存留给 hardening change `add-component-list-deploy-version-cache` / `add-lang-version-cache`
- §7 改造 `AppBatchActionsController` 推迟：避免回归 lifecycle 单调度路径的风险（body 形状转换、`batch_result` 解析），独立 hardening 处理；本 change 仅落地 `BatchServiceOperations` 接口与 `@Primary` 实现，待后续接线

**14 接口骨架进度**：`ServiceOperations` 由 7/7 → 16/16；新增 2 个业务自治接口（`LangVersionOperations` / `BatchServiceOperations`）位于 `modules/application/api/`，与 `BackupOperations`（appmarket）/ `MonitorOperations`（appruntime）/ `AutoscalerOperations`（appruntime）等同级

**与 `migrate-console-maven-setting` 边界**：lang version 的所有权由本 change 持有；P2 maven-setting 子 change 仅在 maven 工具链配置上消费 lang version 数据，不重复落地

### 灰度发布 region 通信收尾（migrate-console-grayrelease-finalize）

`cn.kuship.console.modules.grayrelease.api` 落地路线图 P1 #5 —— `add-gray-release` 中 `GrayReleaseTemplateInstaller` 的 region 调用 stub 替换为真实 region 通信；`GrayReleaseService.updateGrayRatio` 双面同步（数据面 + 命令面）。

**新接口**：`cn.kuship.console.modules.grayrelease.api.GrayReleaseOperations`（3 method）+ `GrayReleaseOperationsImpl(@Primary)`

| method | HTTP | 路径 |
|---|---|---|
| createAppGrayRelease | POST | `/v2/tenants/{namespace}/apps/{regionAppId}/gray_release` |
| updateAppGrayRelease | PUT | `/v2/tenants/{namespace}/apps/{regionAppId}/gray_release` |
| operateAppGrayRelease | PUT | `/v2/tenants/{namespace}/apps/{regionAppId}/operate_gray_release?namespace=&app_id=&operation_method=`（当前已知 `operationMethod=rollback`） |

**职责分层**（与既有 `ApisixRouteWeightUpdater` 协作）：

| 职责 | 类 | 说明 |
|---|---|---|
| 数据面（流量切换） | `ApisixRouteWeightUpdater` | 调 rainbond-go core `/api-gateway/v1/.../routes/http` 改 backends weight |
| 命令面（灰度对象同步） | `GrayReleaseOperations` | 调 region `gray_release` API 同步 `desired_replicas` / `strategy` |

**`updateGrayRatio` 双面顺序**：先 `apisixUpdater.update(...)` 切流量（更紧急）→ 后 `grayReleaseOps.updateAppGrayRelease(...)` 同步 region 灰度对象（可容忍短暂滞后）

**`installGrayServiceGroup` / `uninstallGrayServiceGroup` 签名扩展**：原 `(tenantId, appId, ...)` → 新 `(regionName, tenantName, tenantId, appId, regionAppId, ...)`；`uninstallGrayServiceGroup` 进一步加 `namespace` 参数。`GrayReleaseService` 同步改造调用点；`regionAppId` 临时复用 `appId`，TODO 注释指向 `migrate-console-app-install` 引入正式 `RegionApp` 映射

**rollback 路径降级**：`uninstallGrayServiceGroup` 调 `operateAppGrayRelease(rollback)` 失败仅 WARN 不抛（与 `ApisixRouteWeightUpdater` rollback 失败对齐——record 已要写 CANCELLED，不能让用户 rollback 卡死）

**新增配置项**：`kuship.gray-release.skip-region-template-install`（默认 `false`）—— `contract-test` profile 在 `application-contract-test.yaml` 默认设 `true` 让既有 `GrayReleaseIntegrationTest` 7 用例无破跑通；新增 `GrayReleaseOperationsImplTest` 6 单测验证 region 接线

**仍 stub 范围（待 `migrate-console-app-install`）**：本地 `service_group` / `tenant_service` / `service_group_relation` 批量 INSERT 仍生成合成 id，WARN 日志文案改为 `[GrayRelease][stub] local service_group write bypassed; ... pending migrate-console-app-install`，与 region 调用 WARN 区分

**未来 hardening 路径**：本 change 落地后，新端点（`add-grayrelease-promote-endpoint` / `add-grayrelease-lifecycle-endpoints`）直接复用 `GrayReleaseOperations.operateAppGrayRelease(... operationMethod=...)`，无需重复封装 region 通信层

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

### Region API 覆盖度路线（migrate-region-coverage-roadmap）

母路线图 [`migrate-region-coverage-roadmap`](../openspec/changes/archive/2026-05-11-migrate-region-coverage-roadmap/) 把 rainbond `www/apiclient/regionapi.py` 中剩余 ~153 个 region method 拆成 18 个聚焦子 change，按 P0 / P1 / P2 三段推进。本节是这张表在本仓内的常驻引用锚点；任何新 PR 在描述里只需写「实施 §X.Y」即可定位到具体子 change。

> **状态**：2026-05-11 18 个子 change 全部 propose + apply + archive 闭环完成，母路线图同日自归档（main spec line 4753 起）。

| 优先级 | 子 change                                | 估计 method | 状态 | 备注 |
|--------|-------------------------------------------|------|------|------|
| P0 #1 | `migrate-console-cluster-extras`            | 5    | ✅ 已落地 | 集群基础信息透传（line 964 起） |
| P0 #2 | `migrate-console-gateway-domain`            | 29   | ✅ 已落地 | HTTP / TCP rule + api-gateway proxy（line 1312 起） |
| P0 #3 | `migrate-console-gateway-certificate`       | 5    | ✅ 已落地 | 网关证书 CRUD（line 1078 起） |
| P0 #4 | `migrate-console-cluster-nodes`             | 12   | ✅ 已落地 | 节点 action / label / taint（line 1171 起） |
| P0 #5 | `migrate-console-resource-center`           | 10   | ✅ 已落地 | 资源中心 events / 监控（line 1264 起） |
| P0 #6 | `migrate-console-volume-extras`             | 6    | ✅ 已落地 | 存储卷扩展（line 1033 起） |
| P0 #7 | `migrate-console-dependency-extras`         | 3    | ✅ 已落地 | 批量依赖 + 旧版卷依赖（line 996 起） |
| P0 #8 | `migrate-console-third-party-runtime`       | 6    | ✅ 已落地 | 第三方组件运行时（line 927 起） |
| P1 #1 | `migrate-console-kubeblocks`                | 13   | ✅ 已落地 | KubeBlocks 数据库托管（line 1411 起） |
| P1 #2 | `migrate-console-app-share`                 | 7    | ✅ 已落地 | 应用分享 6-step 状态机（line 1444 起） |
| P1 #3 | `migrate-console-monitor-extras`            | 6    | ✅ 已落地 | 监控指标透传（line 1484 起） |
| P1 #4 | `migrate-console-build-versions`            | 15   | ✅ 已落地 | 构建版本 + 多语言版本（line 1508 起） |
| P1 #5 | `migrate-console-grayrelease-finalize`      | 3    | ✅ 已落地 | 灰度发布 region 收尾（line 1530 起） |
| P2 #1 | `migrate-console-app-import-export`         | 22   | ✅ 已落地 | 应用 yaml import / export（line 4658 起） |
| P2 #2 | `migrate-console-governance-policy`         | 12   | ✅ 已落地 | 治理策略 + k8s 属性（line 4586 起） |
| P2 #3 | `migrate-console-maven-setting`             | 8    | ✅ 已落地 | maven 工具链配置（line 4533 起） |
| P2 #4 | `migrate-console-service-labels`            | 4    | ✅ 已落地 | 组件 label CRUD（line 4422 起） |
| P2 #5 | `migrate-console-backup-extras`             | 5    | ✅ 已落地 | backup-service 扩展（line 4472 起） |

#### 关键依赖关系

```
P0 段（骨架先确立）
  cluster-extras ──► cluster-nodes ──► service-labels (P2)
  gateway-certificate ──► gateway-domain（证书是域名绑定的先决）
  resource-center / volume-extras / dependency-extras / third-party-runtime（独立）

P1 段（业务自治域，与 P0 解耦）
  kubeblocks / app-share / monitor-extras / build-versions / grayrelease-finalize
       │
       ├── app-share 不实现 app_template 序列化 ──► 留给 P2 #1 app-import-export
       ├── build-versions lang version 由本段持有 ──► 仅供 P2 #3 maven-setting 消费
       └── grayrelease-finalize 本地 service_group 批量 INSERT 仍 stub ──► 待独立 hardening migrate-console-app-install

P2 段（增强类，可在 P0/P1 完成后并行）
  app-import-export / governance-policy / maven-setting / service-labels / backup-extras
```

#### 共享规约（所有 18 个子 change 强制遵守）

- **聚焦原则**：每个子 change SHALL 覆盖一个 region API URL 前缀的子集，方法数 ≤ 30，可在 1-2 周内闭合
- **接口位置**：14 接口骨架内的扩展放 `infrastructure/region/api/<X>Operations.java`；新业务域接口放 `modules/<domain>/api/<X>Operations.java`
- **路径回归**：controller 路径与 rainbond `console/urls/__init__.py` 严格一致，trailing slash 兼容
- **错误兜底**：region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- **不打包**：跨 capability 的重构（region client / 全局响应包装 / mTLS 优化）不放入任何子 change，单独立 hardening
- **Service Env 例外**：rainbond 历史选择本地为主 + 重启同步，本路线 SHALL NOT 迁移 `add_service_env` / `update_service_env` / `delete_service_env` 3 个 region method

#### 子 change 落地的回环约束

- 每个子 change 的 `design.md` 头部 SHALL 引用 `migrate-region-coverage-roadmap` 并标注自己在表中的位置（P0/P1/P2 + method 估计数）
- 子 change 归档时 SHALL 反向更新母路线图 `tasks.md` 对应行（标 [x]）+ 在 `kuship-console/CLAUDE.md` 添加段落落点（按上表 line 锚点）
- 实际 method 数与估计偏差 > 30% 时，子 change 的 `design.md` SHALL 解释偏差原因

## 与上层文档的关系

- 仓库根 [`CLAUDE.md`](../CLAUDE.md)：项目总览、目录结构
- 本文件：kuship-console 模块内部约束与开发指南
- [`README.md`](./README.md)：面向开发者的本地启动与构建命令
