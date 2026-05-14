## Why

`migrate-console-region-cluster` 在 `ClusterOperations` 接口中预留了 5 个 default unsupported method —— `getResources` / `getClusterInfo` / `getClusterEvents` / `getNodes` / `getNodeDetail` —— 这些桩自落地以来未被任何 controller 引用，但路线图把"集群基础信息"列为 P0 缺口（UI 集群资源页打开就空、跳节点详情即空）。

清桩本身是热身工作量（5 region method、纯透传），但它解锁了三件事：

1. UI 集群资源 / 节点列表能直接拉到数据，不再依赖 cluster-nodes 子 change 的更复杂落地
2. 后续 `migrate-console-cluster-nodes` 子 change 的"业务级富化"接口（`ClusterNodeOperations`，含 labels / taints / action / metrics 聚合）能直接**复用**本 change 落地的底层 region 透传，不重复写 URL 拼装
3. 让 `ClusterOperations` 接口的所有 default `unsupported(IMPLEMENTING_CHANGE)` 调用清零，避免日后误调时抛异常

`migrate-region-coverage-roadmap` 把这块归为 **P0 #1**（5 method，热身规约验证）。本 change 完整迁移 rainbond `regionapi.py:get_tenant_resources / get_cluster_resource / get_cluster_nodes / get_node_info / get_resource_center_events` 5 段 region API 调用。

## What Changes

### 实现 Region Operations 接口现有 default method

落地 `infrastructure/region/api/ClusterOperationsImpl.java`（已 `@Primary`）的 5 个 method override，每个 1:1 透传 region API：

- `getResources(regionName, tenantName, enterpriseId)` → GET `/v2/tenants/{tenant_name}/resources?enterprise_id={eid}` —— rainbond 锚点 `regionapi.py:92-100 get_tenant_resources`
- `getClusterInfo(regionName)` → GET `/v2/cluster/info` —— rainbond 锚点 `regionapi.py:get_cluster_resource(regionName, "info")`
- `getClusterEvents(regionName, body)` → GET `/v2/cluster/events?<query>` —— rainbond 锚点 `regionapi.py:get_cluster_resource(regionName, "events", params)`
- `getNodes(regionName)` → GET `/v2/cluster/nodes` —— rainbond 锚点 `regionapi.py:2661-2668 get_cluster_nodes`
- `getNodeDetail(regionName, nodeName)` → GET `/v2/cluster/nodes/{node_name}/detail` —— rainbond 锚点 `regionapi.py:2679-2686 get_node_info`

### 新增最小化 controller（4 个，5 endpoint）

按 rainbond `console/urls/__init__.py` 行号锚点（路径变量统一 snake_case）：

- `ClusterInfoController` (`/console/enterprise/{enterprise_id}/regions/{region_name}/info`) GET — 集群版本 / 容量等基础信息
- `ClusterEventsController` (`/console/enterprise/{enterprise_id}/regions/{region_name}/cluster-events`) GET — 集群级 events（不带 tenant 维度）
- `ClusterNodesController` (`/console/enterprise/{enterprise_id}/regions/{region_name}/nodes` + `.../{node_name}`) GET 列表 + GET 详情 — `urls.py:1008,1010` 锚点；后续 `migrate-console-cluster-nodes` 子 change 在同一 controller 上**扩展** action / labels / taints / container 4 个端点，不替换路径
- `TenantResourcesController` (`/console/teams/{team_name}/resources`) GET — 租户在某 region 内的资源使用（`?region_name=&enterprise_id=`）

5 个 endpoint 全部走 advice 自动包装为 general_message 形状。

### 不在本 change 内（明确推迟）

- 节点 action（cordon / uncordon / drain）、labels CRUD、taints CRUD、container 存储 → `migrate-console-cluster-nodes`（P0 #4，新建 `ClusterNodeOperations` 接口承载业务级语义）
- 节点 metrics 聚合（CPU / 内存 / Pod 数）→ `migrate-console-cluster-nodes` + `migrate-console-monitor-extras`
- 资源中心 Pod / Workload 详情 → `migrate-console-resource-center`
- 集群 enterprise 维度的 resource summary（`get_region_tenants_resources`，跨 tenant 汇总）→ 已在 `ClusterOperations.getRegionResources` 实现，不在本 change

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "集群基础信息透传"。覆盖 4 controller / 5 endpoint 的契约、`ClusterOperations` 接口 5 个 method 的 region URL 路径与响应透传约束、与后续 cluster-nodes / resource-center / monitor-extras 子 change 的解耦边界。

## Impact

- **代码新增**：
  - controller：4 个 (`modules/region/controller/cluster/`)
  - region API：扩 `ClusterOperationsImpl` 5 method 实现（接口 default 已存在）
  - entity：无新增（节点 / 集群信息全部走 region 实时，无本地表）
  - 单测：`ClusterOperationsImplTest`（5 method 各覆盖 1 happy + 1 error 透传）+ 4 个 controller 集成测试
- **数据库**：无变更
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - `cluster-nodes` 子 change 后续在 `ClusterNodesController` 上**扩展**（不替换）端点；底层 `ClusterOperations.getNodes/getNodeDetail` 仍由本 change 维护，cluster-nodes 在 `ClusterNodeOperations` 新接口上提供业务级富化
  - `resource-center` 子 change 用 `ClusterOperations.getClusterEvents` 作为底层之一，本 change 落地后即可调用
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change
- **路径变量**：路径中 `{enterprise_id}` / `{region_name}` / `{node_name}` / `{team_name}` 全部 snake_case（与项目硬约束一致；rainbond Python 端 `node_name` 正则带 `[\w\-.]+` 允许带点的节点名，本 change 沿用）
