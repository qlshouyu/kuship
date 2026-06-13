## Why
企业团队概览（`enterprise/{eid}/overview/team`）是纯 console 库读，可对 7070 校准（interop 真实数据）。补齐它继续企业域读。

## What Changes
- 企业团队概览（对齐 `EnterpriseTeamOverView.get`）：`GET /console/enterprise/{enterprise_id}/overview/team` → bean `{active_teams, new_join_team, request_join_team}`：
  - `active_teams`：用户在企业的团队（PermRelTenant 关联），每项 {tenant_id, team_alias, owner, owner_name, enterprise_id, create_time(ISO), team_name, region, region_list, num(=ServiceGroup 数), role}；按 num 降序取前 3；role：creater→"owner"（非 creater 的 tenant_user_role 遗留角色机制本轮 defer）。
  - `new_join_team`：用户团队前 3，每项 {team_name, team_alias, team_id, create_time, region, region_list, enterprise_id, owner, owner_name, roles(角色名列表+creater 加 "owner"), is_pass:true}。
  - `request_join_team`：[]（Applicants 加入申请域 defer；interop 实测为空）。
- **不包含**（defer）：Applicants 加入申请（join/request 段，无该域；interop 空）、非-creater 成员的 tenant_user_role 遗留角色名、region 后端聚合。

## Capabilities
### New Capabilities
- `enterprise-team-overview`: 企业团队概览读（active_teams + new_join_team，纯 console 库）。

## Impact
- 代码：`modules/enterprise` 新增 overview/team 服务 + controller；复用 PermRelTenant/Tenants/TenantRegionInfo/ServiceGroup/UserInfo 仓储 + RbacReadService.getUserTeamRoles。
- 数据：只读；无 schema 变更、不触 region 后端。
- 鉴权：JWTAuthApiView（仅登录）。
- 简化：join/request 段空（Applicants defer）；active_teams role 仅 creater→owner（非 creater 遗留角色 defer）；均以 interop 实测为准校准（两 defer 项 interop 为空/owner，一致）。
