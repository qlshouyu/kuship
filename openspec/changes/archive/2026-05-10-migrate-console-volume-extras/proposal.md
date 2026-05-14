## Summary

补全 `migrate-console-application-core` change 遗留的存储卷（Volume）域 region API 6 个 method，
以及实现挂载依赖（ServiceMntRelation）全套端点。

## Problem

application-core change 已实现 addVolumes / deleteVolumes / upgradeVolumes 三个 region API，
但以下 6 个 method 仍为 `unsupported` 占位：
- `getVolumeOptions`（region 返回可用 volume 驱动类型）
- `getVolumes`（从 region 获取 volume 真实状态）
- `getVolumeStatus`（从 region 获取 volume 挂载状态）
- `getDepVolumes`（依赖 depvolumes API）
- `addDepVolumes`（挂载其他组件存储）
- `deleteDepVolumes`（取消挂载）

同时，`AppMntView` / `AppMntManageView`（rainbond Python 端，对应 `tenant_service_mnt_relation` 表）
在 kuship-console 内完全缺失。

## Solution

1. 补全 `ServiceVolumeOperationsImpl` 实现 6 个 method
2. 新增 `TenantServiceMountRelation` JPA entity + repository
3. 新增 `AppMntService` 封装挂载业务逻辑（batch_mnt + delete_mnt）
4. 新增 `AppMntController`：4 个端点（GET mnt/unmnt + POST + DELETE/{dep_vol_id}）

## Impact

- 新增文件：entity / repo / service / controller / test（各 1 个）
- 修改文件：`ServiceVolumeOperationsImpl.java`（新增 3 个 method）
- 不修改其他业务域

## Non-Goals

- `config-file` volume 的 file_content 存储（依赖 `ServiceConfigFile` 表，独立 change）
- `volume_status` 端点前端暂无调用，实现 region API method 但不新增 controller 端点
