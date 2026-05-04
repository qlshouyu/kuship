## ADDED Requirements

### Requirement: 团队插件 CRUD 端点

kuship-console SHALL 实现团队级插件 CRUD：`GET/POST /teams/{team_name}/plugins`、`GET /teams/{team_name}/plugins/all`、`POST /teams/{team_name}/plugins/default`、`GET/PUT/DELETE /teams/{team_name}/plugins/{plugin_id}`、`GET /teams/{team_name}/plugins/{plugin_id}/used_services` 共 8 endpoint。`TenantPlugin` Entity 落地 `tenant_plugin` 表（17 列含 `desc` 保留字反引号）。

#### Scenario: POST /teams/{team}/plugins 创建插件

- **WHEN** 调 POST body `{"plugin_name":"my-sidecar","plugin_alias":"侧车","category":"net-plugin:up","build_source":"image","image":"nginx:1.20"}`
- **THEN** kuship-console 生成 32-char `plugin_id`
- **AND** 写入 `tenant_plugin` 一行（origin=local, origin_share_id=空）
- **AND** 调 region 创建插件 → 失败回滚
- **AND** 响应 plugin_id

#### Scenario: GET /plugins/{plugin_id} 详情

- **WHEN** 调 GET
- **THEN** kuship-console 读 `tenant_plugin` + 关联读 `plugin_build_version` 当前版本，返回组合 bean

#### Scenario: GET /plugins/{plugin_id}/used_services 已挂载组件

- **WHEN** 调 GET
- **THEN** kuship-console 用 JPQL join `tenant_service_plugin_relation` + `tenant_service` 返回组件列表
- **AND** 每条含 service_id / service_alias / service_cname / plugin_status

### Requirement: 插件版本构建端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/build-history` GET + `/new-version` POST + `/version/{build_version}` GET/PUT + `/version/.../config` GET/PUT/DELETE + `/preview` GET + `/build` POST + `/status` GET + `/event-log` GET 共 10 endpoint。`PluginBuildVersion` Entity 落地 `plugin_build_version` 表（含 `build_status` + `plugin_version_status` 双状态）。

#### Scenario: POST /plugins/{plugin_id}/new-version 创建新版本

- **WHEN** 调 POST body `{"build_version":"1.1.0","update_info":"add tracing"}`
- **THEN** kuship-console INSERT `plugin_build_version` (build_status='unbuilding', plugin_version_status='unbuild')
- **AND** 响应 build_version

#### Scenario: POST /version/{ver}/build 触发构建

- **WHEN** 调 POST
- **THEN** kuship-console 调 region build → 拿 event_id
- **AND** UPDATE `plugin_build_version.event_id + plugin_version_status='building'`
- **AND** 响应 event_id

#### Scenario: GET /version/{ver}/status 查询状态

- **WHEN** 调 GET
- **THEN** kuship-console 调 region 拿状态 → UPDATE 本地 status 字段 → 返回 `{build_status, plugin_version_status}`

### Requirement: 插件配置组与配置项端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/version/{build_version}/config` GET/PUT/DELETE + `/preview` GET 共 4 endpoint。`PluginConfigGroup` + `PluginConfigItems` 双表落地（含 longtext `attr_alt_value` / `attr_default_value`）。

#### Scenario: PUT /version/{ver}/config 配置组+配置项写入

- **WHEN** 调 PUT body `{"config_groups":[{"config_name":"upstream","service_meta_type":"upstream_port","items":[{"attr_name":"weight","attr_type":"int","attr_default_value":"1"}]}]}`
- **THEN** kuship-console 在事务内 INSERT 配置组一行 + INSERT 配置项 N 行
- **AND** 旧配置先 DELETE（按 plugin_id+build_version 级联）

#### Scenario: GET /version/{ver}/preview 配置预览

- **WHEN** 调 GET
- **THEN** kuship-console 读两表组装预览结构（attrs 数组 + 描述）

### Requirement: 组件挂载插件端点

kuship-console SHALL 实现 `/teams/{team}/apps/{service_alias}/{pluginlist,plugins/{plugin_id}/{install,open,configs},analyze_plugins}` 共 5 endpoint。挂载流程操作三表：`tenant_service_plugin_relation` + `tenant_service_plugin_attr` + `service_plugin_config_var`。

#### Scenario: GET /apps/{alias}/pluginlist 已挂载列表

- **WHEN** 调 GET
- **THEN** kuship-console 读 `tenant_service_plugin_relation` 按 service_id 返回插件列表
- **AND** 每条含 plugin_name / plugin_status / build_version

#### Scenario: POST /apps/{alias}/plugins/{id}/install 挂载插件

- **WHEN** 调 POST body `{"build_version":"1.0.0","attrs":[{"attr_name":"weight","attr_value":"5"}]}`
- **THEN** kuship-console 在事务内 INSERT `relation`（plugin_status=true）+ INSERT `config_var` N 行（按 attrs 数组）
- **AND** 调 region installPlugin → 失败回滚

#### Scenario: PUT /apps/{alias}/plugins/{id}/open 启停插件

- **WHEN** 调 PUT body `{"plugin_status":false}`
- **THEN** kuship-console UPDATE `relation.plugin_status=false`
- **AND** 调 region 重启服务

#### Scenario: PUT /apps/{alias}/plugins/{id}/configs 更新配置

- **WHEN** 调 PUT body `{"attrs":[...]}`
- **THEN** kuship-console DELETE 旧 `config_var` + batch INSERT 新行
- **AND** 调 region 更新

#### Scenario: DELETE /apps/{alias}/plugins/{id}/install 卸载

- **WHEN** 调 DELETE
- **THEN** kuship-console 调 region detach → DELETE 三张表

### Requirement: 插件分享异步流程端点

kuship-console SHALL 实现 `/teams/{team}/plugins/{plugin_id}/share/record` POST + `/plugin-share/{share_id}{,/events,/events/{event_id},/complete}` 共 5 endpoint。`TenantPluginShare` Entity 落地 `tenant_plugin_share` 表（含 varchar(4096) `config`）；事件流落 `plugin_share_record_event`；状态机 6-step / 3-status 与第 9 阶段 service_share 完全同构。

#### Scenario: POST /plugins/{plugin_id}/share/record 启动分享

- **WHEN** 调 POST body `{"plugin_name":"my-sidecar","share_version":"1.0","desc":"..."}`
- **THEN** kuship-console INSERT `tenant_plugin_share`（origin_plugin_id=plugin_id，share_id 32-char UUID）
- **AND** 响应 share_id

#### Scenario: POST /plugin-share/{share_id}/events/{event_id} 推送事件

- **WHEN** 调 POST
- **THEN** kuship-console INSERT `plugin_share_record_event` 一行

#### Scenario: POST /plugin-share/{share_id}/complete 完成分享

- **WHEN** 调 POST
- **THEN** kuship-console UPDATE `tenant_plugin_share` 标记完成（用 share_version 字段映射 status）

### Requirement: 应用市场插件同步与安装端点

kuship-console SHALL 实现 `/market/plugins{,/sync,/sync-template,/uninstall-template,/install}` + `/plugins{,/installable}` + `/teams/{team}/apps/plugins` 共 8 endpoint。`RainbondCenterPlugin` Entity 落地 `rainbond_center_plugin` 表。

#### Scenario: GET /market/plugins 列出市场插件

- **WHEN** 调 GET
- **THEN** kuship-console 读 `rainbond_center_plugin` 全部记录返回

#### Scenario: POST /market/plugins/sync 同步远程市场

- **WHEN** 调 POST body `{"market_name":"local"}`
- **THEN** kuship-console（MVP）插入或更新一些占位插件；远程 HTTP 真实拉取留作 hardening

#### Scenario: POST /market/plugins/install 从市场安装到当前团队

- **WHEN** 调 POST body `{"plugin_key":"...","tenantName":"team1"}`
- **THEN** kuship-console 读市场 entity → INSERT `tenant_plugin`（origin='market'）+ 配置组复制

#### Scenario: GET /plugins/installable 列出可安装插件

- **WHEN** 调 GET
- **THEN** kuship-console 返回 `rainbond_center_plugin` 中尚未在当前团队安装过的插件

### Requirement: 平台插件 region 代理端点

kuship-console SHALL 实现 `/enterprise/{eid}/regions/{r}/{plugins,platform-plugins,platform-plugins/{id}/install,officialplugins}` + `/regions/{r}/{plugins/{name}/status,static/plugins/{name},proxy/plugins/{name}/{file_path:**},backend/plugins/{name}/{file_path:**}}` 共 8 endpoint。代理端点透传任意 path + HTTP method 给 region；超时提升到 30s（静态资源 + 冷启动场景）。

#### Scenario: GET /enterprise/{eid}/regions/{r}/plugins 列出 region 插件

- **WHEN** 调 GET
- **THEN** kuship-console 调 `RainbondPluginOperations.listPlugins(region)` 透传

#### Scenario: POST /enterprise/{eid}/regions/{r}/platform-plugins/{id}/install 安装平台插件

- **WHEN** 调 POST body
- **THEN** kuship-console 调 `RainbondPluginOperations.installPlatformPlugin(region, id, body)` 透传

#### Scenario: GET /regions/{r}/static/plugins/{name} 静态资源透传

- **WHEN** 调 GET
- **THEN** kuship-console 调 region 静态路径 → 返回 `byte[]` + 保留 Content-Type
- **AND** 单文件超过 10MB 时返回 413

#### Scenario: GET /regions/{r}/proxy/plugins/{name}/{file_path} 通用反向代理

- **WHEN** 调 GET / POST 任意 method
- **THEN** kuship-console 透传 method + path + body 至 region

### Requirement: plugin 模块 9 张表的 JPA Entity 与 Repository

kuship-console SHALL 在 `cn.kuship.console.modules.plugin.{team,service,market}.entity` 包下新增 9 个 Entity（按 schema 真相）：
1. `TenantPlugin`（tenant_plugin，17 列含 `desc` 反引号）
2. `PluginBuildVersion`（plugin_build_version，16 列双状态）
3. `PluginConfigGroup`（plugin_config_group，6 列）
4. `PluginConfigItems`（plugin_config_items，12 列含 longtext）
5. `TenantServicePluginRelation`（tenant_service_plugin_relation，8 列）
6. `TenantServicePluginAttr`（tenant_service_plugin_attr，17 列含跨服务字段）
7. `ServicePluginConfigVar`（service_plugin_config_var，11 列含 longtext attrs）
8. `TenantPluginShare`（tenant_plugin_share，17 列）
9. `PluginShareRecordEvent`（plugin_share_record_event，10 列）
10. `RainbondCenterPlugin`（rainbond_center_plugin，市场插件）

主键全部 Integer 自增；列名严格对齐 schema（特别 `desc` 保留字反引号）。

#### Scenario: ddl-auto=validate 启动通过

- **WHEN** 应用启动连真实 MySQL（rainbond docker compose）
- **THEN** Hibernate ddl-auto=validate 不报缺列 / 多列 / 错类型错误

### Requirement: PluginOperations 与 RainbondPluginOperations 两 region API 接口

kuship-console SHALL 在 `cn.kuship.console.modules.plugin.api/` 新建：
- `PluginOperations` 接口 + `PluginOperationsImpl`（@Primary @Service）：10 method 覆盖 plugin 全部 region 调用
- `RainbondPluginOperations` 接口 + `RainbondPluginOperationsImpl`（@Primary @Service）：8 method 覆盖平台插件代理 + 静态资源/后端透传

两接口都不在原 14 接口骨架中，作为本 change 新增的非骨架 region 接口。

#### Scenario: PluginOperations 10 method 全部接通

- **WHEN** controller 注入并调用 `PluginOperations.<method>(...)`
- **THEN** Impl 用 `RegionApiSupport.exchange(lambda)` 模板调对应 region path
- **AND** 不再抛 UnsupportedOperationException

#### Scenario: RainbondPluginOperations.proxyStaticResource 流式透传

- **WHEN** controller 调用 `proxyStaticResource(region, name)`
- **THEN** Impl 调 region 静态路径 → 返回 `byte[]` + Content-Type
- **AND** 不走 RegionApiResponseProcessor.extractBean 包装（直接二进制透传）

### Requirement: plugin 模块测试覆盖

kuship-console SHALL 提供至少 4 类集成测试覆盖 plugin 核心：
1. `PluginCrudIntegrationTest`：POST/GET/PUT/DELETE 团队插件 + tenant_plugin 表写入断言
2. `PluginVersionBuildIntegrationTest`：创建版本 + 配置组/配置项写入 + 状态查询
3. `ServicePluginInstallIntegrationTest`：组件挂载插件三表写入 + 启停 + 卸载
4. `PluginShareIntegrationTest`：分享异步流程（record → events → complete）+ tenant_plugin_share 状态变更

#### Scenario: 集成测试全部使用真实 MySQL

- **WHEN** 在 docker-compose 启动后跑 `mvn -Dtest='cn.kuship.console.modules.plugin.**' test`
- **THEN** 每类测试在 `@BeforeAll` 用高位 user_id（9092xx）插入 user/team 数据
- **AND** 在 `@AfterAll` 清理避免数据残留
- **AND** 全部用例通过

#### Scenario: 全套 ≥ 96 用例

- **WHEN** 跑 `mvn test`
- **THEN** 总用例数 ≥ 96（94 老 + 4 新插件）全部通过
