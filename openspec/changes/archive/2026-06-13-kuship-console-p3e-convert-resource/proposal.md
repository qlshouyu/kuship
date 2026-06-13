## Why
region 专题续：命名空间资源转换读 `regions/{region_id}/convert-resource`（`EnterpriseConvertResource.get`），与 P3-d resource 同模式（region 透传 + unclassified 末位），只读 GET，不含 POST 导入写。

## What Changes
- 资源转换（对齐 `EnterpriseConvertResource.get` → `region_api.list_convert_resource` → region `GET /v2/cluster/convert-resource?eid&content&namespace`）：`GET /console/enterprise/{enterprise_id}/regions/{region_id}/convert-resource?content=all&namespace=` → `bean` = region 响应 `bean`，并把 `unclassified` 键移到末尾，`msg_show=获取成功`。region 转换较慢，用 IMPORT_BACKUP(300s) 超时档。
- **不包含**：POST（resource_import 建租户/应用，重写副作用）、lang_version。

## Capabilities
### New Capabilities
- `region-convert-resource-read`: 命名空间资源转换读（region 后端依赖，透传 + unclassified 末位）。

## Impact
- 代码：RegionNamespaceService.convertResource + RegionNamespaceController GET .../convert-resource。
- 数据：只读 region_info + 实时调 region；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 验证：bean 结构（转换后资源名，非运行态）空闲集群下稳定 → 对 7070 比对。dev 需 REGION_URL_OVERRIDE。
