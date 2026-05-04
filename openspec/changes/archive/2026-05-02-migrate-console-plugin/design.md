## Context

第 10 阶段把"插件系统"——rainbond 让组件织入 sidecar、流量代理、网关、性能分析等横向能力——整体迁移到 kuship-console。涉及参考代码：

| view | LOC | 主要 endpoint |
|------|-----|----------------|
| `plugin/plugin_create.py` | 240 | 团队插件创建 / 默认插件创建 |
| `plugin/plugin_info.py` | 407 | 插件详情 / 全部 / 已用服务 / 版本历史 / 单版本 / 事件日志 |
| `plugin/plugin_manage.py` | 158 | 构建 / 新版本 / 构建状态 |
| `plugin/plugin_config.py` | 320 | 配置组 + 配置项管理 + preview |
| `plugin/plugin_share.py` | 381 | 插件分享异步流程（5 endpoint）|
| `plugin/plugin_market.py` | 181 | 应用市场插件同步 / 安装 / 内置市场 |
| `plugin/service_plugin.py` | 298 | 组件挂载插件（list / install / open / configs / analyze） |
| `platform_plugin.py` | 39 | 平台插件列表 / 安装 |
| `rbd_plugin.py` | 137 | Rainbond 平台插件代理（status / static / proxy / backend / 7 view） |
| **合计** | **2161** | **~40 endpoint** |

数据库 schema 真相（已 `DESC` 校验）：
- `tenant_plugin`（17 列，**`desc` 是保留字需反引号**）
- `plugin_build_version`（16 列，`build_status` / `plugin_version_status` 双状态字段）
- `plugin_config_group`（6 列）+ `plugin_config_items`（12 列含 longtext `attr_alt_value`/`attr_default_value`）
- `tenant_service_plugin_relation`（8 列）+ `tenant_service_plugin_attr`（17 列含跨服务字段 `dest_service_id`）+ `service_plugin_config_var`（11 列含 longtext `attrs`）
- `tenant_plugin_share`（17 列含 varchar(4096) `config`）+ `plugin_share_record_event`（10 列）
- `rainbond_center_plugin`（应用市场插件，6 阶段第一次见到 entity）

## Goals / Non-Goals

**Goals:**
- 让 kuship-ui 的"插件商店"、"我的插件"、"组件挂载插件"、"平台插件"四个 tab 完全可用，不再依赖 rainbond-console。
- 落地 9 张本地 schema 表的 JPA Entity（按 `DESC` 真相严格对齐）+ 复用第 9 阶段已声明但未实现的 `RainbondCenterPlugin`。
- 新增 2 个 region API 接口（`PluginOperations` 10 method + `RainbondPluginOperations` 8 method），覆盖完整的 region 插件操作。
- 插件分享（plugin_share）的异步状态机模板从第 9 阶段 service_share 复用，零重复代码。

**Non-Goals:**
- 不实现 `analyze_plugins` 真正的性能分析数据可视化（占位透传 region）。
- 不实现 `rbd_plugin.py` 的 WebSocket 实时插件 UI（前端 iframe 嵌入，console 仅做静态资源代理）。
- 不实现自定义插件源码构建的 webhook 自动触发（依赖 OAuth git 集成，留作 hardening）。
- 不实现插件 OCI artifact 拉取（rainbond 原版用 docker registry，console 透传不解析）。
- 不实现 `kubeblocks_*plugins` 这类 db-class 插件特化逻辑（KubeBlocks 在 misc 阶段统一处理）。

## Decisions

### 决策 1：plugin 模块按 4 子域细分

```
modules/plugin/
├── api/                             共享 RegionApiSupport + 2 region API 接口/Impl
├── team/      —— TenantPlugin + 版本构建 + 配置组 + 已用服务 + 插件分享
├── service/   —— TenantServicePluginRelation + Attr + ConfigVar（组件挂载）
├── market/    —— RainbondCenterPlugin + 市场同步/安装/内置插件
└── platform/  —— Rainbond 平台插件 + region 透传代理
```

每个子域独立 entity/repository/service/controller。共享的 `api/RegionApiSupport.java`（与 application/appruntime/appmarket 同模板的 helper）+ 共享的 `service/PluginContextLoader.java`（按 team_name + plugin_id 取 Tenant + TenantPlugin）。

### 决策 2：插件版本构建走"先 console 后 region"双阶段

构建版本是 console 主权数据，region 仅触发实际镜像 build：
- **创建版本**：`POST /plugins/{id}/new-version` → 事务内 INSERT `plugin_build_version`（status=building）→ 调 region API → region 失败回滚
- **触发构建**：`POST /version/{ver}/build` → 调 region build → 拿 event_id → update `plugin_build_version.event_id + plugin_version_status=building`
- **构建状态**：`GET /version/{ver}/status` → 调 region 拿状态 → update local
- **构建日志**：`GET /event-log` → 透传 region

### 决策 3：插件配置组双表 + 配置项 mapper

`plugin_config_group` 保存配置组元数据（如"网关插件配置"、"性能监控配置"），`plugin_config_items` 是组下的实际可填字段。一个 plugin_id+build_version 下可有多组：
- 配置组 GET/POST/PUT/DELETE 走 `/plugins/{id}/version/{ver}/config` 单端点（按 service_meta_type 区分插件类型）
- 写入时配置组 + 配置项分两 INSERT；删除时按 plugin_id+build_version 级联软删
- preview 端点 `/plugins/{id}/version/{ver}/preview` 返回前端预览用的 JSON 结构（attrs 数组 + 描述）

### 决策 4：组件挂载插件三表关联

挂载插件涉及 3 张表：
- `tenant_service_plugin_relation`：组件 → 插件版本 + 启停状态
- `tenant_service_plugin_attr`：跨服务依赖时的 dest_service_id（如代理插件需要知道下游 service）
- `service_plugin_config_var`：每个插件的实际填值（覆盖默认值）

挂载流程：
1. `POST /apps/{alias}/plugins/{plugin_id}/install` → INSERT `relation`（plugin_status=true）+ 默认 INSERT `config_var`（从 `plugin_config_items.attr_default_value` 拷贝）+ 调 region 通知
2. `PUT /apps/{alias}/plugins/{plugin_id}/open` → UPDATE `relation.plugin_status` + 调 region restart
3. `PUT /apps/{alias}/plugins/{plugin_id}/configs` → 替换 `config_var` 行（先按 service_id+plugin_id 删，再 batch INSERT）+ 调 region update
4. `DELETE /apps/{alias}/plugins/{plugin_id}/install` → DELETE 三张表 + 调 region detach

### 决策 5：插件分享复用 service_share 状态机

`tenant_plugin_share` + `plugin_share_record_event` 状态机与第 9 阶段 service_share 同构（6-step 0-5 + 3-status 0/1/2）。controller 几乎是 copy-paste from `ServiceShareController`：
- `POST /plugins/{plugin_id}/share/record` → INSERT tenant_plugin_share，step=0 status=0
- `GET/POST /plugin-share/{share_id}` → info push（step=2）
- `GET/POST /plugin-share/{share_id}/events{,/{event_id}}` → events
- `POST /plugin-share/{share_id}/complete` → status=1 step=5

### 决策 6：market 插件独立子域 + 远程同步

`rainbond_center_plugin` 表已存在 schema，列包括 plugin_key（远程标识）+ plugin_template + 版本控制等。但本 change 仅做"列表 + 一键安装"基础功能，不做远程市场凭据 CRUD（已在第 9 阶段 AppMarket 处理）。
- `GET /market/plugins` → 拉取本地 cache 的 rainbond_center_plugin
- `POST /market/plugins/sync` → 调 远程 marketplace API（用第 9 阶段 AppMarket 凭据）→ batch upsert `rainbond_center_plugin`
- `POST /market/plugins/install` → 把 marketplace plugin 装到当前团队（INSERT tenant_plugin + plugin_build_version + plugin_config_group/items）
- `GET /plugins/installable` → 列出当前团队已可安装的 marketplace 插件

### 决策 7：平台插件 region 静态资源 + 后端代理

rainbond 平台插件（如 prometheus / nodejs-builder）由 region 端管理，前端通过 console 反向代理：
- `GET /enterprise/{eid}/regions/{r}/{plugins,platform-plugins,officialplugins}` → 列出 region 插件
- `POST /enterprise/{eid}/regions/{r}/platform-plugins/{id}/install` → 触发 region 安装
- `GET /regions/{r}/plugins/{name}/status` → 状态查询
- `GET /regions/{r}/static/plugins/{name}` → **静态资源透传**（HTML/JS/CSS）—— 用 `byte[]` 透传 + 保留 Content-Type
- `GET /regions/{r}/proxy/plugins/{name}/{file_path}` → 通用反向代理（用于插件前端 HTTP 调用）
- `GET /regions/{r}/backend/plugins/{name}/{file_path}` → 后端 API 代理（同样透传任意 method + body）

新增 `RegionRawProxy.java` helper 处理任意 path + method 的代理，**不限制 5s 超时（提升到 30s）**——平台插件的静态资源较大且可能首次冷启动较慢。

### 决策 8：PluginOperations + RainbondPluginOperations 两接口

不在原 14 接口骨架中，作为 plugin 阶段新增 region API：

```
PluginOperations              (10 method)
├── createPlugin / updatePlugin / deletePlugin
├── buildPlugin / getPluginBuildStatus
├── installToService / uninstallFromService / openOnService
└── syncFromMarket / installFromMarket

RainbondPluginOperations      (8 method)
├── listPlugins / listPlatformPlugins / listOfficialPlugins / listObservablePlugins
├── installPlatformPlugin / getPluginStatus
└── proxyStaticResource / proxyBackend
```

两接口都用 `RegionApiSupport.exchange(lambda)` 模板。`RainbondPluginOperations.proxyStaticResource` 返回 `byte[]` + `Content-Type` 元数据（不走 `RegionApiResponseProcessor.extractBean` 的 JSON 包装）。

### 决策 9：plugin_id / share_id / build_version UUID 规则

- `plugin_id`: 32-char UUID（`UuidGenerator.makeUuid()`）—— rainbond 历史规则
- `build_version`: 不是 UUID，是用户填的版本号字符串（如 "v1.0.0"），最长 32-char
- `share_id`: 32-char UUID（与 service_share 共用 ID 命名空间，避免冲突）
- `region_share_id`: 36-char UUID（带连字符的 UUID，由 region 返回）

### 决策 10：保留字 `desc` 列处理

`tenant_plugin.desc` + `tenant_plugin_share.desc` 是 SQL 保留字。entity 必须用反引号：
```java
@Column(name = "`desc`", length = 256)
private String describe;
```
（与第 9 阶段 `RainbondCenterApp.describe` 处理一致）

## Risks / Trade-offs

- **[Risk]** 40 endpoint + 9 entity + 2 region 接口 = 单 change 体量大 → Mitigation：按 4 子域并行实施，每子域闭环；先做 team（最大）→ service（中）→ market → platform。
- **[Risk]** 平台插件静态资源代理 byte[] 透传可能内存 OOM → Mitigation：单文件超过 10MB 时返回 413；用流式透传（`RestClient.exchange` 直接转发输入流）。
- **[Risk]** 插件挂载到 1000+ 组件时批量 region 调用慢 → Mitigation：本 change 不优化，后续若有需要再加 batch endpoint；当前用户 ≤ 50 组件挂载场景下足够。
- **[Risk]** plugin_share 与 service_share 命名容易混淆 → Mitigation：plugin_share 包含 origin_plugin_id 而非 group_id；controller 类名 `PluginShareController` 区分 `ServiceShareController`。
- **[Risk]** rainbond_center_plugin 列名复杂（plugin_key + plugin_template + plugin_image_tag）→ Mitigation：先实现 list 端点，install 端点 MVP 化（仅 INSERT tenant_plugin 元数据，深度安装留作 hardening）。
- **[Trade-off]** analyze_plugins 仅占位透传 → 影响插件分析能力，但前端仍可显示空数据，hardening 完成度。

## Migration Plan

阶段 A：plugin 包结构 + RegionApiSupport + 2 region 接口骨架 + Impl 占位
阶段 B：team 子域 entity + repo + 8 controller（CRUD + 版本 + 配置组 + 已用服务 + 分享）—— 最大子域，~20 endpoint
阶段 C：service 子域 entity + repo + 1 controller（组件挂载 4 endpoint）
阶段 D：market 子域 + 1 controller（市场插件同步 + 安装 6 endpoint）
阶段 E：platform 子域 + 1 controller（region 平台插件代理 10 endpoint）
阶段 F：编译 + 集成测试（4 类）+ 文档（CLAUDE.md）+ openspec validate

阶段无强依赖；按子域可并行。

## Open Questions

- **(Q1)** 配置组 / 配置项当前 controller 是 1 个文件还是分开？决策 3 说放一起；如有需要细分再独立。
- **(Q2)** market 插件是否需要远程 HTTP 拉取？MVP 先做本地 cache，远程拉取留作下个 hardening change。
- **(Q3)** 平台插件 backend 代理是否需要 WebSocket 支持？本 change 不实现，前端如需 WS 直连 region。

不阻塞实施。
