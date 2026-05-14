# Design — migrate-console-resource-center

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #5**，估 10 method（实际精算 10 完全对齐）+ 9 endpoint，工作量约 1-1.5 周。归档时反向更新路线表对应行。

依赖：无强依赖。软依赖 `cluster-extras` 完成（其 `getClusterEvents` 与本 change `getEvents` 在 controller 命名上做区分）；与 `gateway-domain` / `cluster-nodes` 不冲突，可并行推进。

## Region API URL 表

ResourceCenter 子接口（4 method）：

| method                                         | HTTP | 路径                                                                  | rainbond 锚点                  |
|------------------------------------------------|------|-----------------------------------------------------------------------|--------------------------------|
| getWorkloadDetail(rn, tn, resource, name, q)   | GET  | `/v2/tenants/{tenant_name}/resource-center/workloads/{resource}/{name}` | `regionapi.py:3811-3819`        |
| getPodDetail(rn, tn, podName)                  | GET  | `/v2/tenants/{tenant_name}/resource-center/pods/{pod_name}`           | `regionapi.py:3822-3828`        |
| getEvents(rn, tn, query)                       | GET  | `/v2/tenants/{tenant_name}/resource-center/events`                    | `regionapi.py:3830-3839`        |
| getPodLogs(rn, tn, podName, query) → InputStream | GET | `/v2/tenants/{tenant_name}/resource-center/pods/{pod_name}/logs`     | `regionapi.py:3841-3849`        |

NsResource 子接口（6 method）：

| method                                         | HTTP   | 路径                                          | rainbond 锚点                  |
|------------------------------------------------|--------|-----------------------------------------------|--------------------------------|
| getResourceTypes(rn, tn)                       | GET    | `/v2/tenants/{tenant_name}/ns-resource-types` | `regionapi.py:3670-3676`        |
| listResources(rn, tn, query)                   | GET    | `/v2/tenants/{tenant_name}/ns-resources`      | `regionapi.py:3678-3687`        |
| getResource(rn, tn, name, query)               | GET    | `/v2/tenants/{tenant_name}/ns-resources/{name}` | `regionapi.py:3689-3698`      |
| createResource(rn, tn, ct, body, query)        | POST   | `/v2/tenants/{tenant_name}/ns-resources`      | `regionapi.py:3700-3712`        |
| updateResource(rn, tn, name, ct, body, query)  | PUT    | `/v2/tenants/{tenant_name}/ns-resources/{name}` | `regionapi.py:3714-3726`      |
| deleteResource(rn, tn, name, query)            | DELETE | `/v2/tenants/{tenant_name}/ns-resources/{name}` | `regionapi.py:3728-3736`      |

注意路径中 `tenant_name` 是 console 端 team_name（与 cluster-extras `getResources` 用 `region_tenant_name=namespace` 不同）—— rainbond Python 端 `team_resources.py` 的 view 直传 team_name 不查 namespace，本 change 沿用。

## Controller 路径锚点

| Controller                                  | path                                                                          | method            | rainbond 锚点 |
|---------------------------------------------|-------------------------------------------------------------------------------|-------------------|--------------|
| NsResourceTypesController                   | `/console/teams/{team_name}/regions/{region_name}/ns-resource-types`           | GET               | `team_resources urls:23` |
| NsResourcesController                       | `/console/teams/{team_name}/regions/{region_name}/ns-resources`                | GET / POST         | `team_resources urls:25` |
| NsResourceDetailController                  | `/console/teams/{team_name}/regions/{region_name}/ns-resources/{name}`         | GET / PUT / DELETE | `team_resources urls:27` |
| ResourceCenterWorkloadDetailController      | `/console/teams/{team_name}/regions/{region_name}/resource-center/workloads/{resource}/{name}` | GET | `team_resources urls:36` |
| ResourceCenterPodDetailController           | `/console/teams/{team_name}/regions/{region_name}/resource-center/pods/{pod_name}` | GET           | `team_resources urls:39` |
| ResourceCenterEventsController              | `/console/teams/{team_name}/regions/{region_name}/resource-center/events`       | GET               | `team_resources urls:41` |
| ResourceCenterPodLogsController             | `/console/teams/{team_name}/regions/{region_name}/resource-center/pods/{pod_name}/logs` | GET (stream) | `team_resources urls:43` |
| ResourceCenterWSInfoController              | `/console/teams/{team_name}/regions/{region_name}/resource-center/ws-info`      | GET               | `team_resources urls:45` |

trailing slash 兼容：每 endpoint 同时声明 `path` 与 `path/`。

## 决策 1 — NsResource POST/PUT 透传原始 body + content-type

rainbond `NsResourcesView.post` / `NsResourceDetailView.put` 用 `request.body`（原始字节流）+ `request.META["CONTENT_TYPE"]` 直传 region。这是为了支持 K8s 风格的 YAML 请求体：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-config
  namespace: my-team
data:
  key: value
```

如果用 `@RequestBody Map<String, Object>` 接收，YAML 会被 Spring 拒绝（默认 JSON 解析器）。

**决策**：本 change 用 `HttpServletRequest.getInputStream()` 读字节流 + `request.getContentType()` 取 content-type，透传给 region。具体：

```java
@PostMapping("/.../ns-resources")
public ResponseEntity<String> createResource(HttpServletRequest req,
                                              @PathVariable("team_name") String teamName,
                                              ...) {
    byte[] body = req.getInputStream().readAllBytes();
    String contentType = req.getContentType(); // "application/yaml" or "application/json"
    Map<String, String> queryParams = ...; // 从 req.getParameterMap() 取
    return nsResourceOps.createResource(rn, teamName, contentType, body, queryParams);
}
```

`@SkipResponseWrapper` 跳过 advice 自动包装（POST/PUT 也透传 region 原始响应 + status code）—— 这是 rainbond 行为：`NsResourcesView.post` 返 `Response(data, status=status_code)`，data 是 region 原始 dict，**不**走 general_message 包装。

## 决策 2 — POST 状态码透传

rainbond view：

```python
res, data = region_api.post_tenant_ns_resource(...)
status_code = getattr(res, "status", 200)
return Response(data, status=status_code)
```

422 (validation failed) / 409 (resource conflict) / 201 (created) 等 region 状态码直接回到客户端。

**决策**：kuship controller 用 `ResponseEntity.status(regionResp.statusCode()).body(regionResp.body())` 显式回写 region 状态。`RegionApiResponseProcessor.checkStatus` 的 4xx/5xx 异常映射对本 endpoint **不适用**（用 `RegionApiSupport.exchange` 拿原始 ResponseEntity，不走 processor）。

PUT 同样行为，但 rainbond view 强制返 200 + general_message —— **kuship 沿用 PUT 强制 200**，与 rainbond 一致；POST 才走透传 status。

DELETE 走 general_message + 200（rainbond 也是固定返 `删除成功`）。

## 决策 3 — PodLogs 流式响应

rainbond `region_api.get_resource_center_pod_log` 用 `preload_content=False` 流式读 region socket，`Response(stream)` 直接 streaming back to client。

**决策**：kuship 端用 Spring WebMvc 的 `StreamingResponseBody`：

```java
@GetMapping(".../pods/{pod_name}/logs")
@SkipResponseWrapper
public ResponseEntity<StreamingResponseBody> podLogs(...) {
    StreamingResponseBody stream = output -> {
        try (InputStream regionStream = resourceCenterOps.getPodLogs(rn, tn, podName, query)) {
            regionStream.transferTo(output);
        }
    };
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body(stream);
}
```

`ResourceCenterOperations.getPodLogs` 返 `InputStream`（不是 `Map<String,Object>`），实现层用 `RestClient.get().exchange((req, resp) -> resp.getBody())` 拿 raw input stream，不读完。

需要在 region client 层处理超时：log streaming 可能持续数小时，不能用默认 5s 超时。`RegionClientFactory` 已有 `kuship.region.timeout-seconds` 配置（默认 5s），本 change 在 `ResourceCenterOperationsImpl` 内部为 PodLogs 单独构造长超时 RestClient（如 30 分钟），其他 method 走默认。

实际上更稳妥：把 PodLogs 实现为 SSE-friendly 模式（每读到一段输出 flush 给客户端），这样浏览器 EventSource 也能消费。但 rainbond 是普通 chunked transfer-encoding，本 change 沿用 chunked 不走 SSE。

## 决策 4 — Events 与 ClusterOperations.getClusterEvents 的边界

`cluster-extras` 已实现 `ClusterOperations.getClusterEvents(regionName, body)`，路径 `/v2/cluster/events`（集群级事件，所有 namespace）。

本 change `ResourceCenterOperations.getEvents(regionName, tenantName, query)`，路径 `/v2/tenants/{tn}/resource-center/events`（租户/namespace 事件，可按 kind / name 过滤）。

URL 不同、scope 不同，两者并存：
- enterprise admin 看集群整体 events → 走 `ClusterEventsController`（cluster-extras）
- team member 看自己 tenant 内的 events → 走 `ResourceCenterEventsController`（本 change）

不重命名既有 method 避免破坏 cluster-extras 已落地实现。

## 决策 5 — WSInfo 不调 region

rainbond `ResourceCenterWSInfoView` 返回前端 WebSocket 连接所需信息：

```json
{
  "ws_url": "wss://region-host/v2/tenants/{tn}/resource-center/pods/ws",
  "token": "<short-lived JWT>",
  "expires_in": 300
}
```

token 是 console 端短时效签发的 JWT，含 `user_id` + `team_name` + `region_name` 字段；前端连 region WebSocket 时把 token 放 query 或 header，region 端自行验证。

**决策**：本 change 在 `WebSocketTokenService` 实现 token 签发，复用既有 `JwtIssuer`（已在 `migrate-console-account-team` 落地）。`ws_url` 从 `RegionInfo.url` 取 + 替换 scheme（http→ws / https→wss）。`expires_in` 默认 300 秒，可配置 `kuship.resourcecenter.wsinfo.token-ttl-seconds`。

## 决策 6 — 错误处理

绝大多数 method 走 region 透传 + `RegionApiException` 自动映射。

NsResource POST 走原始 status code 透传（决策 2），不进 GlobalExceptionHandler 路径。但若 region 完全无响应（network error / timeout），仍抛 `RegionApiSocketException`，让 GlobalExceptionHandler 处理。

PodLogs 流式响应：如果上游 region 中途断开，`StreamingResponseBody` 让 client 看到 EOF（不抛异常）。如果开始流式前 region 就 5xx，照常走 `RegionApiException`（在 `transferTo` 之前）。

## 决策 7 — 权限

rainbond 所有 9 view 都是 `TenantHeaderView`（团队成员可见）。本 change 沿用：

- 全部 endpoint `@RequirePerm("describe_team_app")`（团队 read 权限）
- 写操作（NsResource POST/PUT/DELETE）`@RequirePerm("manage_team_resource")`（团队 manage 权限，与 rainbond 一致）

如果对应权限码在 kuship `PermCode` 里没有，回退到 `app_create_perms` 或 enterprise admin（与既有 controller 处理一致）。

## 决策 8 — query 参数透传策略

rainbond view 的 query 处理是 `params = {k: v for k, v in request.GET.items()}` 然后 `region_api(params=params)` —— 全部透传，不做白名单。

**决策**：kuship 端 controller 用 `@RequestParam Map<String, String> queryParams` 接所有 query 参数（**注意不能有 `@RequestBody` 同方法接 body 否则混淆**），全部传给 region。

PodLogs 的 query 通常含 `follow=true&previous=false&since_seconds=300&tail=100` 等，全部透传。

## 非决策（明确不做）

- **不**新建本地 entity（资源中心数据全部走 region 实时）
- **不**做 K8s YAML 校验（region 端做）
- **不**做 PodLogs 持久化（流式输出后丢弃，`migrate-console-monitor-extras` 后续可加日志聚合）
- **不**在本 change 实现 WebSocket server（前端直连 region WS，console 仅签 token）

## 测试约定

集成测试（`@SpringBootTest + @ActiveProfiles({"local","contract-test"})`）覆盖：

- `NsResourceTypesControllerTest`：透传 region bean
- `NsResourcesControllerTest`：
  - GET 透传 list
  - POST `application/yaml` body 透传 + region 422 状态码透传
  - POST `application/json` body 透传
- `NsResourceDetailControllerTest`：
  - GET 透传 + query 参数透传
  - PUT body 透传 → 强制 200
  - DELETE 路径变量 `name` 含 `.` 不被错误解析
- `ResourceCenterWorkloadDetailControllerTest`：路径变量 `resource=Deployment` `name=my-app` 透传
- `ResourceCenterPodDetailControllerTest`：透传 bean
- `ResourceCenterEventsControllerTest`：query `?kind=Pod&type=warning` 透传给 region
- `ResourceCenterPodLogsControllerTest`：
  - 流式响应：mock region 返 chunked stream，断言客户端能逐块读到
  - region 5xx → 流式开始前 5xx 透传
  - `?follow=true` query 透传
- `ResourceCenterWSInfoControllerTest`：
  - 返 `{ws_url, token, expires_in}` shape
  - token 包含 `user_id` / `team_name` / `region_name` claims
  - token TTL = 300s

`@MockitoBean ResourceCenterOperations` / `NsResourceOperations` 替换 region 调用。

PodLogs 流式集成测试用 `MockRestServiceServer` 的 chunked response（Spring 6 原生支持）。
