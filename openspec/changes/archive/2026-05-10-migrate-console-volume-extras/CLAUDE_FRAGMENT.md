### 存储挂载（migrate-console-volume-extras）

`cn.kuship.console.modules.application`（application 模块扩展）落地"组件挂载依赖存储"（mnt）全套端点，
以及 `ServiceVolumeOperations` 接口 6 个 region API method 的完整实现。

**新增 Entity**（1 张本地表 JPA 映射）：
- `TenantServiceMountRelation`（tenant_service_mnt_relation，5 列：tenantId / serviceId / depServiceId / mntName / mntDir）

**新增 Repository**：
- `TenantServiceMountRelationRepository`：5 个 finder / deleter

**新增 Service**：
- `AppMntService`：getMounted / getUnmounted / addMnt / deleteMnt

**新增 Controller**：
- `AppMntController`：
  - `GET  /console/teams/{team_name}/apps/{service_alias}/mnt?type=mnt|unmnt`
  - `POST /console/teams/{team_name}/apps/{service_alias}/mnt`
  - `DELETE /console/teams/{team_name}/apps/{service_alias}/mnt/{dep_vol_id}`

**补全 ServiceVolumeOperationsImpl（6 个 method）**：
- `getVolumeOptions` → `GET /v2/volume-options`（无 tenant 前缀）
- `getVolumes` → `GET /v2/tenants/{t}/services/{a}/volumes`
- `getVolumeStatus` → `GET /v2/tenants/{t}/services/{a}/volumes-status`
- `getDepVolumes` → `GET /v2/tenants/{t}/services/{a}/depvolumes`
- `addDepVolumes` → `POST /v2/tenants/{t}/services/{a}/depvolumes`
- `deleteDepVolumes` → `DELETE /v2/tenants/{t}/services/{a}/depvolumes`（含 JSON body）

**挂载写策略**：
- `addMnt`：组件 `create_status=complete` 时调 region addDepVolumes；region 失败仅记 WARN，本地 mnt_relation 仍写入
- `deleteMnt`：region deleteDepVolumes → 404 时忽略，直接删本地行
- config-file volume 类型挂载返回 400（依赖 service_config_file 表，本 change 不支持）

**未挂载过滤规则**（对齐 Python `mnt_service.get_service_unmount_volume_list`）：
- 排除当前组件自身存储
- 仅保留 access_mode=RWX
- 排除 config-file / local-path 类型
- 排除有状态组件（extend_method in {state, singleton}）的存储
- 排除已在 mnt_relation 中的存储
