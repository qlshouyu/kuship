# kuship-console-app

## ADDED Requirements

### Requirement: 组件 node label 绑定与可用 label 列表（migrate-console-service-labels）

kuship-console 后端 SHALL 落地组件 node label 绑定能力，覆盖 rainbond `services/app_config/label_service.py` + `regionapi.py:337-388` 中 4 个 region method（`get_region_labels` / `addServiceNodeLabel` / `deleteServiceNodeLabel` / `update_service_state_label`），并在本地 `service_labels` 表持久化组件 ↔ label 关联关系。

本 Requirement 是母路线图 [`migrate-region-coverage-roadmap`](../../../migrate-region-coverage-roadmap/) 表中 **P2 #4** 行的细化契约。

#### Scenario: 列出组件已绑定的 node label

- **WHEN** 客户端调 GET `/console/teams/{team_name}/apps/{service_alias}/labels`
- **THEN** 后端 SHALL 仅查本地 `service_labels` 表，按 `service_id = service.serviceId` 返回 `label_id` 列表
- **AND** SHALL NOT 调 region（避免每次列页面慢）
- **AND** UI 端用 `labels/available` 接口的返回拼 label_alias / label_name 显示

#### Scenario: 给组件添加 node label

- **WHEN** 客户端调 POST `/console/teams/{team_name}/apps/{service_alias}/labels` body `{"label_ids": ["x", "y"]}`
- **THEN** 后端 SHALL 在 `@Transactional` 内：
  - 先本地批量 INSERT `TenantServiceLabel`（按 service_id + label_id 唯一去重）
  - 再调 region POST `/v2/tenants/{tenant_name}/services/{service_alias}/label` body 透传
- **AND** region 失败 SHALL 回滚本地 INSERT（事务一致性）
- **AND** body 中 `label_ids` 为空 SHALL 返回 400 `"label_ids is empty"`，不调 region

#### Scenario: 删除组件 node label

- **WHEN** 客户端调 DELETE `/console/teams/{team_name}/apps/{service_alias}/labels` body `{"label_id": "x"}`
- **THEN** 后端 SHALL 先调 region DELETE `/v2/tenants/{tenant_name}/services/{service_alias}/label`（DELETE with body）
- **AND** region 成功后 SHALL 本地 DELETE `service_labels` WHERE service_id AND label_id
- **AND** region 返回 404 时（label 已不存在）SHALL 仍删除本地行
- **AND** region 5xx 时 SHALL 抛 RegionApiException，**不删本地**（避免脏数据）

#### Scenario: 列出 region 端所有可用 label

- **WHEN** 客户端调 GET `/console/teams/{team_name}/apps/{service_alias}/labels/available`
- **THEN** 后端 SHALL 调 region GET `/v2/resources/labels`
- **AND** 返回 `bean.list = [{label_id, label_alias, label_name, category}, ...]`
- **AND** region 调用失败 SHALL fallback 返回空列表 + 200 状态（不阻塞 UI 展示）

#### Scenario: 内部调用：更新组件有无状态 label（不暴露 controller endpoint）

- **WHEN** 其它服务（如 OS 切换 / 有无状态切换）调用 `ServiceLabelOperations.updateServiceStateLabel(...)`
- **THEN** SHALL 走 region PUT `/v2/tenants/{tenant_name}/services/{service_alias}/label`
- **AND** 本 Requirement SHALL NOT 在 `AppLabelController` 暴露独立 endpoint（rainbond 也未暴露）

#### Scenario: 路线图位置可追溯

- **WHEN** 团队成员看到本 Requirement
- **THEN** SHALL 在 `kuship-console/CLAUDE.md` "Region API 覆盖度路线" 表 P2 #4 行 + 本 spec 文件头部找到完整路线图引用
- **AND** SHALL 知道本 change 硬依赖 P0 #4 `migrate-console-cluster-nodes` 提供的 node label 数据
- **AND** SHALL 不与其它 P0/P1/P2 子 change 的 region URL 前缀重叠（本 change 唯一前缀：`/v2/resources/labels` + `/v2/tenants/{tn}/services/{alias}/label`）
