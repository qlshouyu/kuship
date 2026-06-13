## Why
region 专题续：单集群详情读 `regions/{region_id}`（`EnterpriseRegionsRUDView.get`，check_status=**False** 纯 DB，open 级 28 字段），完全可对 7070 逐字节比对，低风险。

## What Changes
- 单集群详情（对齐 `EnterpriseRegionsRUDView.get` → `get_enterprise_region(eid, region_id, check_status=False)` → `conver_region_info(check_status=False)` → `__init_region_resource_data(level="open")`）：`GET /console/enterprise/{enterprise_id}/regions/{region_id}` → `bean` = open 级集群字典（safe 22 字段 + open 专属 wsurl/httpdomain/tcpdomain/ssl_ca_cert/cert_file/key_file 共 28 字段），check_status=False → 资源/健康用固定默认，**不调 region 后端**，`msg_show=获取成功`。
- **不包含**：PUT/DELETE（写集群，含 operation_log）、check_status=yes 富化（P2-e 列表已覆盖富化逻辑）。

## Capabilities
### New Capabilities
- `region-detail-read`: 单集群详情读（open 级，纯 DB，按 region_id）。

## Impact
- 代码：EnterpriseRegionReadService.getRegion(open 级单集群)；EnterpriseRegionController GET .../regions/{region_id}。
- 数据：只读 region_info；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 验证：bean 28 字段（含 create_time ISO）对 7070 逐字节一致。
