# kuship-console-app

## ADDED Requirements

### Requirement: 应用治理模式与组件 k8s 属性透传（migrate-console-governance-policy）

kuship-console 后端 SHALL 落地两域 region 调用：应用级治理模式（governance mode + governance-cr，5 method）+ 组件级 k8s 自定义属性（k8s-attributes，4 method），共 9 个 region method，覆盖 rainbond `views/group.py` + `views/k8s_attribute.py` + `regionapi.py:2319-2353` + `:2572-2598` 中所有相关能力。

本 Requirement 是母路线图 [`migrate-region-coverage-roadmap`](../../../migrate-region-coverage-roadmap/) 表中 **P2 #2** 行的细化契约（路线图估计 12 method，实际 9；偏差 -25% 在 30% 阈值内）。

#### Scenario: 列出可用治理模式

- **WHEN** 客户端调 GET `/console/teams/{team_name}/groups/{app_id}/governancemode`
- **THEN** 后端 SHALL 调 region GET `/v2/cluster/governance-mode`
- **AND** 响应 200 + `list = [{name, description, ...}]`

#### Scenario: 检查应用治理模式可行性

- **WHEN** 客户端调 GET `/console/teams/{team_name}/groups/{app_id}/governancemode/check?governance_mode={mode}`
- **THEN** 后端 SHALL 调 region GET `/v2/tenants/{tenant_name}/apps/{region_app_id}/governance/check?governance_mode={mode}`
- **AND** region 返回 412（如 mesh 未安装）SHALL 透传 412 + region 的 msg_show
- **AND** 响应 200 + `bean = {"governance_mode": mode}`

#### Scenario: 切换应用治理模式

- **WHEN** 客户端调 PUT `/console/teams/{team_name}/groups/{app_id}/governancemode` body `{"governance_mode": "...", "action": "..."}`
- **THEN** 后端 SHALL 先校验目标 mode 在 `listGovernanceMode` 返回集合内
- **AND** 通过后写本地 `app.governance_mode = mode`
- **AND** SHALL 根据 action 调 region `createGovernanceCr` / `updateGovernanceCr` / `deleteGovernanceCr` 同步落地 region CR
- **AND** 响应 200 + `bean = {governance_mode, governance_cr?}`

#### Scenario: 创建/更新/删除应用治理 CR

- **WHEN** 客户端调 POST/PUT/DELETE `/console/teams/{team_name}/groups/{app_id}/governancemode-cr`
- **THEN** 后端 SHALL 调对应 region method 透传 body
- **AND** 本地 `k8s_resources` 表（kind = `governance`）SHALL 同步落地 / 更新 / 删除

#### Scenario: 列出组件所有 k8s 属性

- **WHEN** 客户端调 GET `/console/teams/{team_name}/apps/{service_alias}/k8s-attributes`
- **THEN** 后端 SHALL 查本地 `component_k8s_attributes` 表 WHERE component_id = service.serviceId
- **AND** 响应 200 + `list = [{name, save_type, attribute_value, ...}]`

#### Scenario: 创建组件 k8s 属性

- **WHEN** 客户端调 POST `/console/teams/{team_name}/apps/{service_alias}/k8s-attributes` body `{"attribute": {name, save_type, attribute_value}}`
- **THEN** 后端 SHALL 在 `@Transactional` 内：
  - 本地 INSERT `ComponentK8sAttribute`
  - 调 region POST `/v2/tenants/{tn}/services/{alias}/k8s-attributes` body 透传
- **AND** 同名属性已存在 SHALL 返回 409 + msg `"属性名已存在"`
- **AND** region 失败 SHALL 回滚本地 INSERT

#### Scenario: 查询组件单个 k8s 属性

- **WHEN** 客户端调 GET `/console/teams/{team_name}/apps/{service_alias}/k8s-attributes/{name}`
- **THEN** 后端 SHALL 查本地 + 调 region GET `/v2/tenants/{tn}/services/{alias}/k8s-attributes`（GET with body `{"name": name}`）做 reconcile
- **AND** 响应 200 + `list = [...]`

#### Scenario: 更新组件 k8s 属性

- **WHEN** 客户端调 PUT `/console/teams/{team_name}/apps/{service_alias}/k8s-attributes/{name}` body `{"attribute": {...}}`
- **THEN** 后端 SHALL `@Transactional` 内本地 UPDATE + region PUT
- **AND** path `name` 与 body `attribute.name` 不一致 SHALL 返回 400 `"参数错误"`

#### Scenario: 删除组件 k8s 属性

- **WHEN** 客户端调 DELETE `/console/teams/{team_name}/apps/{service_alias}/k8s-attributes/{name}`
- **THEN** 后端 SHALL 调 region DELETE（DELETE with body `{"name": name}`）后本地 DELETE
- **AND** region 404 SHALL 仍删除本地（最终一致性）

#### Scenario: 路线图位置可追溯

- **WHEN** 团队成员看到本 Requirement
- **THEN** SHALL 在 `kuship-console/CLAUDE.md` "Region API 覆盖度路线" 表 P2 #2 行 + 本 spec 文件头部找到完整路线图引用
- **AND** SHALL 不与其它 P0/P1/P2 子 change 的 region URL 前缀重叠（本 change 唯一前缀：`/v2/cluster/governance-mode` + `/v2/tenants/{tn}/apps/{app_id}/governance*` + `/v2/tenants/{tn}/services/{alias}/k8s-attributes`）
