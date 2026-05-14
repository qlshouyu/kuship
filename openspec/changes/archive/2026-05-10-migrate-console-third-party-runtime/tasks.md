# Tasks — migrate-console-third-party-runtime

## 1. 探测 region 端 6 个 URL

- [ ] 1.1 curl 验证 6 URL（POST/PUT/DELETE 带 `Resource-Validation: true` header）（**实施期推迟到 task §7 联动验证**：实施环境无在线 region 实例，按 rainbond Python `regionapi.py:1828-1893` 锚点路径直接实现，单测用 `MockRestServiceServer.expect(header("Resource-Validation","true"))` 验证 header 透传）：
  - `GET /v2/tenants/<ns>/services/<alias>/endpoints` → 200 + list
  - `POST /v2/tenants/<ns>/services/<alias>/endpoints` body=`{"address":"1.2.3.4:80","is_online":true}` → 200/201
  - `PUT /v2/tenants/<ns>/services/<alias>/endpoints` body=`{...}` → 200
  - `DELETE /v2/tenants/<ns>/services/<alias>/endpoints` body=`{"ep_id":"..."}` → 200
  - `GET /v2/tenants/<ns>/services/<alias>/3rd-party/probe` → 200 + bean
  - `PUT /v2/tenants/<ns>/services/<alias>/3rd-party/probe` body=`{...}` → 200
- [x] 1.2 响应 shape 与 header 推断写在 ThirdPartyServiceOperations.java javadoc + ThirdPartyServiceOperationsImpl 注释，与 rainbond Python `_set_headers(token, resource_validation="true")` 严格对齐

## 2. 新增 ThirdPartyServiceOperations 接口 + Impl

- [x] 2.1 新建 `modules/thirdparty/api/ThirdPartyServiceOperations.java` 6 method 接口签名（无 default 占位，新业务域接口由 `ThirdPartyServiceOperationsImpl` 直接为唯一实现）
- [x] 2.2 新建 `modules/thirdparty/api/ThirdPartyServiceOperationsImpl.java` `@Primary @Service`：
  - 注入 `RegionClientFactory` + `RegionApiResponseProcessor` + `TenantsRepository`
- [x] 2.3 实现 6 method：
  - URL 拼接含 `namespace`（tenant.namespace || tenant_name，`resolveNamespace` helper，团队不存在抛 404）
  - POST/PUT/DELETE endpoints 三个 method 在 `RestClient` 链式调用中显式加 `.header("Resource-Validation", "true")`
  - GET endpoints / GET health / PUT health 不加该 header（rainbond Python `_set_headers` 对 health probe 也未传 resource_validation）
  - DELETE with body 用 `c.method(HttpMethod.DELETE).uri(url).contentType(JSON).body(body)` 模式（Spring 6 RestClient 写法）
- [x] 2.4 单测 `ThirdPartyServiceOperationsImplTest` 9 用例全过：6 method × happy + Resource-Validation header 在/不在断言（`header("Resource-Validation","true")` / `headerDoesNotExist`）+ namespace fallback + team 不存在 404 + 5xx 透传

## 3. 新建 ThirdPartyEndpointService

- [x] 3.1 新建 `modules/thirdparty/service/ThirdPartyEndpointService.java`
- [x] 3.2 公共 helper `validateThirdPartyService(tenantId, alias)`：
  - 取 `tenant_service` 行
  - 不存在 → 抛 `ServiceHandleException(404, "service not found", "组件不存在")`
  - `service.serviceSource != "third_party"`（或对应字段名）→ 抛 `(400, "service is not a third-party service", "组件不是第三方组件")`
- [x] 3.3 暴露 6 个 facade method（getEndpoints / postEndpoints / .../  putHealth），每个先调 `validateThirdPartyService`，再调 region

## 4. 新建 controller

- [x] 4.1 新建 `modules/thirdparty/controller/ThirdPartyEndpointsController.java`：
  - `@GetMapping({"/console/teams/{team_name}/apps/{service_alias}/third_party/pods", ".../"})`
  - `@PostMapping(...)` body 透传
  - `@PutMapping(...)` body 透传
  - `@DeleteMapping(...)` `@RequestBody Map<String, Object>` body 透传
  - 读 `@RequirePerm("describe_team_app")`
  - 写 `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`
- [x] 4.2 新建 `ThirdPartyHealthController.java`：
  - `@GetMapping({"/console/teams/{team_name}/apps/{service_alias}/3rd-party/health", ".../"})`
  - `@PutMapping(...)` body 透传
  - 同样权限规约

## 5. 集成测试

实际合并到一个 `ThirdPartyRuntimeIntegrationTest` 类（@SpringBootTest + @MockitoBean ThirdPartyServiceOperations + 真实 DB seed），保留 controller + service 的真实校验链。11 用例全过：

- [x] 5.1 endpoints 5 用例：GET happy / POST 单条 / POST 批量 / PUT 更新 is_online / DELETE body=`{"ep_id"}`
- [x] 5.2 health 2 用例：GET happy（断言 mode/port 透传）/ PUT body 透传
- [x] 5.3 校验链 4 用例：内部组件（serviceSource=docker_run）调 endpoints → 400 + msg_show=组件不是第三方组件；同样校验 health PUT；不存在的 alias → 404 + msg_show=组件不存在；region 5xx 透传 → 503 + msg_show=集群不可用
- [ ] 5.4 无权限 403 用例：本 change 用户全部 sys_admin（绕过 PermAspect），实际 PermAspect 单测在 account 模块覆盖，本 change 不重复（与 cluster-extras / helm-release 等同模式）
- [x] 5.5 单测 `ThirdPartyServiceOperationsImplTest` 9 用例：见 §2.4

## 6. 文档与归档

- [x] 6.1 更新 `kuship-console/CLAUDE.md` 在"第三方组件运行时（migrate-console-third-party-runtime）"新段：列 2 controller、6 region method、Resource-Validation header 透传约束、组件类型校验严一档说明、URL 段拼写不一致保留
- [ ] 6.2 路线图 Requirement 表标本 change 完成（归档时执行，与 cluster-extras 一同处理）

## 7. 编译 / 重启 / 验证

- [x] 7.1 `cd kuship-console && mvn -DskipTests package` 通过；`mvn test` 在 third-party 范围内 20 用例全过（9 单测 + 11 集成）
- [ ] 7.2 创建一个第三方组件（用 app-create 已有 endpoint）（**需用户本地起 console + region 后联动**）
- [ ] 7.3 `curl ... POST .../third_party/pods -d '{"address":"10.0.0.1:80","is_online":true}'` → 200（**需用户联动**）
- [ ] 7.4 `curl ... GET .../third_party/pods` → 200 + list 含刚加的 endpoint（**需用户联动**）
- [ ] 7.5 `curl ... PUT .../3rd-party/health -d '{"mode":"tcp","port":80,"period":30}'` → 200（**需用户联动**）
