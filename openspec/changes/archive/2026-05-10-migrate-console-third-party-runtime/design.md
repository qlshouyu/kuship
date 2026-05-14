# Design — migrate-console-third-party-runtime

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #8**，估 6 method（精算一致），工作量约 3 天。

依赖：软依赖 `migrate-console-app-create`（已落地第三方组件创建，本 change 处理创建后的运行时管理）。可与 volume-extras / dependency-extras 并行。

## Region API URL 表

| method                                       | HTTP   | 路径                                                                  | rainbond 锚点              |
|----------------------------------------------|--------|-----------------------------------------------------------------------|---------------------------|
| getEndpoints(rn, tn, alias)                  | GET    | `/v2/tenants/{namespace}/services/{alias}/endpoints`                  | `regionapi.py:1861-1869`   |
| postEndpoints(rn, tn, alias, body)           | POST   | `/v2/tenants/{namespace}/services/{alias}/endpoints`                  | `regionapi.py:1839-1847`   |
| putEndpoints(rn, tn, alias, body)            | PUT    | `/v2/tenants/{namespace}/services/{alias}/endpoints`                  | `regionapi.py:1828-1836`   |
| deleteEndpoints(rn, tn, alias, body)         | DELETE | `/v2/tenants/{namespace}/services/{alias}/endpoints`                  | `regionapi.py:1850-1858`   |
| getHealth(rn, tn, alias)                     | GET    | `/v2/tenants/{namespace}/services/{alias}/3rd-party/probe`            | `regionapi.py:1872-1880`   |
| putHealth(rn, tn, alias, body)               | PUT    | `/v2/tenants/{namespace}/services/{alias}/3rd-party/probe`            | `regionapi.py:1883-1893`   |

`namespace` 取自 `Tenants.namespace || tenant_name`。

POST/PUT/DELETE endpoints 三个 region 调用需附 `resource_validation: true` header（rainbond `_set_headers(token, resource_validation="true")`）。GET endpoints 与 health 两个 不带。

## Controller 路径锚点

| Controller                              | path                                                                    | method            | rainbond 锚点               |
|-----------------------------------------|-------------------------------------------------------------------------|-------------------|----------------------------|
| ThirdPartyEndpointsController           | `/console/teams/{team_name}/apps/{service_alias}/third_party/pods`       | GET / POST / PUT / DELETE | `urls.py:576` `ThirdPartyAppPodsView` |
| ThirdPartyHealthController              | `/console/teams/{team_name}/apps/{service_alias}/3rd-party/health`       | GET / PUT          | `urls.py:494` `ThirdPartyHealthzView` |

trailing slash 兼容：每 endpoint 同时声明 `path` 与 `path/`。

注意 controller URL 段 `third_party` 与 `3rd-party` 不一致 —— rainbond 历史拼写：endpoint 用 `third_party/pods`（下划线），health 用 `3rd-party/health`（连字符 + 数字简写）。本 change **保留两种**风格，不修复一致性（与 rainbond URL 一致防破坏 kuship-ui 调用）。

## 决策 1 — `resource_validation: true` header 透传

rainbond `regionapi.py` 的 `post/put/delete_third_party_service_endpoints` 在 `_set_headers` 时多传一个 `resource_validation="true"`，最终生成 HTTP header `Resource-Validation: true`。

**决策**：kuship 端 `ThirdPartyServiceOperationsImpl` 在 POST/PUT/DELETE endpoints 三个 method 内显式加 header：

```java
ResponseEntity<String> resp = RegionApiSupport.exchange(clientFactory, regionName, "", API_TYPE, url, "POST",
    c -> c.post().uri(url)
        .header("Resource-Validation", "true")  // ← 透传
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange((req, r) -> RegionApiSupport.readAsString(r)));
```

GET endpoints / GET health / PUT health 不带此 header（与 rainbond 一致）。

## 决策 2 — 组件类型校验（必须是 third_party）

rainbond `ThirdPartyAppPodsView` / `ThirdPartyHealthzView` 继承 `AppBaseView`，本身不强校验组件类型。但实际业务上对内部组件调 `endpoints` API 会让 K8s API server 返 400 或意外覆盖 K8s service 配置。

**决策**：kuship 端 service 层先 `serviceRepo.findByTenantIdAndServiceAlias(tenantId, alias)` 取 `TenantService`，校验 `service.getServiceSource() == "third_party"`（或类似字段，看 application-core 怎么标记）；非 third_party 抛 `ServiceHandleException(400, "service is not a third-party service", "组件不是第三方组件")`。

这是比 rainbond 严一档的防御（rainbond 未做此校验，相信前端只对 third_party 组件展示这些按钮）。

## 决策 3 — endpoint body shape

`POST /endpoints` 既可单条 `{"address":"1.2.3.4:80","is_online":true}` 也可批量 `{"endpoints":[{...},{...}]}`，rainbond 端 region 接受两种格式。

**决策**：kuship controller 层不强 typed DTO，用 `@RequestBody Map<String, Object>` 透传。前端发什么 region 收什么。

`DELETE /endpoints` body=`{"ep_id":"<endpoint-id>"}` 同样透传。

## 决策 4 — 错误处理 + 权限

- 6 region method 透传 `RegionApiException`
- 组件类型校验失败抛 `ServiceHandleException(400)`
- 读端点 `@RequirePerm("describe_team_app")`
- 写端点 `@RequirePerm("manage_team_app")` 或 fallback `app_create_perms`

## 决策 5 — 与 ServiceStatusOperations.getServicePods 的边界

`ServiceStatusOperations.getServicePods` (已实现) URL 是 `/v2/tenants/{tn}/services/{alias}/pods`，看的是普通组件的 K8s Pod 列表。

本 change `getEndpoints` URL 是 `/v2/tenants/{tn}/services/{alias}/endpoints`，看的是 third-party 组件的逻辑 endpoint 列表（外部 IP/PORT，不在 K8s 里跑）。

URL 不同、scope 不同，不冲突。kuship UI 端按组件类型选不同 endpoint 调用：
- 内部组件 → `/apps/{alias}/pods`
- 第三方组件 → `/apps/{alias}/third_party/pods`

## 非决策（明确不做）

- **不**修复 URL 段拼写不一致（`third_party` vs `3rd-party`），与 rainbond URL 严格一致
- **不**实现 K8s ExternalName / Endpoints 的本地缓存（数据全在 region 实时）
- **不**在本 change 内做"endpoint 健康探针自动告警"（后续 monitor-extras 范畴）

## 测试约定

集成测试覆盖：

- `ThirdPartyServiceOperationsImplTest`：6 method × (1 happy + 1 5xx)；POST/PUT/DELETE 断言带 `Resource-Validation: true` header；GET 不带
- `ThirdPartyEndpointsControllerTest`：
  - 6.1.1 GET 透传 list（pods / endpoints）
  - 6.1.2 POST 单条 endpoint
  - 6.1.3 POST 批量 endpoints
  - 6.1.4 PUT 更新 is_online
  - 6.1.5 DELETE body=`{ep_id}`
  - 6.1.6 内部组件调 → 400 + `组件不是第三方组件`
  - 6.1.7 写端点无权限 → 403
- `ThirdPartyHealthControllerTest`：
  - GET 透传 probe 配置
  - PUT body=`{mode,scheme,path,port,period,timeout}` 透传
  - 内部组件调 → 400
