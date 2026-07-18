# 对齐报告：GET /console/teams/{tenantName}/overview 计数修复（存量偏差）

- **日期**：2026-07-18
- **发现方式**：本轮逐端点对齐后的回归扫描（非本轮改动引入——P1-a 时代空库校准掩盖了硬编码 0）

## 差异

| 字段 | 7070 | 8000（修复前） |
|---|---|---|
| team_app_num | 1 | 0（硬编码） |
| team_service_num | 1 | 0（硬编码） |

## rainbond 口径（console/views/public_areas.py TeamOverView.get）

- `team_app_num` = `group_repo.get_tenant_region_groups(tenant_id, region_name)` 行数（service_group 按 tenant+region）
- `team_service_num` = `service_repo.get_team_service_num_by_team_id(tenant_id, region_name)` = **service_group_relation** 按 tenant+region 计数（注意：不是 tenant_service 表）

## 修复

- `ServiceGroupRelationRepository` 新增 `countByTenantIdAndRegionName`
- `TeamReadService.teamOverview` 接入两个真实计数（复用 `ServiceGroupRepository.findByTenantIdAndRegionName...`）

## 校准结果

`GET /console/teams/default/overview?region_name=rainbond`：**MATCH ✅** 逐叶子+键序全一致（含两计数=1）。165 单测全绿。

## 备注

- rainbond 该 view 还有「应用同步到集群 + running_app_num」的副作用逻辑（batch_create_application），kuship 未做；running_app_num 两侧当前均 0 一致，待有 running 应用环境复核。
