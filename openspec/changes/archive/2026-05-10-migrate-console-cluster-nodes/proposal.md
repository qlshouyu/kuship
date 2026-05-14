## Why

`migrate-console-cluster-extras` 完成后 UI 能拉到节点列表与详情，但所有"节点运维"动作（cordon / uncordon / drain / 上下电、改标签、加污点、查存储路径）整片缺失。kuship 当前：

- `POST /console/enterprise/{eid}/regions/{rn}/nodes/{node_name}/action` 无 handler — 节点不能调度状态切换
- `GET / PUT /.../nodes/{node_name}/labels` 无 handler — 调度亲和无法配置
- `GET / PUT /.../nodes/{node_name}/taints` 无 handler — 污点不可见 / 不可改
- `GET /.../nodes/{node_name}/container` 无 handler — 容器存储路径 / 总容量 / 已用量看不到
- `GET /.../rbd-components` 无 handler — Rainbond 平台自身组件列表（rbd-api / rbd-worker / rbd-monitor）看不到

UI 表现：用户进集群管理页 → 选节点 → 右侧"标签 / 污点 / 操作"标签页全是空。运维场景（节点维护下线、临时禁止调度、扩容前打专用标签）必须切回 rainbond-ui (7070) 完成。

`migrate-region-coverage-roadmap` 把这块归为 **P0 #4**，估 12 method，实际精算 **7 region method + 1 业务聚合服务**（路线表估值偏宽，design.md 决策 1 标注偏差原因）。本 change 完整迁移 rainbond `console/views/enterprise.py:911-985` 的 7 个节点运维 view + `regionapi.py:2688-2749` 的 7 个 region API method。

## What Changes

### 新增 Region Operations 接口

新增 `modules/region/api/ClusterNodeOperations.java`（业务模块下，与 `ClusterOperations` 解耦，规约见路线图 design.md 决策 4）：

| method                                          | HTTP   | 路径                                                          |
|------------------------------------------------|--------|---------------------------------------------------------------|
| `operateNodeAction(rn, nodeName, action)`      | POST   | `/v2/cluster/nodes/{node_name}/action/{action}`               |
| `getNodeLabels(rn, nodeName)`                  | GET    | `/v2/cluster/nodes/{node_name}/labels`                        |
| `updateNodeLabels(rn, nodeName, labels)`       | PUT    | `/v2/cluster/nodes/{node_name}/labels`                        |
| `getNodeTaints(rn, nodeName)`                  | GET    | `/v2/cluster/nodes/{node_name}/taints`                        |
| `updateNodeTaints(rn, nodeName, taints)`       | PUT    | `/v2/cluster/nodes/{node_name}/taints`                        |
| `getRainbondComponents(rn)`                    | GET    | `/v2/cluster/rbd-components`                                  |
| `getContainerDisk(rn, containerType)`          | GET    | `/v2/container_disk/{container_type}`                         |

7 个 method 全部 region 透传，由 `ClusterNodeOperationsImpl @Primary` 实现。

### 扩展 ClusterNodesController（cluster-extras 已建）

在 `cluster-extras` 子 change 落地的 `ClusterNodesController` 上追加 6 个端点（不替换既有 GET 端点）：

- `POST /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action` — 节点操作（`urls.py:1013`）
- `GET /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` — 标签查询（`urls.py:1016`）
- `PUT /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` — 标签更新
- `GET /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` — 污点查询（`urls.py:1019`）
- `PUT /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` — 污点更新
- `GET /console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/container` — 容器存储查询（`urls.py:1022`）

### 新增 RainbondComponentsController

- `GET /console/enterprise/{enterprise_id}/regions/{region_name}/rbd-components` — Rainbond 平台组件列表（`urls.py:1005`）

### 业务规则迁移

按 `console/views/enterprise.py:911-985`：

- **NodeAction**：限定 5 种 action（`unschedulable` / `reschedulable` / `down` / `up` / `evict`），不在白名单内返 400 + `暂不支持当前操作`
- **NodeLabelsOperate.PUT**：body 形如 `{"labels": {"key1":"v1","key2":"v2"}}`，labels 字段是 `Map<String, String>`，service 层取出转给 region
- **NodeTaintOperate.PUT**：body 形如 `{"taints": [{"key":"k","value":"v","effect":"NoSchedule"}]}`，taints 字段是 `List<Map<String,Object>>`
- **ContainerDisk**：query 参数 `container_runtime` 形如 `containerd://1.6.20`，业务层取 `:` 前的 `containerd` 段作为 `container_type` 传给 region；响应字段 `total` / `used` 字节数除以 `1024^3` 转 GiB（rainbond 端 view 内做的转换，不在 region 端做）
- **RainbondComponents**：返回 list 形状直接透传 region

### 不在本 change 内（明确推迟 / 切出）

- 业务级"节点列表富化"（聚合节点 metrics / Pod 数 / rainbond 组件分布）→ 独立 hardening 或 `migrate-console-monitor-extras` 联动
- 节点上 Pod 列表 / Workload 详情 → `migrate-console-resource-center`
- 节点新增 / 删除（add node / remove node 全生命周期）→ rainbond Python 也未实现（这是 RKE2 / 集群 install 流程范畴）
- KubeBlocks 集群 manage（`manage_cluster_status` 实际是 KubeBlocks 集群操作） → `migrate-console-kubeblocks`

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "节点运维与 Rainbond 平台组件查询"。覆盖 7 endpoint 的契约（其中 6 个挂在 `ClusterNodesController` 上扩展，1 个新建 `RainbondComponentsController`）、7 个 region API 的 URL 与 body 形状、NodeAction 5 种 action 白名单、ContainerDisk 字节单位转换、与 cluster-extras / resource-center / kubeblocks 子 change 的解耦边界。

## Impact

- **代码新增**：
  - 接口：`ClusterNodeOperations` 7 method（`modules/region/api/`）
  - 实现：`ClusterNodeOperationsImpl @Primary`
  - controller：`RainbondComponentsController`（新）+ `ClusterNodesController` 扩 6 个端点（修改既有类）
  - service：`NodeService`（含 NodeAction 白名单校验、ContainerDisk 单位转换）
  - 单测：`ClusterNodeOperationsImplTest` + 5 controller 集成测试
- **数据库**：无变更
- **依赖**：不引入新 maven 依赖
- **跨 change 衔接**：
  - 软依赖 `cluster-extras` 完成（本 change 在 `ClusterNodesController` 扩展端点）；如未完成，本 change 内同时建 controller 也行，但建议路线顺序
  - `resource-center` 子 change 后续在节点维度查 Pod 列表时复用 `ClusterOperations.getNodeDetail`（cluster-extras 落地的）
- **不影响**：rainbond-console（仍可独立跑 7070）、其他已迁移 change
- **路径变量**：路径中 `{enterprise_id}` / `{region_name}` / `{node_name}` 全部 snake_case；`node_name` 允许带 `.` 与 `-`（rainbond Python 正则 `[\w\-.]+`）
