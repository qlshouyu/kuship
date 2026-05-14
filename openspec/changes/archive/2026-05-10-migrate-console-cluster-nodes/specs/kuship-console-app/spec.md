## ADDED Requirements

### Requirement: 节点运维与 Rainbond 平台组件查询

kuship-console SHALL 提供与 rainbond `console/views/enterprise.py:911-985` 等价的节点运维能力，覆盖 7 个 endpoint（其中 6 个挂在 `cluster-extras` 已建的 `ClusterNodesController` 上扩展，1 个新建 `RainbondComponentsController`），路径与 rainbond `console/urls/__init__.py:1005,1013,1016,1019,1022` 严格对齐（路径变量 snake_case），响应形状沿用 general_message。所有写操作 MUST 走 `ClusterNodeOperations` 接口（新建于 `modules/region/api/`），与 `ClusterOperations` 透传层解耦。所有端点 MUST 通过 `@RequireEnterpriseAdmin` 校验。

业务规则：

- `POST /nodes/{node_name}/action` 的 `action` 字段 MUST 在白名单 `{"unschedulable","reschedulable","down","up","evict"}` 内，否则返 400 + `暂不支持当前操作`
- `PUT /nodes/{node_name}/labels` body=`{"labels":{...}}` 时，service 层 MUST 取 `body.labels` 作为 region 入参（不是整 body）
- `PUT /nodes/{node_name}/taints` body=`{"taints":[...]}` 时同样取 `body.taints` 作为 region 入参
- `GET /nodes/{node_name}/container` 的 `container_runtime` query 参数（如 `containerd://1.6.20`）MUST 由 controller 切 `:` 取前段作为 region 调用参数 `container_type`；缺失或不含 `:` 时传空字符串
- `GET /nodes/{node_name}/container` 响应字段 `total` / `used` MUST 由 service 层从字节数除以 `1024^3` 转 GiB（double）；缺字段时返 0.0 不抛异常
- region 异常 MUST 透传 `RegionApiException`，由 `GlobalExceptionHandler` 自动映射为 general_message

#### Scenario: 合法节点操作

- **GIVEN** 企业管理员，节点 `worker-01` 存在
- **WHEN** `POST /console/enterprise/abc/regions/rainbond/nodes/worker-01/action` body=`{"action":"unschedulable"}`
- **THEN** kuship 调 region `POST /v2/cluster/nodes/worker-01/action/unschedulable`
- **AND** 响应 200 + `data.bean`（透传 region）

#### Scenario: 非法节点操作

- **WHEN** body=`{"action":"delete"}`（不在白名单）
- **THEN** 响应 400 + `msg_show=暂不支持当前操作`，未发起 region 调用

#### Scenario: 非企业管理员调用

- **GIVEN** 普通团队成员
- **WHEN** 任一节点操作 endpoint
- **THEN** 响应 403 + `msg_show=需要企业管理员权限`

#### Scenario: 节点标签更新 body 解构

- **WHEN** `PUT /nodes/worker-01/labels` body=`{"labels":{"node-role":"worker","zone":"us-west-1a"}}`
- **THEN** kuship 调 region `PUT /v2/cluster/nodes/worker-01/labels` body=`{"node-role":"worker","zone":"us-west-1a"}`（去掉外层 `labels` key）
- **AND** 响应 200 + `data.bean`

#### Scenario: 节点标签 body 缺 labels 字段

- **WHEN** `PUT /nodes/worker-01/labels` body=`{}`
- **THEN** kuship 调 region `PUT /v2/cluster/nodes/worker-01/labels` body=`{}`（默认空 map）
- **AND** 不抛异常

#### Scenario: 节点污点更新

- **WHEN** `PUT /nodes/worker-01/taints` body=`{"taints":[{"key":"dedicated","value":"gpu","effect":"NoSchedule"}]}`
- **THEN** kuship 调 region body=`[{"key":"dedicated","value":"gpu","effect":"NoSchedule"}]`（去外层 `taints` key，得到 list）

#### Scenario: 容器存储查询 + 字节转 GiB

- **GIVEN** region 端 `/v2/container_disk/containerd` 返 `{path:"/var/lib/containerd",total:1073741824,used:536870912}`
- **WHEN** `GET /nodes/worker-01/container?container_runtime=containerd://1.6.20`
- **THEN** kuship 调 region `GET /v2/container_disk/containerd`（取 `:` 前段）
- **AND** 响应 200 + `data.bean = {path:"/var/lib/containerd",total:1.0,used:0.5}`（字节转 GiB）

#### Scenario: 容器存储查询 缺字段降级

- **GIVEN** region 返 `{path:"/x"}` 缺 total / used
- **WHEN** 调用同上
- **THEN** 响应 200 + `data.bean = {path:"/x",total:0.0,used:0.0}` 不抛异常

#### Scenario: container_runtime 缺失

- **WHEN** `GET /nodes/worker-01/container`（无 query 参数）
- **THEN** kuship 调 region `GET /v2/container_disk/`（container_type 空字符串）
- **AND** region 返回什么响应什么（透传）

#### Scenario: Rainbond 平台组件查询

- **WHEN** `GET /console/enterprise/abc/regions/rainbond/rbd-components`
- **THEN** kuship 调 region `GET /v2/cluster/rbd-components`
- **AND** 响应 200 + `data.list`（透传 region 的 list 字段，含 rbd-api / rbd-worker / rbd-monitor 等组件信息）

#### Scenario: 节点名含点号

- **GIVEN** 节点名 `worker-01.example.com`
- **WHEN** 任一 `/nodes/{node_name}/...` 端点
- **THEN** kuship 路径变量正确解析含 `.` 的节点名，URL encode 后传给 region

#### Scenario: 与 cluster-extras / kubeblocks 子 change 的边界

- **GIVEN** 本 change 已落地
- **WHEN** 后续需要"业务级节点列表富化"（含 metrics / Pod 计数 / rainbond 组件分布）
- **THEN** SHALL 通过新增 `NodeService` method（如 `listNodesEnriched`）实现，复用 `ClusterOperations.getNodes` (cluster-extras) 与 `ClusterNodeOperations.getRainbondComponents` (本 change)，不污染既有透传层
- **AND** "节点上 Pod 列表" 由 `migrate-console-resource-center` 子 change 提供，本 change 不实现
- **AND** "KubeBlocks 集群节点操作" 由 `migrate-console-kubeblocks` 子 change 提供，与本 change 节点 action 不冲突
