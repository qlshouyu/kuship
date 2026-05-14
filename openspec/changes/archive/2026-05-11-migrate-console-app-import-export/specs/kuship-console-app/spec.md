# kuship-console-app

## ADDED Requirements

### Requirement: 应用模板与 yaml 资源 import/export 透传（migrate-console-app-import-export）

kuship-console 后端 SHALL 落地应用模板（rainbond-app-yaml）和 k8s yaml 资源的 import/export 能力，覆盖 rainbond `views/center_pool/app_import.py` + `app_export.py` + `views/yaml_resource.py` + `regionapi.py:1538-1670 + 2039-2065 + 704` 中 22 个 region method，分 6 子域：app export（2）/ app import（10）/ app upload（4）/ load tar image（1）/ helm chart import（1）/ yaml resource（3）。

本 Requirement 是母路线图 [`migrate-region-coverage-roadmap`](../../../migrate-region-coverage-roadmap/) 表中 **P2 #1** 行的细化契约（22 method 与路线图估计完全一致；本 change 是 P2 段最大子 change，建议单独安排 1-2 周迭代实施）。

#### Scenario: 应用模板导出（trigger）

- **WHEN** 客户端调 POST `/console/enterprise/{enterprise_id}/app-models/export` body `{"app_key": "...", "app_versions": [...], "format": "rainbond-app | docker-compose"}`
- **THEN** 后端 SHALL 调 region POST `/v2/app/export` 透传 body
- **AND** SHALL 写本地 `app_export_record`（status = `init`）
- **AND** 响应 200 + `bean` 含 region 返回的 `event_id` + 本地 record id

#### Scenario: 应用模板导出状态轮询

- **WHEN** 客户端调 GET `/console/enterprise/{enterprise_id}/app-models/export/{event_id}/status`
- **THEN** 后端 SHALL 调 region GET `/v2/app/export/{event_id}` 拿真相
- **AND** SHALL reconcile 本地 `app_export_record.status` 与 region 真相
- **AND** 响应 200 + `bean = {status, file_path?, error_msg?}`

#### Scenario: 应用模板导入初始化

- **WHEN** 客户端调 POST `/console/enterprise/{enterprise_id}/app-models/import`（enterprise scope）或 POST `/console/teams/{tn}/app-models/import`（team scope）body `{...}`
- **THEN** 后端 SHALL 调对应 region method（`import_app_2_enterprise` 或 `import_app`）
- **AND** SHALL 写本地 `app_import_record`（status = `init`）
- **AND** 响应 200 + `bean` 含 region 返回的 `event_id`

#### Scenario: 应用模板导入状态轮询

- **WHEN** 客户端调 GET `/console/enterprise/{enterprise_id}/app-models/import/{event_id}`
- **THEN** 后端 SHALL 调 region GET `/v2/app/import/ids/{event_id}`（enterprise）或 `/v2/app/import/{event_id}`（team）
- **AND** region 状态 `failed` SHALL 仍返回 200 + bean 含 `error_msg`（不抛异常）
- **AND** event 不存在 SHALL 返回 404 + msg `"导入事件不存在"`

#### Scenario: 应用模板导入取消与目录清理

- **WHEN** 客户端调 DELETE `/console/enterprise/{enterprise_id}/app-models/import/{event_id}`
- **THEN** 后端 SHALL 调 region DELETE 删除事件 + region DELETE 删除文件目录
- **AND** SHALL 删除本地 `app_import_record` 行
- **AND** region 404 兼容仍删除本地行

#### Scenario: 上传文件目录管理（4 HTTP method 同 URL）

- **WHEN** 客户端调 POST/GET/DELETE/PUT `/console/teams/{tn}/app-upload/events/{event_id}`
- **THEN** 后端 SHALL 按 HTTP method 调对应 region method（`create_upload_file_dir` / `get_upload_file_dir` / `delete_upload_file_dir` / `update_upload_file_dir`）
- **AND** PUT 时 SHALL 在 URL 末尾追加 `/component_id/{component_id}` 段（rainbond 真相）

#### Scenario: 加载 tar 镜像

- **WHEN** 客户端调 POST `/console/teams/{tn}/app/load_tar_image` body `{...}`
- **THEN** 后端 SHALL 调 region POST `/v2/app/load_tar_image`
- **AND** 响应 200 + `bean = {load_status, image_list?}`

#### Scenario: Helm chart 资源导入

- **WHEN** 客户端调 POST `/console/teams/{tn}/import_upload_chart_resource` body
- **THEN** 后端 SHALL 调 region POST `/v2/helm/import_upload_chart_resource`
- **AND** 响应 200 + region 返回 bean

#### Scenario: yaml 资源解析（resource-name + resource-detailed）

- **WHEN** 客户端调 POST `/console/teams/{tn}/resource-name` body `{"yaml_content": "..."}`
- **THEN** 后端 SHALL 调 region GET `/v2/cluster/yaml_resource_name?eid={eid}`（GET with body 模式）
- **AND** 响应 200 + `bean.resources = [{name, kind, ...}]`

- **WHEN** 客户端调 POST `/console/teams/{tn}/resource-detailed` body
- **THEN** 后端 SHALL 调 region GET `/v2/cluster/yaml_resource_detailed?eid={eid}`
- **AND** 响应 200 + `bean = {parsed yaml details}`

#### Scenario: yaml 资源导入

- **WHEN** 客户端调 POST `/console/enterprise/{eid}/regions/{rn}/yaml-resource-import` body `{"yaml_content": "...", "namespace": "..."}`
- **THEN** 后端 SHALL 调 region POST `/v2/cluster/yaml_resource_import?eid={eid}` 透传
- **AND** region timeout 容错：超时 SHALL 透传 504 + msg `"yaml 导入超时,请稍后查询导入状态"`

#### Scenario: 全部 enterprise scope 端点要求企业管理员权限

- **WHEN** 任何 enterprise scope endpoint 被非 enterprise admin 用户访问
- **THEN** 后端 SHALL 返回 403
- **AND** 由 `@RequireEnterpriseAdmin` 注解驱动

#### Scenario: 全部 team scope 端点要求 APP_OVERVIEW_CREATE 权限

- **WHEN** 任何 team scope endpoint 被无 `APP_OVERVIEW_CREATE` 权限用户访问
- **THEN** 后端 SHALL 返回 403
- **AND** 由 `@RequirePerm(PermCode.APP_OVERVIEW_CREATE)` 驱动

#### Scenario: 路线图位置可追溯

- **WHEN** 团队成员看到本 Requirement
- **THEN** SHALL 在 `kuship-console/CLAUDE.md` "Region API 覆盖度路线" 表 P2 #1 行 + 本 spec 文件头部找到完整路线图引用
- **AND** SHALL 知道本 change 与 P1 #2 `migrate-console-app-share` 边界：share 是发布到 marketplace；本 change 是 yaml/tar 包导入导出，**两者解耦**
- **AND** SHALL 知道本 change 与 P2 #5 `migrate-console-backup-extras` 边界：backup 是数据备份；本 change 是模板导入导出，**两者解耦**
- **AND** SHALL 不与其它 P0/P1/P2 子 change 的 region URL 前缀重叠（本 change 唯一前缀：`/v2/app/export*` + `/v2/app/import*` + `/v2/app/upload*` + `/v2/app/load_tar_image` + `/v2/helm/import_upload_chart_resource` + `/v2/cluster/yaml_resource*`）
