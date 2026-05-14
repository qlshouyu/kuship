# Design — migrate-console-cluster-nodes

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P0 #4**，路线表估 **12 method**。实际精算 **7 region method + 7 endpoint**，偏差原因见决策 1。归档时反向更新路线表对应行，且在 design.md 注明实际 method 数与原估值的差异说明。

依赖：软依赖 `migrate-console-cluster-extras`（在其新建的 `ClusterNodesController` 上扩展端点）。建议先 cluster-extras 后本 change，确保不打架。

## 决策 1 — 路线估值偏差说明

路线 design.md 把本子 change 估为 12 method，理由是把 `get_resource_center_*`（节点维度）/ `manage_cluster_status` 等也纳入。实施期复盘后：

- `manage_cluster_status` 是 **KubeBlocks 集群** 操作，归 `migrate-console-kubeblocks` 子 change（已在 P1）
- `get_resource_center_*` 是 **Pod / Workload 维度**，按命名空间过滤而非节点，归 `migrate-console-resource-center`
- "节点上 Pod 列表" 由 resource-center 提供（同时支持按节点 / 按 namespace 过滤）

**修订**：本 change scope 锁定为 7 region method + 7 endpoint，偏差从 12 → 7 应记入归档时的路线表更新。这是预期的"实施期收紧"，不破坏路线规约（路线规约要求"偏差 > 30% 时 design 解释原因"，本次偏差 ~40%，已在此说明）。

## Region API URL 表

| method                                          | HTTP   | 路径                                                          | rainbond 锚点                            |
|------------------------------------------------|--------|---------------------------------------------------------------|------------------------------------------|
| operateNodeAction(rn, name, action)            | POST   | `/v2/cluster/nodes/{node_name}/action/{action}`               | `regionapi.py:2688-2695`                  |
| getNodeLabels(rn, name)                        | GET    | `/v2/cluster/nodes/{node_name}/labels`                        | `regionapi.py:2697-2704`                  |
| updateNodeLabels(rn, name, body)               | PUT    | `/v2/cluster/nodes/{node_name}/labels`                        | `regionapi.py:2706-2713`                  |
| getNodeTaints(rn, name)                        | GET    | `/v2/cluster/nodes/{node_name}/taints`                        | `regionapi.py:2715-2722`                  |
| updateNodeTaints(rn, name, body)               | PUT    | `/v2/cluster/nodes/{node_name}/taints`                        | `regionapi.py:2724-2731`                  |
| getRainbondComponents(rn)                      | GET    | `/v2/cluster/rbd-components`                                  | `regionapi.py:2733-2740`                  |
| getContainerDisk(rn, containerType)            | GET    | `/v2/container_disk/{container_type}`                         | `regionapi.py:2742-2749`                  |

注意 `getContainerDisk` 路径前缀是 `/v2/container_disk/{type}` 不在 `/v2/cluster/...` 下，是 rainbond 历史路径。本 change 1:1 透传，不修复路径风格。

## Controller 路径锚点

| Controller                          | path                                                                                  | method     | rainbond 锚点         | 来源     |
|-------------------------------------|---------------------------------------------------------------------------------------|------------|-----------------------|----------|
| ClusterNodesController（扩展）       | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action`  | POST       | `urls.py:1013` `NodeAction` | cluster-extras 已建，本 change 追加 |
| ClusterNodesController              | `.../nodes/{node_name}/labels`                                                        | GET / PUT  | `urls.py:1016` `NodeLabelsOperate` | 同上 |
| ClusterNodesController              | `.../nodes/{node_name}/taints`                                                        | GET / PUT  | `urls.py:1019` `NodeTaintOperate`  | 同上 |
| ClusterNodesController              | `.../nodes/{node_name}/container`                                                     | GET        | `urls.py:1022` `ContainerDisk` | 同上 |
| RainbondComponentsController（新）   | `/console/enterprise/{enterprise_id}/regions/{region_name}/rbd-components`             | GET        | `urls.py:1005` `RainbondComponents` | 本 change 新建 |

trailing slash 兼容：每 endpoint 同时声明 `path` 与 `path/`。

## 决策 2 — Controller 端点放在 cluster-extras 已建类上扩展

cluster-extras 已建 `ClusterNodesController` 含 GET `/nodes` 和 GET `/nodes/{node_name}` 两个端点。本 change 在同一类上追加 6 个新端点，所有节点相关操作集中维护，URL 路径连续。

避免新建 `NodeActionController` / `NodeLabelsController` / `NodeTaintsController` 三个分开的 controller —— 那会让节点功能散落，未来加端点时容易选错位置。

唯一例外：`RainbondComponents` 不在 `/nodes/...` 路径下，建独立 controller。

## 决策 3 — `NodeAction` 5 种 action 白名单

rainbond `enterprise.py:NodeAction.post` 白名单是 `["unschedulable", "reschedulable", "down", "up", "evict"]`：

- `unschedulable` / `reschedulable` —— K8s `kubectl cordon` / `uncordon`，禁止 / 允许新 Pod 调度
- `down` / `up` —— 节点电源状态（rainbond 自定义概念，可能需要 IPMI 配合）
- `evict` —— 驱逐节点上所有 Pod（类似 `kubectl drain`）

**决策**：kuship 端 service 层做 5 种白名单校验，不在白名单内抛 `ServiceHandleException(400, "unsupported node action: " + action, "暂不支持当前操作")`。region API 不再做二次校验（避免 400 来自两层）。

未来扩展白名单（如 K8s `taint` 等）时，只改 service 层 set，不动 controller / region 接口。

## 决策 4 — `ContainerDisk` 字节单位转换

rainbond view 把 region 返回的 `total` / `used` 字节数除以 `1024^3` 转成 GiB 浮点数：

```python
res = {
    "path": container_disk["path"],
    "total": container_disk["total"] / 1024 / 1024 / 1024,
    "used": container_disk["used"] / 1024 / 1024 / 1024
}
```

**决策**：kuship 端在 `NodeService.getContainerDisk` 做同样转换，**不**在 controller 暴露原始字节数。前端 UI 期望 GiB 浮点数（rainbond-ui 也是这么读的），改字节会破坏 UI。

精度：用 `double` 保留至少 2 位小数（rainbond Python 是无限精度浮点）。

## 决策 5 — `container_runtime` query 参数解析

rainbond ContainerDisk view 接收 query `container_runtime=containerd://1.6.20`，内部 `split(":")[0]` 取得 `containerd` 作为 `container_type` 传给 region API。

**决策**：kuship 端 controller 层做同样解析，service 收 `containerType` 已分割后的 string。container_type 取值是 `containerd` / `docker` 等枚举集，不在本 change 限定枚举（与 rainbond 一致开放）。

如果 `container_runtime` query 缺失或不含 `:`，按 rainbond 行为传空字符串给 region —— region 端自行处理或返 400。

## 决策 6 — `update_node_labels` / `update_node_taints` body 形状

rainbond view：

```python
labels = request.data.get("labels", {})
res, body = region_api.update_node_labels(region_name, node_name, labels)
```

view 把 `body.labels` 直接当 region body 用。region 端期望的 body 是 `{...labels...}`（直接 K-V map），不是 `{labels: {...}}`。

**决策**：kuship controller 端接 `@RequestBody Map<String, Object>`，service 层取 `body.get("labels")` 转给 region。**不强制 typed DTO**（labels schema 灵活，未来可能加注解 / annotations）。

`update_node_taints` 同理：controller 接 body，取 `body.get("taints")` （`List<Map<String,Object>>`）传给 region。

## 决策 7 — 错误处理

7 个 region method 都简单透传，region 异常走 `RegionApiException` + `GlobalExceptionHandler`。

`NodeAction` 白名单失败抛 `ServiceHandleException(400, ...)`，业务异常路径，与 rainbond 一致。

`ContainerDisk` 字节转换不抛异常 —— 缺字段时返 0（rainbond 行为：缺字段时 KeyError 会让请求 500，本 change 改为 null-safe 防御，更稳健）。

## 决策 8 — 权限控制

rainbond 端这 7 view 全部 `EnterpriseAdminView`（企业管理员才能操作）。

**决策**：kuship 端 controller 用 `@RequireEnterpriseAdmin` 注解（与 `EnterpriseUserController` 等已有用法一致）。非企业管理员调用返 403 + `需要企业管理员权限`。

`RainbondComponents` 用 GET 也需企业管理员（rainbond 一致）。

## 非决策（明确不做）

- **不**新建 `NodeAction` / `NodeLabel` / `NodeTaint` 等本地 entity（节点状态全部走 region 实时，无本地表）
- **不**做"节点状态变化通知"（如 NodeAction 完成后推送事件给前端）—— 留给 hardening
- **不**做"批量节点操作"（一次 cordon 多个节点）—— rainbond 也是单节点 API，不在本 change 扩展
- **不**修复 `/v2/container_disk/{type}` 路径不在 `/v2/cluster/` 下的不一致（这是 region 端路径，console 端透传不动）

## 测试约定

集成测试覆盖：

- `ClusterNodesControllerNodeActionTest`：
  - 5 种 action 各 1 happy path（mock `ClusterNodeOperations.operateNodeAction` 返成功）
  - 不在白名单的 action（如 `delete`）→ 400
  - region 5xx 透传
- `ClusterNodesControllerLabelsTest`：
  - GET 返 region bean
  - PUT body=`{"labels":{"foo":"bar"}}`，断言 region 入参是 `{"foo":"bar"}` 而非 `{"labels":{"foo":"bar"}}`
- `ClusterNodesControllerTaintsTest`：
  - PUT body=`{"taints":[{key,value,effect}]}`，断言 region 入参是 list
  - GET 返 region list
- `ClusterNodesControllerContainerDiskTest`：
  - query `container_runtime=containerd://1.6.20`，断言 region 入参 `container_type=containerd`
  - 字节单位转换：region 返 `total=1073741824`（1 GiB），断言响应 `total=1.0`
  - 缺 query 参数 → region 入参 `container_type=""` 不抛异常
- `RainbondComponentsControllerTest`：透传 list；权限校验非企业管理员 → 403

`@MockitoBean ClusterNodeOperations` 替换 region 调用，断言入参 nodeName / action / body 形状。

`ClusterNodeOperationsImplTest`：
- 7 method 各 1 happy + 1 region 5xx 透传
- `MockRestServiceServer` 断言 URL 路径 + body / query 形状
- `getContainerDisk` 路径不在 `/v2/cluster/` 下的 URL 形状校验
