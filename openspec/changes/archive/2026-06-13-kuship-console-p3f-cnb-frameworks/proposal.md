## Why
region 专题续：集群 CNB 框架读 `regions/{region_id}/cnb/frameworks`（`EnterpriseRegionCNBFrameworks.get`），region 后端返回**稳定非空**框架定义列表（nextjs/nuxt/...），完全可对 7070 逐字节比对。

## What Changes
- CNB 框架列表（对齐 `EnterpriseRegionCNBFrameworks.get` → `region_api.get_cnb_frameworks` → region `GET /v2/cluster/cnb/frameworks?lang=`）：`GET /console/enterprise/{enterprise_id}/regions/{region_id}/cnb/frameworks?lang=nodejs` → `list` = region 响应 `list`（框架定义数组），`msg_show=获取成功`；异常回退 `code=400 msg=failed msg_show=获取CNB框架列表失败`（对齐 view try/except）。
- **不包含**：lang_version（CRUD）、其它 CNB 配置写。

## Capabilities
### New Capabilities
- `region-cnb-frameworks-read`: 集群 CNB 框架读（region 后端依赖，list 透传）。

## Impact
- 代码：RegionCnbService.showFrameworks + RegionCnbController GET .../cnb/frameworks。
- 数据：只读 region_info + 实时调 region；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录）。
- 验证：list（框架定义，稳定非空）对 7070 逐字节一致。dev 需 REGION_URL_OVERRIDE。
