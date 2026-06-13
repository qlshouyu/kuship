# P3-l 组件持久化列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/volumes?region_name=rainbond`（非 config 路径，is_config 默认 false）
- rainbond: `AppVolumeView.get` → `volume_service.get_service_volumes`（排除 config-file）。
- create_status != "complete" → 每 volume.to_dict + status=not_bound（**纯 DB 无 region**）；complete → region `/v2/tenants/{region_tenant_name}/services/{alias}/volumes?enterprise_id=` 卷状态(READY→bound)。
- dep_services = get_volume_dependent(mnt by dep_service_id；单组件→null)；[0].first=true。
- list 项 = volume.to_dict(15 列) + status + dep_services + first（共 18 字段）。

## 校准结果（8000 vs 7070，team=default，组件 grb15b29 未部署 + 1 个 volume pdata）
```
字段顺序: True
[ID,service_id,category,host_path,volume_type,volume_path,volume_name,volume_capacity,
 volume_provider_name,access_mode,share_policy,backup_policy,reclaim_policy,
 allow_expansion(false),mode(null), status(not_bound),dep_services(null),first(true)]
DIFF: MATCH ✓
```
- 探针经 [[kuship-region-component-provision]] 配方**仅创建**(未部署，create_status=creating)→status=not_bound 纯 DB；加 volume(POST .../volumes：volume_name/volume_type/volume_path/volume_capacity)。校准毕删除组件+组，库恢复原状(tenant_service/volume 全 0)。
- **defer**：is_config=true(config-file+file_content)、多组件挂载依赖 dep_services 填充。
