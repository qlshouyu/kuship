## 1. Schema 真相校验 + Entity

- [ ] 1.1 `docker exec kuship-mysql mysql ... DESC console_sys_config` 校验列名（已知：ID/key/type/value/desc/enable/create_time/enterprise_id 共 8 列）
- [ ] 1.2 新建 `modules/misc/config/entity/ConsoleSysConfig.java`：`@Entity` 映射 `console_sys_config`，PK `Integer ID`，`key` 列用 `@Column(name = "\`key\`")` 反引号转义（保留字），`enterpriseId` 列名 `enterprise_id`，`createTime` 列名 `create_time`，`desc` 列用 `@Column(name = "\`desc\`")` 反引号
- [ ] 1.3 新建 `ConsoleSysConfigRepository extends JpaRepository<ConsoleSysConfig, Integer>`：`Optional<ConsoleSysConfig> findByKey(String key)` + `boolean existsByKey(String key)`

## 2. 默认值常量表

- [ ] 2.1 新建 `modules/misc/config/service/PlatformConfigDefaults.java`：复刻 rainbond `cfg_keys_value`（17 个 key）+ `base_cfg_keys_value`（5 个 key），用 `record DefaultEntry(String value, String desc, boolean enable, String type)` 数据载体，`Map<String, DefaultEntry> CFG_DEFAULTS` + `Map<String, DefaultEntry> BASE_CFG_DEFAULTS` 两个常量表
- [ ] 2.2 `BASE_CFG_DEFAULTS`：`IS_PUBLIC` / `MARKET_URL`（默认 `http://api.goodrain.com:80`）/ `ENTERPRISE_CENTER_OAUTH`（占位 null）/ `VERSION`（默认 `public-cloud`）/ `IS_USER_REGISTER`（占位 false）—— 4 项依赖 env，预留 getter 接受 env 注入
- [ ] 2.3 `CFG_DEFAULTS`：17 个 key 复刻原值（含 `DOCUMENT` 默认 `{"platform_url":"https://www.rainbond.com/"}`、`ENTERPRISE_EDITION` 默认 `"true"` 等）
- [ ] 2.4 暴露 `getEffectiveBase(String key, Environment env)` 方法处理环境变量注入

## 3. PlatformConfigService

- [ ] 3.1 新建 `modules/misc/config/service/PlatformConfigService.java` 注入 `ConsoleSysConfigRepository` + `Environment`
- [ ] 3.2 实现 `Map<String,Object> initializationOrGetConfig()`：遍历 `BASE_CFG_DEFAULTS` + `CFG_DEFAULTS`，对每个 key 查表，命中则用 DB 值（type=json 时 Jackson `readValue` 解 JSON，type=string 直返字符串），未命中则 INSERT 默认值并返回；输出形状 `{ key.toLowerCase(): {"enable": bool, "value": v} }`
- [ ] 3.3 顶层平铺字段：`enterprise_id`（env `ENTERPRISE_ID` 默认 ""）/ `is_disable_logout`（env `IS_DISABLE_LOGOUT` 默认 false）/ `is_offline` / `sso_enable` / `diy`（默认 true，env `DIY=false` 才关）/ `enable_yum_oauth` / `diy_customer`（默认 `rainbond`）/ `is_delivery_version` / `portal_site` / `default_market_url`（env `DEFAULT_APP_MARKET_URL`）/ `disable_logo`（env `DISABLE_LOGO==true`）—— `IS_ENTERPRISE_EDITION` env 真时 `enterprise_edition.value="true"`、`is_saas` 仅 env `USE_SAAS` 真时输出
- [ ] 3.4 `Map<String,Object> updateConfig(String key, Object value, boolean enable)`：base_cfg 仅改 enable；cfg 改 value（json 序列化为 string 落库）+ enable；返回 `{ key.toLowerCase(): {"enable":..., "value":...} }`
- [ ] 3.5 `Map<String,Object> deleteConfig(String key)`：仅 cfg_keys 允许，重置 enable+value+desc 为默认值；返回同上形状
- [ ] 3.6 全部方法 `@Transactional`；JSON 序列化用 Spring Boot 4 自带的 Jackson 3 `tools.jackson.databind.ObjectMapper`

## 4. PlatformConfigController

- [ ] 4.1 新建 `modules/misc/config/controller/PlatformConfigController.java` `@RestController @RequestMapping(...)`
- [ ] 4.2 `@GetMapping({"/console/config/info","/console/config/info/"})`：调 `service.initializationOrGetConfig()` → 包成 `Map<String,Object> bean`；额外注入 `initialize_info: {}`（占位）；advice 自动包 general_message
- [ ] 4.3 `@PutMapping({"/console/config/info","/console/config/info/"})`：`@RequestParam String key` + `@RequestBody Map<String,Object>`；body 必含 `value` 与 `enable` 字段；调 service.updateConfig；缺 key 或 value 返 404 ApiResult
- [ ] 4.4 `@DeleteMapping(...)`：`@RequestParam String key` + body；调 service.deleteConfig；非 cfg_keys 返 404 + `"该配置不可重置"`
- [ ] 4.5 PUT/DELETE 通过 `RequestContext.sysAdmin` 校验，非 sys_admin 抛 `ServiceHandleException(403, "需要平台管理员权限")`

## 5. SecurityConfig 加白

- [ ] 5.1 修改 `config/SecurityConfig.java:97-98` 的 `requestMatchers(HttpMethod.GET, ...)` 列表，追加 `"/console/config/info", "/console/config/info/"`
- [ ] 5.2 PUT/DELETE 走 JWT 默认通道（不放 permitAll，由 controller 内 sys_admin 校验）

## 6. 编译 + 重启 + 验证

- [ ] 6.1 `cd kuship-console && mvn -DskipTests package` 编译通过（不挂掉骨架的其它测试）
- [ ] 6.2 杀掉旧 `mvn spring-boot:run` 进程；重启 `nohup mvn spring-boot:run -Dspring-boot.run.profiles=local > target/run.log 2>&1 &`
- [ ] 6.3 等启动完成后 `curl -s http://localhost:8080/console/config/info | jq .` —— 验证 200、含 22 个 key、含 11 个顶层字段、含 `code:200/msg:"query success"` 包装
- [ ] 6.4 浏览器刷新 `http://localhost:8000/user/login`，控制台无 401，登录页正常渲染（logo/title/footer/document 元素就位）
- [ ] 6.5 抽样验证 DB 持久化：直连 mysql `SELECT \`key\`,value FROM console_sys_config WHERE \`key\`='TITLE'` 与响应一致

## 7. specs

- [ ] 7.1 写 `specs/kuship-console-app/spec.md` 加 1 条 ADDED Requirement —— 站点平台配置端点
