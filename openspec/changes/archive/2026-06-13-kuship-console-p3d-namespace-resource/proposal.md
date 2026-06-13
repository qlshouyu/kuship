## Why
region 专题续：命名空间资源读 `regions/{region_id}/resource`（`EnterpriseNamespaceResource.get`），region 透传 + 把 `unclassified` 键移到末尾；空闲集群下系统工作负载名稳定，可对 7070 比对。

## What Changes
- 命名空间资源（对齐 `EnterpriseNamespaceResource.get` → `region_api.list_namespace_resources` → region `GET /v2/cluster/resource?eid&content&namespace`）：`GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource?content=all&namespace=` → `bean` = region 响应 `bean`，并把 `unclassified` 键**移到末尾**（对齐 view 的 `pop`+回填），`msg_show=获取成功`。
- **不包含**：convert-resource（GET 转换 / POST 导入建租户）、lang_version。

## Capabilities
### New Capabilities
- `region-namespace-resource-read`: 命名空间资源读（region 后端依赖，透传 + unclassified 末位）。

## Impact
- 代码：RegionNamespaceService.listNamespaceResources + RegionNamespaceController GET .../resource。
- 数据：只读 region_info + 实时调 region；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 验证：bean 结构（工作负载/资源**名**，非运行态）空闲集群下稳定 → 对 7070 比对。dev 需 REGION_URL_OVERRIDE。
