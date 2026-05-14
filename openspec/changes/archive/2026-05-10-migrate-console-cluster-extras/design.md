# Design — migrate-console-cluster-extras

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #1**（路线热身项），估计 method 数 **5**，工作量约 2-3 天，远低于 ≤ 30 上限。归档时反向更新路线表对应行。

依赖：无（独立可起）。`cluster-nodes` / `resource-center` 子 change 软依赖本 change 完成（它们会复用本 change 落地的 `ClusterOperations.getNodes/getNodeDetail/getClusterEvents` 作为底层调用）。

## Region API URL 表

| method                              | HTTP | 路径                                                   | rainbond 锚点                                |
|-------------------------------------|------|--------------------------------------------------------|---------------------------------------------|
| getResources(rn, tn, eid)           | GET  | `/v2/tenants/{tenant_name}/resources?enterprise_id={eid}` | `regionapi.py:92-100 get_tenant_resources`   |
| getClusterInfo(rn)                  | GET  | `/v2/cluster/info`                                     | `regionapi.py:3618 get_cluster_resource(rn,"info")` |
| getClusterEvents(rn, body)          | GET  | `/v2/cluster/events` + query 透传 body 的 k/v           | `regionapi.py:3618 get_cluster_resource(rn,"events",params)` |
| getNodes(rn)                        | GET  | `/v2/cluster/nodes`                                    | `regionapi.py:2661-2668 get_cluster_nodes`   |
| getNodeDetail(rn, nodeName)         | GET  | `/v2/cluster/nodes/{node_name}/detail`                 | `regionapi.py:2679-2686 get_node_info`       |

`getResources` 路径的 `tenant_name` 段：rainbond `regionapi.py:97` 用的是 `tenant_region.region_tenant_name`（即 namespace），与其他 method 直传 `tenant_name` 不同。kuship 端在 `ClusterOperationsImpl.getResources` 内部从 `TenantsRepository.findByTenantName(...).getNamespace()` 取 namespace，与 `migrate-console-helm-release` / `migrate-console-gateway-certificate` 的 `updateIngressesByCertificate` 同样套路。

## Controller 路径锚点

| Controller                  | path                                                                | method | rainbond 锚点                                |
|-----------------------------|---------------------------------------------------------------------|--------|---------------------------------------------|
| ClusterInfoController       | `/console/enterprise/{enterprise_id}/regions/{region_name}/info`     | GET    | rainbond Python 无独立 view（`urls.py:215` 是 license cluster-id），本 change 新建 |
| ClusterEventsController     | `/console/enterprise/{enterprise_id}/regions/{region_name}/cluster-events` | GET | rainbond 端没暴露成 console URL（直接走 region 透传），本 change 新建供 UI 调用 |
| ClusterNodesController      | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes`    | GET    | `urls.py:1008` `GetNodes`                   |
| ClusterNodesController      | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}` | GET | `urls.py:1010` `GetNode`                  |
| TenantResourcesController   | `/console/teams/{team_name}/resources`                              | GET    | rainbond Python 无 console view（仅 region API），本 change 新建 |

trailing slash 兼容沿用既定规则（每 endpoint 同时声明 `path` 与 `path/`）。

`/console/teams/{team_name}/resources` 的命名避免与 `migrate-console-app-runtime` 已有的 `/apps/{service_alias}/resource` 端点冲突 —— 后者是组件维度的资源（CPU 占用），前者是租户维度的资源（quota / used）。

## 决策 1 — Controller URL 边界与后续子 change 解耦

`ClusterNodesController` 的 `GET /nodes` 与 `GET /nodes/{node_name}` 由本 change 提供 region 透传实现。

`migrate-console-cluster-nodes` 子 change 后续将在**同一个 controller 类**上扩展：

```java
// 本 change 落地
@GetMapping(value = {"/console/enterprise/{eid}/regions/{rn}/nodes", "/console/enterprise/{eid}/regions/{rn}/nodes/"})
public ApiResult listNodes(...) { return clusterOps.getNodes(rn); }

@GetMapping(value = {"/console/enterprise/{eid}/regions/{rn}/nodes/{node_name}", ".../"})
public ApiResult nodeDetail(...) { return clusterOps.getNodeDetail(rn, nodeName); }

// migrate-console-cluster-nodes 子 change 在同一 controller 加：
@PostMapping("/console/enterprise/{eid}/regions/{rn}/nodes/{node_name}/action")
public ApiResult nodeAction(...) { return clusterNodeOps.operateNode(...); }
// + labels / taints / container 等
```

URL 不重复，行为分层：
- **本 change**：透传 region，不做业务聚合
- **cluster-nodes**：业务级，可能聚合 metrics / Pod 计数 / 节点上 rainbond 组件等

## 决策 2 — `getClusterEvents` 的 body 含义

`ClusterOperations.getClusterEvents(regionName, Map<String, Object> body)` 接口签名是带 `body` 的，但语义上是 `GET` 请求，body 实际是 query string 的 k/v map（rainbond Python `get_cluster_resource(rn, "events", params)` 的 `params` 同样是 query map，不是 request body）。

**决策**：本 change 把 `body` map 序列化为 query string（按 `&` 拼接 + URL encode），不发 request body —— 这样既不破坏接口签名，也与 rainbond Python 端行为一致。

未来若 region 端改成 GET with body，可再调整。

## 决策 3 — `getClusterInfo` 路径推断

rainbond 没有专门的 `get_cluster_info` region method，但有通用代理 `get_cluster_resource(rn, "info")`。region Go 端 `/v2/cluster/info` 是否存在需在本 change 实施前验证：

1. 用 curl 先打 `GET <region>/v2/cluster/info` 验证 200
2. 若 region 端返 404，换路径为 `/v2/cluster`（裸路径）
3. 若也 404，本 change 把 `getClusterInfo` 标"region 端不支持"，实现为返本地 `region_info` 表的 entity 数据 + 一些静态字段（与 rainbond `regionapi.py:1404 get_region_info` 同样从本地 `region_info` 表读取，不调 region）

实施任务（tasks.md §2.4）会在 `ClusterOperationsImpl.getClusterInfo` 上明确标注探测结果与最终路径。

**实施期探测结果**（2026-05-10 实施阶段，由于无在线 region 实例直接探测推迟到 task 7 联动验证）：

- 默认按 rainbond Python `get_cluster_resource(rn, "info")` 路径取 `/v2/cluster/info` 实现
- region 端返 4xx (含 404) 时 `ClusterOperationsImpl.getClusterInfo` 内部 catch `RegionApiException`，仅 httpStatus == 404 时降级为读本地 `region_info` entity 的字段子集 (`region_name / region_alias / url / wsurl / tcpdomain / httpdomain / status / scope / provider / region_type`)
- 其他 4xx/5xx 透传 `RegionApiException` 不降级
- 单测 `ClusterOperationsImplExtraTest.getClusterInfo_404_fallbacksToLocalRegionInfo` 用 MockRestServiceServer 模拟 404 验证降级路径，避免实施期需要真实 region 容器

## 决策 4 — `TenantResourcesController` 与既有 namespace controller 的边界

`migrate-console-region-cluster` 已在 `ClusterNamespacesController` 上落地：
- `/console/enterprise/{eid}/regions/{rid}/resource` —— enterprise 维度集群总资源
- `/console/enterprise/{eid}/regions/{rid}/tenants` —— 列出该 region 内所有 tenant
- `/console/enterprise/{eid}/regions/{rid}/tenants/{tn}/limit` —— 设 tenant 资源上限

本 change 新增的 `/console/teams/{team_name}/resources?region_name=&enterprise_id=` 与上述不冲突，区别在于：
- 既有 `tenants/{tn}/limit` 是 enterprise admin 视角设置上限
- 本 change `/teams/{team_name}/resources` 是普通团队成员查自己 tenant 的当前用量

rainbond Python 端 `get_tenant_resources` 实际被多处调用（团队页 / 应用创建前置校验 / 消费监控），本 change 提供一个 console URL 入口暴露给 UI。

## 决策 5 — 错误透传

5 个 method 都是简单 region 透传，遇到 region 错误走 `RegionApiException` + `GlobalExceptionHandler` 自动映射为 general_message。`region not found` 的本地 entity 缺失在 `RegionClientFactory` 已有处理，不在本层重复。

`getClusterInfo` 若决策 3 探测出 region 端不支持，本 change 实现为读本地 `region_info` 表 → 不抛 region 异常，但响应字段集会少（`version` / `capacity` 等只能从 region 实时 K8s 拿到的字段缺失）。tasks.md 标注此降级行为。

## 决策 6 — 测试用 region 端真实数据 vs Mock

`ClusterOperationsImplTest` 走 `MockRestServiceServer`（与已有 `TenantOperationsImplTest` / `HelmOperationsImplTest` 同模式）。

集成测试（4 个 controller）走 `@MockitoBean ClusterOperations`，断言 region 入参（regionName / nodeName / body 序列化为 query 的形状）。

不依赖本地起 region 容器（项目既定测试规约）。

## 非决策（明确不做）

- **不**修改 `ClusterOperations` 接口签名（即使 `getClusterEvents` 的 body 当 query 用名字略歧义，保留以避免破坏 default 占位的兼容性）
- **不**新建 `ClusterEventsOperations` 等独立接口（5 method 都属于 cluster 基础信息域，挂在 `ClusterOperations` 上合理）
- **不**实现 PostNode / PutNode 等写操作（属 cluster-nodes 子 change）
- **不**实现 cluster 资源中心相关的复杂查询（`/v2/cluster/resource-center/*`）—— 属 resource-center 子 change

## 测试约定

集成测试覆盖：

- `ClusterInfoControllerTest`：mock `ClusterOperations.getClusterInfo` 返一个 sample bean，断言响应 general_message 形状
- `ClusterEventsControllerTest`：mock 返 events list，断言 query 参数透传（如 `?type=warning&since=1h`）
- `ClusterNodesControllerTest`：
  - 列表 happy path
  - 详情 happy path（`node_name` 含点号 `worker-01.example.com`）
  - region 5xx 透传（断言响应 code = region code）
- `TenantResourcesControllerTest`：
  - 路径变量 `team_name` 解析到 namespace 后传给 `getResources`
  - team 不存在 → 404
  - region 失败透传

每个 controller 至少 1 happy + 1 error 透传 + 1 路径变量校验。
