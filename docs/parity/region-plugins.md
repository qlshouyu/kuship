# 对齐报告：集群插件三端点（plugins / platform-plugins / officialplugins）

- **日期**：2026-07-18
- **rainbond 实现**：`RainbondPluginLView` / `PlatformPluginLView`（console/views/platform_plugin.py）/ officialplugins；核心逻辑 `console/services/platform_plugin_service.py`
- **kuship 实现**：`modules/region/controller/RegionPluginController` + `RainbondPluginService` / `PlatformPluginService`

## 1. GET /console/enterprise/{eid}/regions/{region_name}/plugins

- 契约：region `/v2/cluster/plugins?official=False` 映射；`{code:200,msg:"success",msg_show:"查询成功",list:[...]}`
- 结果：**MATCH ✅**（本环境无已装插件，list=[]）

## 2. GET /console/enterprise/{eid}/regions/{region_name}/officialplugins

- 契约：official=True，bean 带 `{"need_authz": bool}`
- 结果：**MATCH ✅**（bean.need_authz=false，list=[]）

## 3. GET /console/enterprise/{eid}/regions/{region_name}/platform-plugins

- 契约（JWTAuthApiView）：云端市场 `{market_url}/app-server/openapi/apps/platform-plugins?marketDomain=enterprise&query=&page=1&pageSize=-1`（Authorization=app_market.access_key，url 取企业默认市场行、兜底 hub.grapps.cn）拉取全部 SKU，再：
  1. 按 plugin_id 分组（保持市场返回顺序）；
  2. 按集群架构（region `/v2/cluster/nodes/arch`，60s 缓存，失败回退 {amd64,arm64}）过滤候选，混合架构 amd64 优先；
  3. SKU 选择：free → license app_key（含 -ARM64/-AMD64 基名归一）→ first；
  4. 有效授权时过滤未授权非 free 插件（plugin_mapping 来自 region `/v2/license/status`）；
  5. 已安装插件（region `/v2/cluster/plugins?official=False`）锚定 latest_version（region_app→tenant_service_group.group_key 反查安装 SKU；**跨源安装强制不报可升级**）。
  - 每项 24 字段定序：plugin_id/app_key/plugin_name/name/description/logo/app_level/latest_version/plugin_type/plugin_views/frontend_component/entry_path/menu_title/route_path/installed/status/installed_version/upgradeable/can_upgrade/team_name/app_id/author/selected_arch/installed_arch/available_arches
- 结果：**MATCH ✅** 10 个插件（市场 23 SKU、13 id，arm64 集群过滤后 10）逐叶子+键序全一致；二次请求（缓存路径）仍一致

## 本轮修复

**platform-plugins 原为空 stub**（7070 返 10 项、8000 返 []）。本轮 1:1 移植 `list_platform_plugins` 全逻辑：
- 新增 `AppMarket`/`RegionApp`/`TenantServiceGroup` 只读实体+仓储（app_market / region_app / tenant_service_group 表）；
- `PlatformPluginService` 重写：市场 HTTP 拉取（15s 超时）+60s 缓存、arch 过滤/选择/授权过滤/安装锚定，Python `.get(k,def)` 与 `or` 语义用 containsKey/truthy 帮助函数精确复刻；
- `DISABLE_DEFAULT_APP_MARKET`/`DISABLE_CLOUD_MARKET` 环境变量支持（云市场禁用→空 list）。

## 未覆盖分支（本环境不可测）

- 已安装插件的锚定/升级判定分支（环境无已装平台插件）；有效授权的 license 过滤分支（无 AUTHZ_CODE）。逻辑 1:1 移植+代码审查，待 populated 环境复核。
- `POST platform-plugins/{id}/install`（安装，写端点）未实现，404 backlog。
