## Why

kuship-ui "资源中心"菜单 + "K8s 原生资源管理"页打开是空白：跳 Pod 详情、看 Workload 详情、查看资源中心事件、流式查 Pod 日志、增删改查 K8s 命名空间级 CRD（CronJob / ConfigMap / Secret / 自定义 CRD 等 YAML 资源）—— 9 个 endpoint 全无 handler。

UI 表现：
- 应用看板上"查看 Pod 详情"按钮 → 404
- 集群资源中心 → workload list 空，跳详情 5xx
- 资源中心事件流 → 空
- "K8s YAML 编辑" 弹窗保存 → 405

`migrate-region-coverage-roadmap` 把这块归为 **P0 #5**（10 method）。本 change 完整迁移 rainbond `console/views/team_resources.py:164-330` 共 9 个 view + `console/urls/team_resources.py` 路由 + `regionapi.py:3670-3849` 共 10 个 region API method。

## What Changes

### 新增 Region Operations 接口

新增 `modules/resourcecenter/api/ResourceCenterOperations.java` 5 method：

| method                                           | HTTP | 路径                                                                  |
|--------------------------------------------------|------|-----------------------------------------------------------------------|
| getWorkloadDetail(rn, tn, resource, name, query) | GET  | `/v2/tenants/{tenant_name}/resource-center/workloads/{resource}/{name}` |
| getPodDetail(rn, tn, podName)                    | GET  | `/v2/tenants/{tenant_name}/resource-center/pods/{pod_name}`           |
| getEvents(rn, tn, query)                         | GET  | `/v2/tenants/{tenant_name}/resource-center/events`                    |
| getPodLogs(rn, tn, podName, query) → stream      | GET  | `/v2/tenants/{tenant_name}/resource-center/pods/{pod_name}/logs`      |

新增 `modules/resourcecenter/api/NsResourceOperations.java` 6 method（K8s namespace 级资源 CRUD，对应 rainbond `tenant_ns_resource_*`）：

| method                                          | HTTP   | 路径                                          |
|-------------------------------------------------|--------|-----------------------------------------------|
| getResourceTypes(rn, tn)                        | GET    | `/v2/tenants/{tenant_name}/ns-resource-types` |
| listResources(rn, tn, query)                    | GET    | `/v2/tenants/{tenant_name}/ns-resources`      |
| getResource(rn, tn, name, query)                | GET    | `/v2/tenants/{tenant_name}/ns-resources/{name}` |
| createResource(rn, tn, contentType, body, query)| POST   | `/v2/tenants/{tenant_name}/ns-resources`      |
| updateResource(rn, tn, name, contentType, body, query) | PUT | `/v2/tenants/{tenant_name}/ns-resources/{name}` |
| deleteResource(rn, tn, name, query)             | DELETE | `/v2/tenants/{tenant_name}/ns-resources/{name}` |

合计 10 method，分两个接口（按业务子域），全部由对应 `*Impl @Primary` 实现透传。

### 新增 controller（8 个，9 endpoint）

按 rainbond `console/urls/team_resources.py` 行号锚点（路径变量 snake_case）：

- `NsResourceTypesController` (`/console/teams/{team_name}/regions/{region_name}/ns-resource-types`) GET — `team_resources urls:23` `NsResourceTypesView`
- `NsResourcesController` (`/console/teams/{team_name}/regions/{region_name}/ns-resources`) GET / POST — `team_resources urls:25` `NsResourcesView`
- `NsResourceDetailController` (`/console/teams/{team_name}/regions/{region_name}/ns-resources/{name}`) GET / PUT / DELETE — `team_resources urls:27` `NsResourceDetailView`
- `ResourceCenterWorkloadDetailController` (`/console/teams/{team_name}/regions/{region_name}/resource-center/workloads/{resource}/{name}`) GET — `team_resources urls:36` `ResourceCenterWorkloadDetailView`
- `ResourceCenterPodDetailController` (`/console/teams/{team_name}/regions/{region_name}/resource-center/pods/{pod_name}`) GET — `team_resources urls:39` `ResourceCenterPodDetailView`
- `ResourceCenterEventsController` (`/console/teams/{team_name}/regions/{region_name}/resource-center/events`) GET — `team_resources urls:41` `ResourceCenterEventsView`
- `ResourceCenterPodLogsController` (`/console/teams/{team_name}/regions/{region_name}/resource-center/pods/{pod_name}/logs`) GET (streaming) — `team_resources urls:43` `ResourceCenterPodLogsView`
- `ResourceCenterWSInfoController` (`/console/teams/{team_name}/regions/{region_name}/resource-center/ws-info`) GET — `team_resources urls:45` `ResourceCenterWSInfoView`（不调 region，仅返 WebSocket 连接信息）

### 业务规则迁移

按 `team_resources.py:164-330`：

- **NsResource POST/PUT 内容类型透传**：rainbond view 用 `request.body` 直传原始字节流 + `request.META["CONTENT_TYPE"]` 透传给 region API；不做 JSON 反序列化（支持 `application/yaml` 内容类型）。kuship 端 controller 用 `HttpServletRequest.getInputStream() + getContentType()`，service 层透传给 region
- **NsResource POST 状态码透传**：rainbond `NsResourcesView.post` 用 `getattr(res, "status", 200)` 透传 region status code（如 422 校验失败 / 409 冲突）。kuship 端 controller 用 `ResponseEntity.status(...)` 显式回写 region 的 HTTP 状态码
- **NsResource DELETE 幂等**：rainbond view 不区分"资源不存在"和"删除成功"，统一返 200。kuship 端透传 region 行为（404 时透传 404，不强行幂等）
- **PodLogs 流式响应**：rainbond `region_api.get_resource_center_pod_log` 用 `preload_content=False` 流式读 region；kuship 端用 `StreamingResponseBody` + Spring WebMvc 流式输出，不通过 `GeneralMessageResponseBodyAdvice` 包装（用 `@SkipResponseWrapper` 注解跳过）
- **WSInfo**：本 endpoint 不调 region，仅返回前端连接 WebSocket 网关所需的 token + URL；token 沿用 `RequestContext.userId` 签发短时效（5 分钟）的 WebSocket 票据 —— 这是 rainbond Python 历史行为，Java 端复用现有 `JwtIssuer`
- **events query 透传**：`?type=warning&kind=Pod&namespace=...` 等 query 参数全部透传给 region

### 不在本 change 内（明确推迟 / 切出）

- `TeamComponentsView` (`/teams/{name}/regions/{rn}/components`) → 该 view 不调 region，直接读本地 `tenant_service` 表返"团队下指定 region 内组件 basic info" —— 与 `migrate-console-application-core` 已实现的组件列表重叠，本 change 不接管，留给 application 子 change 后续 hardening
- 应用 / 服务维度的 Pod 列表（`/apps/{alias}/pods` / `/groups/{group_id}/pods`）→ 已在 `migrate-console-app-runtime` 实现
- 容器存储 (`/v2/container_disk/{type}`) → 已在 `migrate-console-cluster-nodes` 实现
- HelmReleases 系列 view → 已在 `migrate-console-helm-release` archive 中
- WebSocket 服务端实现（接受 PodLogs WS 长连接）→ rainbond Python 也是前端直连 region 的 WS 网关，console 仅签 token；本 change 同此

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "K8s 资源中心与命名空间资源管理"。覆盖 8 controller / 9 endpoint 的契约、`ResourceCenterOperations` + `NsResourceOperations` 两接口共 10 method 的 region URL 与 query 透传约束、PodLogs 流式响应规约、NsResource POST/PUT 原始 body + content-type 透传规约、WSInfo token 签发规约。

## Impact

- **代码新增**：
  - 接口：`ResourceCenterOperations`（4 method） + `NsResourceOperations`（6 method） + `*Impl @Primary`
  - controller：8 个 (`modules/resourcecenter/controller/`)
  - service：`PodLogStreamingService`（流式响应封装） + `WebSocketTokenService`（WSInfo token 签发）
  - 单测 + 集成测试：10 method × （1 happy + 1 error）+ 9 controller 集成测试 + PodLogs 流式 + WSInfo token
- **数据库**：无变更
- **依赖**：不引入新 maven 依赖（流式响应用 Spring WebMvc 自带 `StreamingResponseBody`）
- **跨 change 衔接**：
  - `cluster-extras` 已实现 `ClusterOperations.getClusterEvents`（集群级事件，不带 namespace），与本 change `getEvents`（tenant 内事件，需 namespace + kind 过滤）共存不冲突
  - `cluster-nodes` 已实现 `getContainerDisk`，本 change 不重复
  - `kubeblocks` 子 change 后续会复用 `getResource`（查 KubeBlocks Cluster 自定义资源）—— 本 change 落地后即可被 P1 子 change 直接调用
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change
- **路径变量**：`{team_name}` / `{region_name}` / `{pod_name}` / `{resource}` / `{name}` 全部 snake_case
- **CONTENT_TYPE 转发**：rainbond region 端 NsResource POST/PUT 接受 `application/yaml` 与 `application/json`；本 change kuship controller 不限定 content-type，透传给 region
