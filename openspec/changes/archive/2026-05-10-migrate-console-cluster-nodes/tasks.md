# Tasks: migrate-console-cluster-nodes

## 接口签名追加（ClusterOperations.java）

- [x] T1: 追加 `getClusterNodes(String regionName, String enterpriseId): Map<String, Object>`
- [x] T2: 追加 `getNodeDetail(String regionName, String enterpriseId, String nodeName): Map<String, Object>`
- [x] T3: 追加 `operateNodeAction(String regionName, String enterpriseId, String nodeName, String action): Map<String, Object>`
- [x] T4: 追加 `getNodeLabels(String regionName, String enterpriseId, String nodeName): Map<String, Object>`
- [x] T5: 追加 `updateNodeLabels(String regionName, String enterpriseId, String nodeName, Map<String, Object> labels): Map<String, Object>`
- [x] T6: 追加 `getNodeTaints(String regionName, String enterpriseId, String nodeName): List<Object>`
- [x] T7: 追加 `updateNodeTaints(String regionName, String enterpriseId, String nodeName, List<Object> taints): List<Object>`

## ClusterOperationsImpl.java 实现追加（不动已有 method）

- [x] T8: 实现 `getClusterNodes` → GET `/v2/cluster/nodes`，返回原始 region JSON（Map）
- [x] T9: 实现 `getNodeDetail` → GET `/v2/cluster/nodes/{node_name}/detail`，返回原始 region JSON（Map）
- [x] T10: 实现 `operateNodeAction` → POST `/v2/cluster/nodes/{node_name}/action/{action}`，返回 bean Map
- [x] T11: 实现 `getNodeLabels` → GET `/v2/cluster/nodes/{node_name}/labels`，返回 bean Map
- [x] T12: 实现 `updateNodeLabels` → PUT `/v2/cluster/nodes/{node_name}/labels`，body={labels}，返回 bean Map
- [x] T13: 实现 `getNodeTaints` → GET `/v2/cluster/nodes/{node_name}/taints`，返回 list
- [x] T14: 实现 `updateNodeTaints` → PUT `/v2/cluster/nodes/{node_name}/taints`，body={taints}，返回 list

## ClusterNodeService.java（新建）

- [x] T15: 创建 `ClusterNodeService`，注入 `ClusterOperations`
- [x] T16: 实现 `getNodesWithRoleCount(regionName, enterpriseId)` — 调用 getClusterNodes，计算 cluster_role_count（Map<String,Integer>），返回 ClusterNodesResult record
- [x] T17: 实现 `getNodeDetail(regionName, enterpriseId, nodeName)` — 调用 getNodeDetail，提取 bean，转换为节点详情 Map（对齐 Python 字段 status/ip/container_runtime/architecture/roles 等）
- [x] T18: 实现 `operateNode(regionName, enterpriseId, nodeName, action)` — 校验 action 白名单，调 operateNodeAction
- [x] T19: 实现 `getNodeLabels(regionName, enterpriseId, nodeName)` — 透传
- [x] T20: 实现 `updateNodeLabels(regionName, enterpriseId, nodeName, labels)` — 透传
- [x] T21: 实现 `getNodeTaints(regionName, enterpriseId, nodeName)` — 透传
- [x] T22: 实现 `updateNodeTaints(regionName, enterpriseId, nodeName, taints)` — 透传

## ClusterNodesController.java（新建，modules/region/controller/cluster/）

- [x] T23: 创建 `ClusterNodesController`，`@RestController @RequestMapping("/console")`，注入 ClusterNodeService + RegionInfoEntityRepository
- [x] T24: `GET /enterprise/{enterprise_id}/regions/{region_name}/nodes` → `@RequireEnterpriseAdmin`，调 service.getNodesWithRoleCount，返回 bean=cluster_role_count，list=nodes
- [x] T25: `GET /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}` → `@RequireEnterpriseAdmin`，返回 bean=node_detail
- [x] T26: `POST /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/action` → `@RequireEnterpriseAdmin`，body 取 action，白名单校验（400），调 service.operateNode，返回 bean={}
- [x] T27: `GET /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` → `@RequireEnterpriseAdmin`，返回 bean=labels
- [x] T28: `PUT /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/labels` → `@RequireEnterpriseAdmin`，body 取 labels，调 service.updateNodeLabels，返回 bean=labels
- [x] T29: `GET /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` → `@RequireEnterpriseAdmin`，返回 list=taints
- [x] T30: `PUT /enterprise/{enterprise_id}/regions/{region_name}/nodes/{node_name}/taints` → `@RequireEnterpriseAdmin`，body 取 taints，调 service.updateNodeTaints，返回 list=taints

## 单测（ClusterOperationsNodeTest.java）

- [x] T31: `getClusterNodes` → MockRestServiceServer 验证 GET `/v2/cluster/nodes` + 返回解析
- [x] T32: `getNodeDetail` → GET `/v2/cluster/nodes/node-1/detail`
- [x] T33: `operateNodeAction` → POST `/v2/cluster/nodes/node-1/action/unschedulable`
- [x] T34: `getNodeLabels` / `updateNodeLabels` → GET/PUT `/v2/cluster/nodes/node-1/labels`
- [x] T35: `getNodeTaints` / `updateNodeTaints` → GET/PUT `/v2/cluster/nodes/node-1/taints`

## 集成测试（ClusterNodesIntegrationTest.java）

- [x] T36: `@SpringBootTest + @MockitoBean ClusterOperations`，验证 7 个端点路径 / 鉴权 / 响应格式
- [x] T37: 未认证请求返回 401
- [x] T38: 非 enterprise admin 请求 nodes/action 返回 403
- [x] T39: NodeAction 未知 action 返回 400

## curl 验证（需用户联动验证）

- [ ] T40: `curl -H "Authorization: GRJWT <token>" localhost:8080/console/enterprise/{eid}/regions/{r}/nodes` 返回 200（需用户联动验证）
- [ ] T41: `curl -X POST ... /nodes/{node}/action -d '{"action":"unschedulable"}'` 返回 200（需用户联动验证）

## 文档

- [x] T42: 写 `CLAUDE_FRAGMENT.md`（集群节点管理章节，附加到 CLAUDE.md 用）
- [x] T43: tasks.md 打勾同步

## 编译验证

- [x] T44: `mvn -DskipTests package` 通过
