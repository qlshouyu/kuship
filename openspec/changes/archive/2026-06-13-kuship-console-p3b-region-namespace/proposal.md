## Why
region 专题续：集群命名空间读 `regions/{region_id}/namespace`，region 后端依赖、输出稳定（命名空间列表），复用 P3-a 的 RegionClient mTLS 底座。

## What Changes
- 集群命名空间（对齐 `EnterpriseRegionNamespace` + `list_namespaces`）：`GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace?content=all` → 由 region_id 解析 region_name，调 region `/v2/cluster/namespace?eid=&content=` → `bean = body.list`（命名空间名数组），`msg_show=获取成功`。
- **不包含**：命名空间资源/转换（resource/convert-resource）、lang_version（实测空）。

## Capabilities
### New Capabilities
- `region-namespace-read`: 集群命名空间读（region 后端依赖，复用 RegionClient mTLS）。

## Impact
- 代码：RegionNamespaceService + RegionNamespaceController；RegionConfigRepository.findByRegionId。
- 数据：只读 region_info（解析连接）+ 实时调 region；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 验证：bean=命名空间数组（稳定，实测 ["default"]）对 7070 一致。dev 需 REGION_URL_OVERRIDE。
