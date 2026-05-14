## ADDED Requirements

### Requirement: 集群基础信息透传

kuship-console SHALL 把 `ClusterOperations` 接口中既存的 5 个 default unsupported method（`getResources` / `getClusterInfo` / `getClusterEvents` / `getNodes` / `getNodeDetail`）落地为对 region API 的 1:1 透传实现，并提供 4 个最小化 console controller（5 endpoint）暴露给 UI。本 Requirement 同时锁定与后续 `migrate-console-cluster-nodes` / `migrate-console-resource-center` 子 change 的解耦边界：本 change 仅做 region 透传，不做业务级聚合 / 节点写操作 / 资源中心 Pod 详情；后续子 change SHALL 在 `ClusterNodesController` 上扩展端点而非替换路径，业务级富化通过新接口（`ClusterNodeOperations` 等）承载，不污染 `ClusterOperations` 透传层。

业务规则：

- `ClusterOperations.getResources(regionName, tenantName, enterpriseId)` MUST 把路径段 `tenant_name` 替换为 `Tenants.namespace`（缺失时回退 `tenant_name`），与 rainbond `regionapi.py:97` `region_tenant_name` 行为一致
- `ClusterOperations.getClusterEvents(regionName, body)` MUST 把 `body` 序列化为 URL query string（按 `&` 拼接，URL encode），不发 request body —— 因为 region 端是 GET 方法
- `ClusterOperations.getNodeDetail(regionName, nodeName)` MUST 对 `nodeName` 做 URL encode，确保节点名含 `.` / `-` / 数字时路径不被错误解析
- `ClusterOperations.getClusterInfo(regionName)` 当 region 端不支持 `/v2/cluster/info` 时 MUST 降级为读本地 `region_info` entity（注入 `RegionInfoEntityRepository`），返回 entity 字段子集；不抛 region 异常
- 5 endpoint 全部走默认 JWT 鉴权链，不进 permitAll
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message

#### Scenario: 团队资源使用查询

- **GIVEN** team `default` 的 `Tenants.namespace="my-namespace"`，enterprise_id="abc"
- **WHEN** `GET /console/teams/default/resources?region_name=rainbond&enterprise_id=abc`
- **THEN** kuship 调 region `GET /v2/tenants/my-namespace/resources?enterprise_id=abc`
- **AND** 响应 200 + `data.bean` 含 `cpu` / `memory` / `disk` 等字段（透传 region）

#### Scenario: 团队不存在

- **WHEN** `GET /console/teams/no-such-team/resources?region_name=rainbond`
- **THEN** 响应 404 + `msg_show=团队不存在`，未发起任何 region 调用

#### Scenario: 集群事件 query string 透传

- **WHEN** `GET /console/enterprise/abc/regions/rainbond/cluster-events?type=warning&since=1h`
- **THEN** kuship 调 region `GET /v2/cluster/events?since=1h&type=warning`（参数顺序按字典序或保持原顺序均可，但全部 URL encode）
- **AND** 响应原样透传 region 的 list

#### Scenario: 节点列表

- **WHEN** `GET /console/enterprise/abc/regions/rainbond/nodes`
- **THEN** kuship 调 region `GET /v2/cluster/nodes`
- **AND** 响应 200 + `data.list`（透传 region 的 list 字段）

#### Scenario: 节点详情含点号

- **GIVEN** 节点名 `worker-01.example.com`
- **WHEN** `GET /console/enterprise/abc/regions/rainbond/nodes/worker-01.example.com`
- **THEN** kuship 调 region `GET /v2/cluster/nodes/worker-01.example.com/detail`
- **AND** 响应 200 + `data.bean`

#### Scenario: 集群信息 region 端不支持时降级

- **GIVEN** region 端 `/v2/cluster/info` 返 404（未实现）
- **WHEN** `GET /console/enterprise/abc/regions/rainbond/info`
- **THEN** 响应 200 + `data.bean` 含本地 `region_info` entity 的字段子集（region_name / region_alias / url / tcpdomain / httpdomain / status）
- **AND** 不抛 region 异常

#### Scenario: region 5xx 透传

- **GIVEN** region 端在 `/v2/cluster/nodes` 返 503
- **WHEN** `GET /console/enterprise/abc/regions/rainbond/nodes`
- **THEN** 响应 503 + general_message 形状（msg/msg_show 来自 region），HTTP status code 等于 region code

#### Scenario: 与 cluster-nodes 子 change 的边界

- **GIVEN** 本 change 已落地，`ClusterNodesController` 提供 GET `/nodes` 与 `/nodes/{node_name}`
- **WHEN** `migrate-console-cluster-nodes` 子 change 后续要加 POST `/nodes/{node_name}/action`
- **THEN** 该子 change SHALL 在 `ClusterNodesController` 上**追加** `@PostMapping`，不替换 URL 路径，不动 GET 端点的 region 透传实现
- **AND** 业务级聚合（metrics / Pod 计数 / 节点上 rainbond 组件）SHALL 通过新接口 `ClusterNodeOperations` 承载，不污染 `ClusterOperations`
