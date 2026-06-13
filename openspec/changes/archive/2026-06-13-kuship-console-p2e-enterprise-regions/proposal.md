## Why
企业 region 列表（`enterprise/{eid}/regions`）在 check_status 为空（视图默认）时是纯 console 库读（`__init_region_resource_data` level=safe，资源/健康字段用固定默认，不调 region 后端），且有真实数据（rainbond 集群），可完整校准。补齐它。

## What Changes
- 企业集群列表（对齐 `EnterpriseRegionsLCView.get` + `get_enterprise_regions(level=safe)` + `__init_region_resource_data`）：`GET /console/enterprise/{enterprise_id}/regions?status=` →
  - `region_info` 按 `enterprise_id`（可选 status）过滤；每项 22 字段：region_id/region_alias/region_name/status/region_type(json→list,空→[])/enterprise_id/url/scope/provider/provider_cluster_id/desc + 资源默认(total/used_memory/cpu/disk=0, rbd_version="unknown", health_status="ok", resource_proxy_status=false) + create_time(ISO) + enterprise_alias（由 enterprise_id 查 tenant_enterprise）。
  - safe 级：不含 wsurl/httpdomain/tcpdomain/ssl/cert/key（仅 open 级才有）。
  - `msg_show=获取成功`。
- **不包含**：check_status=yes 的 region 资源/健康实时拉取（调 region Go 后端）、region 增删改。

## Capabilities
### New Capabilities
- `enterprise-region-read`: 企业集群列表读（safe 级，纯 console 库 + 固定资源默认）。

## Impact
- 代码：`modules/region` 下集群列表服务 + controller；`RegionConfigRepository.findByEnterpriseId`；enterprise_alias 由 `TenantEnterpriseRepository.findByEnterpriseId`。
- 数据：只读 `region_info` + `tenant_enterprise`；无 schema 变更、不触 region 后端（check_status 空）。
- 鉴权：JWTAuthApiView（仅登录）。
- 简化：资源/健康字段为固定默认（check_status=yes 实时拉取留后续）；scope 直接取 region.scope（IS_STANDALONE env 覆盖暂不处理）。
