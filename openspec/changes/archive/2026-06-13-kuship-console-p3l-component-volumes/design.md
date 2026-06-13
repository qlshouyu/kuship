## Context
AppVolumeView.get(is_config=false)：volume_service.get_service_volumes(排除 config-file)；create_status!=complete→每 to_dict+status=not_bound(无 region)；complete→region get_service_volumes(/v2/tenants/{region_tenant_name}/services/{alias}/volumes?enterprise_id=) status map(READY→bound)。dep_services=get_volume_dependent(mnt by dep_service_id；单组件→None)。[0].first=true。general_message(list=)。

## Goals / Non-Goals
**Goals:** 非 config 持久化列表(to_dict+status+dep_services+first) 对 7070 校准(未部署 not_bound 纯 DB)。
**Non-Goals:** is_config(config-file+file_content)、多组件挂载依赖 dep_services 填充、增删改。

## Decisions
### 决策 1：TenantServiceVolume(to_dict 15 列,allow_expansion bool/mode int)+repo findByServiceIdAndVolumeTypeNotOrderById("config-file")
### 决策 2：create_status!=complete→not_bound；complete→RegionClient GET volumes→READY→bound；dep_services 恒 null(多组件依赖 defer)；[0].first=true
### 决策 3：general_message list=（bean 默认 {}）

## Risks / Trade-offs
- dep_services 仅单组件(null) 校准；多组件挂载依赖填充 defer(需多组件拓扑)。
- is_config 路径 defer。

## Migration Plan
无 schema 变更。校准：未部署组件 + 1 volume，8000 vs 7070 逐字节一致。

## Open Questions
- 无。
