## Context

`migrate-console-application-core` 已实现 `AppVolumeController`（POST/DELETE/PUT）和
`ServiceVolumeOperationsImpl`（addVolumes / deleteVolumes / upgradeVolumes），
但 `ServiceVolumeOperations` 接口的 6 个 method 仍为 `unsupported` 占位：

| method | rainbond Python 锚点 | region 路径 | 说明 |
|--------|----------------------|-------------|------|
| `getVolumeOptions` | `regionapi.get_volume_options` | `GET /v2/volume-options` | 不含 tenant 路径段 |
| `getVolumes` | `regionapi.get_service_volumes` | `GET /v2/tenants/{t}/services/{a}/volumes?enterprise_id={e}` | |
| `getVolumeStatus` | `regionapi.get_service_volumes_status` | `GET /v2/tenants/{t}/services/{a}/volumes-status` | |
| `getDepVolumes` | `regionapi.get_service_dep_volumes` | `GET /v2/tenants/{t}/services/{a}/depvolumes?enterprise_id={e}` | |
| `addDepVolumes` | `regionapi.add_service_dep_volumes` | `POST /v2/tenants/{t}/services/{a}/depvolumes` | |
| `deleteDepVolumes` | `regionapi.delete_service_dep_volumes` | `DELETE /v2/tenants/{t}/services/{a}/depvolumes` + body | |

## Goals

1. 补全 6 个 region API method 实现
2. 新增 `TenantServiceMountRelation` entity（`tenant_service_mnt_relation` 表）
3. 新增 `AppMntController` 端点：与 rainbond `AppMntView` / `AppMntManageView` 对齐

## Decisions

### 决策 1：getVolumeOptions 不含 tenant 路径段

rainbond Python `get_volume_options` 路径是 `/v2/volume-options`（无 `/tenants/{t}/services/{a}` 前缀），
与其他 volume method 不同。Java Impl 中需直接用 regionName 解析 URL，不带 tenantName 路径。

接口签名保留 `tenantName` 参数（方便 `RegionClientFactory.getClient(regionName, ...)` 获取 token），
但 URL 拼接不使用该参数。

### 决策 2：挂载关系本地为主，region 同步为辅

rainbond Python 端：已创建完成（`create_status == "complete"`）的组件才调 region；
其他情况只写本地表。kuship-console 沿用此策略：
- `AppMntService.addMnt`：调 `addDepVolumes` region API + 写 `tenant_service_mnt_relation`
- `AppMntService.deleteMnt`：调 `deleteDepVolumes` region API + 删本地行；region 404 则忽略直接删本地

### 决策 3：controller 端点对齐 rainbond URL

```
GET  /console/teams/{team_name}/apps/{service_alias}/mnt        ?type=mnt|unmnt
POST /console/teams/{team_name}/apps/{service_alias}/mnt
DELETE /console/teams/{team_name}/apps/{service_alias}/mnt/{dep_vol_id}
```

`type=mnt`：返回已挂载列表（join mnt_relation + volume）
`type=unmnt`：返回可挂载但未挂载的 volume 列表（过滤掉 config-file，过滤掉已挂载）

### 决策 4：不实现 config-file 挂载

rainbond mnt_service 对 `config-file` volume 类型需要额外查 `service_config_file` 表（file_content），
该表尚未映射。本 change 对 config-file volume 挂载请求返回 `400 "config-file volume 暂不支持挂载"`。

### 决策 5：getVolumeStatus 只实现 region API method，不暴露 controller 端点

UI 当前未调用 volume-status 端点，controller 暂不暴露，仅实现 Impl 供未来使用。

## Risks

- `tenant_service_mnt_relation` 表的 schema 需在启动前已存在（rainbond Django 已创建）
- region addDepVolumes 失败时已写入本地行不回滚（与 rainbond Python 端一致，允许本地记录"待同步"状态）
