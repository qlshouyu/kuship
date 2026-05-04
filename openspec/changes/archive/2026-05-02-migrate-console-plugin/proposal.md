## Why

第 9 阶段（migrate-console-app-market）让用户能从模板批量装应用 + 整组备份/升级。但 **插件系统**——rainbond 让组件之间织入 sidecar、流量代理、网关功能（如 mesh sidecar、A/B 路由、性能分析）的关键能力——还停留在 rainbond-console。kuship-ui 的"插件商店"标签页、"组件附属插件"抽屉、"自定义插件创建"流程全部空白。

本次把 rainbond `views/plugin/` 8 个 view 文件 + `platform_plugin.py` + `rbd_plugin.py` 共 **2200+ 行 / ~40 endpoint** 迁移到 kuship-console，覆盖：插件 CRUD / 版本构建 / 配置组 / 安装至组件 / 插件分享 / 应用市场插件同步安装 / Rainbond 平台插件代理。

## What Changes

- **团队级插件 CRUD**：`/teams/{team}/plugins` GET 列表 / POST 创建、`/plugins/all` 全部、`/plugins/default` 默认、`/plugins/{plugin_id}` GET/PUT/DELETE 详情、`/plugins/{plugin_id}/used_services` 已使用服务列表。
- **插件版本构建**：`/plugins/{plugin_id}/build-history` GET 构建历史 / `/new-version` POST 新版本 / `/version/{build_version}` GET/PUT 详情 / `/version/.../config` 配置 / `/preview` 预览 / `/build` 触发构建 / `/status` 构建状态 / `/event-log` 构建日志。
- **插件配置组**：plugin_config.py 320 行 = 配置组 + 配置项管理（`plugin_config_group` + `plugin_config_items` 两表）。
- **组件挂载插件**：`/teams/{team}/apps/{alias}/{pluginlist,plugins/{plugin_id}/{install,open,configs},analyze_plugins}` 共 5 endpoint —— 组件级插件安装/启停/配置（`tenant_service_plugin_relation` + `tenant_service_plugin_attr` + `service_plugin_config_var` 三表）。
- **插件分享**：`/teams/{team}/plugins/{plugin_id}/share/record` POST + `/plugin-share/{share_id}/{info,events,events/{event_id},complete}` 共 5 endpoint —— `tenant_plugin_share` + `plugin_share_record_event` 两表，复用 service-share 6-step 状态机模板。
- **应用市场插件**：`/market/plugins{,/sync,/sync-template,/uninstall-template,/install}` + `/plugins{,/installable}` + `/teams/{team}/apps/plugins` 共 8 endpoint —— `rainbond_center_plugin` 表（已存在 schema）+ 远程市场拉取。
- **Rainbond 平台插件**：`/enterprise/{eid}/regions/{r}/{plugins,platform-plugins,platform-plugins/{id}/install,officialplugins}` + `/regions/{r}/{plugins/{name}/status,static/plugins/{name},proxy/plugins/{name}/{file_path},backend/plugins/{name}/{file_path}}` 共 ~10 endpoint —— region 平台插件代理 + 静态资源 + 后端 API 反向代理。
- **Region API 扩展**：完整实现 `RainbondPluginOperations` 8 method（list/install/uninstall/status/list-official/list-observable/static-proxy/backend-proxy）—— 不在原 14 接口骨架中，作为新 region 接口。
- **Plugin Operations**：实现 `PluginOperations`（10 method：create/update/delete/build/buildStatus/installToService/uninstallFromService/openOnService/syncFromMarket/installFromMarket）—— 不在原 14 接口骨架中，新接口。

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增约 18 条插件相关 Requirement —— 团队插件 CRUD / 版本构建 / 配置组 / 组件挂载 / 插件分享 / 市场同步 / 平台插件代理 / 9 张表 JPA 落地 / Region API 实现 / 测试覆盖。

## Impact

- **新增包**：`cn.kuship.console.modules.plugin/`（与 appmarket / appruntime 平级），按 4 子域细分：`team/`（团队级插件 CRUD + 构建 + 配置组 + 分享）、`service/`（组件挂载插件）、`market/`（应用市场插件 + 同步）、`platform/`（平台/官方/可观测插件 + region 代理）。
- **新增 Entity**（9 张本地表 JPA 映射）：
  - `TenantPlugin`（tenant_plugin）/ `PluginBuildVersion`（plugin_build_version）
  - `PluginConfigGroup`（plugin_config_group）/ `PluginConfigItems`（plugin_config_items）
  - `TenantServicePluginRelation`（tenant_service_plugin_relation）/ `TenantServicePluginAttr`（tenant_service_plugin_attr）/ `ServicePluginConfigVar`（service_plugin_config_var）
  - `TenantPluginShare`（tenant_plugin_share）/ `PluginShareRecordEvent`（plugin_share_record_event）
  - 复用：`RainbondCenterPlugin`（rainbond_center_plugin，第 9 阶段未涉及，本阶段补 entity）
- **新增 Region API**：`PluginOperations` 接口 + Impl（10 method，处理 region 插件 CRUD/构建/挂载）；`RainbondPluginOperations` 接口 + Impl（8 method，处理 region 平台插件代理）。
- **依赖**：保持现有 `RestClient` + `JPA`，不引入新第三方库。平台插件 region 代理需透传任意 URL path，新增 `RegionRawProxy.java` helper（但 timeout 仍 5s）。
- **测试**：扩展 4 个集成测试覆盖 plugin CRUD / version build / service install / share record。
