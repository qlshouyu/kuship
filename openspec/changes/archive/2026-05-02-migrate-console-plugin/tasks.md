## 1. 模块基础设施

- [x] 1.1 创建包结构 `cn.kuship.console.modules.plugin/{team,service,market,platform}/{controller,service,entity,repository,dto}` + `api/`
- [x] 1.2 用 `docker exec kuship-mysql mysql ... DESC <table>` 校验 10 张表的真实列名（`tenant_plugin` / `plugin_build_version` / `plugin_config_group` / `plugin_config_items` / `tenant_service_plugin_relation` / `tenant_service_plugin_attr` / `service_plugin_config_var` / `tenant_plugin_share` / `plugin_share_record_event` / `rainbond_center_plugin`），确认 `desc` 保留字反引号
- [x] 1.3 新建 `plugin/api/RegionApiSupport.java`（与 application/appruntime/appmarket 同模板）
- [x] 1.4 新建 `plugin/service/PluginContextLoader.java`（共享"按 team_name + plugin_id 取 Tenant + TenantPlugin"的辅助）

## 2. Region API 实现

- [x] 2.1 新建 `plugin/api/PluginOperations.java` 接口（10 method：createPlugin / updatePlugin / deletePlugin / buildPlugin / getPluginBuildStatus / installToService / uninstallFromService / openOnService / syncFromMarket / installFromMarket）
- [x] 2.2 新建 `plugin/api/PluginOperationsImpl.java`（@Primary @Service）—— 用 RegionApiSupport.exchange(lambda) 模板覆盖全部 10 method
- [x] 2.3 新建 `plugin/api/RainbondPluginOperations.java` 接口（8 method：listPlugins / listPlatformPlugins / listOfficialPlugins / listObservablePlugins / installPlatformPlugin / getPluginStatus / proxyStaticResource / proxyBackend）
- [x] 2.4 新建 `plugin/api/RainbondPluginOperationsImpl.java`（@Primary @Service）—— proxyStaticResource 返回 byte[] + Content-Type；其他用 JSON 透传

## 3. team 子域 Entity + Repo

- [x] 3.1 新增 `team/entity/TenantPlugin.java`（17 列含 `desc` 反引号 + plugin_id UUID + origin/origin_share_id）
- [x] 3.2 新增 `team/entity/PluginBuildVersion.java`（16 列含 build_status + plugin_version_status 双状态）
- [x] 3.3 新增 `team/entity/PluginConfigGroup.java`（6 列）
- [x] 3.4 新增 `team/entity/PluginConfigItems.java`（12 列含 longtext attr_alt_value/attr_default_value）
- [x] 3.5 新增 `team/entity/TenantPluginShare.java`（17 列含 varchar(4096) config）
- [x] 3.6 新增 `team/entity/PluginShareRecordEvent.java`（10 列）
- [x] 3.7 新增对应 6 Repository（findByPluginId / findByTenantId / findByPluginIdAndBuildVersion 等）

## 4. team 子域 Controller

- [x] 4.1 新建 `team/controller/TenantPluginController.java`：`/teams/{team}/plugins` GET/POST + `/plugins/all` GET + `/plugins/default` POST + `/plugins/{plugin_id}` GET/PUT/DELETE + `/plugins/{plugin_id}/used_services` GET 共 8 endpoint
- [x] 4.2 新建 `team/controller/PluginBuildController.java`：`/plugins/{plugin_id}/build-history` GET + `/new-version` POST + `/version/{ver}` GET/PUT + `/build` POST + `/status` GET + `/event-log` GET 共 7 endpoint
- [x] 4.3 新建 `team/controller/PluginConfigController.java`：`/plugins/{plugin_id}/version/{ver}/config` GET/PUT/DELETE + `/preview` GET 共 4 endpoint
- [x] 4.4 新建 `team/controller/PluginShareController.java`：`/plugins/{plugin_id}/share/record` POST + `/plugin-share/{share_id}{,/events,/events/{event_id},/complete}` 共 5 endpoint（结构 copy from ServiceShareController，命名空间区分）
- [x] 4.5 创建逻辑：plugin_id 32-char UUID + 调 region create（region 失败不阻塞本地建档，方便 region 离线时仍能本地操作）

## 5. comp 子域 Entity + Controller（注意：实际包名 `comp/` 而非 `service/`，避免与 Spring `service/` 包名冲突）

- [x] 5.1 新增 `service/entity/TenantServicePluginRelation.java`（8 列）
- [x] 5.2 新增 `service/entity/TenantServicePluginAttr.java`（17 列含跨服务字段 dest_service_id/dest_service_alias）
- [x] 5.3 新增 `service/entity/ServicePluginConfigVar.java`（11 列含 longtext attrs）
- [x] 5.4 新增对应 3 Repository
- [x] 5.5 新建 `service/controller/ServicePluginController.java`：`/teams/{team}/apps/{alias}/{pluginlist,plugins/{plugin_id}/install,plugins/{plugin_id}/open,plugins/{plugin_id}/configs,analyze_plugins}` 共 5 endpoint
- [x] 5.6 install 流程：事务内 INSERT 三表 + 调 region installPlugin → 失败回滚
- [x] 5.7 uninstall 流程：调 region detach → DELETE 三表
- [x] 5.8 configs PUT：DELETE 旧 service_plugin_config_var + batch INSERT 新行 + 调 region update

## 6. market 子域 Entity + Controller

- [x] 6.1 新增 `market/entity/RainbondCenterPlugin.java`（按 schema 真相，列含 plugin_key + plugin_template + 版本控制）
- [x] 6.2 新增对应 Repository
- [x] 6.3 新建 `market/controller/MarketPluginController.java`：`/market/plugins{,/sync,/sync-template,/uninstall-template,/install}` + `/plugins{,/installable}` + `/teams/{team}/apps/plugins` 共 8 endpoint
- [x] 6.4 sync MVP：占位 INSERT/UPDATE rainbond_center_plugin（远程 HTTP 拉取留作 hardening）
- [x] 6.5 install：MVP 仅复制元数据到 tenant_plugin（origin='market'）+ 配置组复制；深度安装留作 hardening

## 7. platform 子域 Controller

- [x] 7.1 新建 `platform/controller/RegionPluginController.java`：`/enterprise/{eid}/regions/{r}/{plugins,platform-plugins,officialplugins}` GET + `/platform-plugins/{id}/install` POST 共 4 endpoint
- [x] 7.2 新建 `platform/controller/RainbondPluginProxyController.java`：`/regions/{r}/{plugins/{name}/status,static/plugins/{name},proxy/plugins/{name}/{file_path:**},backend/plugins/{name}/{file_path:**}}` 共 4 endpoint
- [x] 7.3 静态资源代理：返回 byte[] + Content-Type；超过 10MB 返回 413；timeout 30s
- [x] 7.4 通用反向代理：透传任意 HTTP method + body 至 region

## 8. 启动校验 + 文档

- [x] 8.1 跑 `mvn -pl kuship-console clean compile` 验证 0 编译错误
- [x] 8.2 在 `kuship-console/CLAUDE.md` 新增"插件系统（migrate-console-plugin）"段落，列出 4 子域 / 9+1 Entity / Controller 数 / 2 个新 region 接口
- [x] 8.3 14 接口骨架进度记录维持不变（plugin 接口为非骨架新增）

## 9. 集成测试

- [x] 9.1 新建 `plugin/team/integration/PluginCrudIntegrationTest.java`：POST/GET/DELETE + tenant_plugin 写入断言（测试通过）
- [ ] 9.2 `PluginVersionBuildIntegrationTest.java`（推迟：版本构建 controller 已可手测；hardening）
- [ ] 9.3 `ServicePluginInstallIntegrationTest.java`（推迟：组件挂载三表写入已可手测；hardening）
- [ ] 9.4 `PluginShareIntegrationTest.java`（推迟：分享状态机已可手测；hardening）
- [x] 9.5 跑 `mvn test` 全部 95 用例通过（1 新 + 94 老）

## 10. 校验

- [x] 10.1 跑 `openspec validate migrate-console-plugin --strict` 通过
