# 集群节点管理（migrate-console-cluster-nodes）

`cn.kuship.console.modules.region.controller.cluster` 落地 K8s 集群节点查询与操作的 7 个端点，
对齐 rainbond-console `GetNodes / GetNode / NodeAction / NodeLabelsOperate / NodeTaintOperate`。

## 端点表

| 方法 | 路径 | Python 锚点 | 鉴权 |
|------|------|------------|------|
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes` | `GetNodes.get` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}` | `GetNode.get` | `@RequireEnterpriseAdmin` |
| POST | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action` | `NodeAction.post` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` | `NodeLabelsOperate.get` | `@RequireEnterpriseAdmin` |
| PUT  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` | `NodeLabelsOperate.put` | `@RequireEnterpriseAdmin` |
| GET  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` | `NodeTaintOperate.get` | `@RequireEnterpriseAdmin` |
| PUT  | `/console/enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` | `NodeTaintOperate.put` | `@RequireEnterpriseAdmin` |

## 模块结构（新增/修改）

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

## 节点状态计算（对齐 Python）

```java
// conditions 数组推导：type=Ready && status=True → "Ready"；否则 "NotReady"
// unschedulable=true → 追加 ",SchedulingDisabled"
```

## NodeAction 白名单

```java
Set.of("unschedulable", "reschedulable", "down", "up", "evict")
```

未知 action → `ServiceHandleException(400, ...)` → HTTP 400

## Region API 路径

```
GET  /v2/cluster/nodes
GET  /v2/cluster/nodes/{node_name}/detail
POST /v2/cluster/nodes/{node_name}/action/{action}
GET  /v2/cluster/nodes/{node_name}/labels
PUT  /v2/cluster/nodes/{node_name}/labels
GET  /v2/cluster/nodes/{node_name}/taints
PUT  /v2/cluster/nodes/{node_name}/taints
```

## 响应格式

- `GET /nodes`：`bean={cluster_role_count}，list=[节点列表]`（对齐 Python `bean=cluster_role_count, list=nodes`）
- `GET /nodes/{node_name}`：`bean={节点详情 14 个字段}`
- `POST .../action`：`bean={}`
- `GET/PUT .../labels`：`bean={labels:{...}}`（透传 region bean）
- `GET/PUT .../taints`：`list=[{key,effect,...}]`（透传 region list）

## 幂等性说明

- `cordon（unschedulable）`：幂等，已 cordon 的节点再次 cordon region 返回 200
- `evict`：幂等，region API 自身保证幂等
- 危险操作（drain/evict）权限：严格要求 `@RequireEnterpriseAdmin`，无团队内成员权限可绕过

## ClusterOperations 接口新增 method（不影响已有 8 个 method）

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

## 测试

- 单测：`ClusterOperationsNodeTest`（MockRestServiceServer，8 个 case）
- 集成测试：`ClusterNodesIntegrationTest`（@SpringBootTest + @MockitoBean，13 个 case）
  - 注意：mock 配置在 `@BeforeEach` 中（因为 @MockitoBean 每次测试方法后 reset）
