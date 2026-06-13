## Why
region 组件域续：持久化列表读 `teams/{team}/apps/{serviceAlias}/volumes`（`AppVolumeView.get` 非 config 路径）。未部署组件 create_status!=complete → status=not_bound 纯 DB，可对 7070 逐字节校准。

## What Changes
- 持久化列表（对齐 `AppVolumeView.get` 非 config 路径）：`GET .../volumes?region_name=` → `list`=[每 volume]，每项=`volume.to_dict()`(15 列)+status(create_status!=complete→not_bound；complete→region 卷状态 READY→bound)+dep_services(单组件 null)+([0])first=true。`msg_show=查询成功`。volume 排除 config-file。
- **不包含**：is_config=true（config-file + file_content）、多组件挂载依赖 dep_services 填充、增删改。

## Capabilities
### New Capabilities
- `component-volumes-read`: 组件持久化列表读（volume.to_dict + status + dep_services + first）。

## Impact
- 代码：TenantServiceVolume 实体+repo(排除 config-file)；TenantServiceInfo 加 getCreateStatus；ComponentVolumesService；ComponentVolumesController。
- 数据：只读 tenant_service_volume/tenant_service/tenant_region/tenant_info（complete 时 + region）；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：未部署组件 not_bound 纯 DB，list 全字段对 7070 逐字节一致。
