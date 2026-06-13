## Context
`EnterpriseTeamOverView.get` bean={active_teams(get_enterprise_user_active_teams), new_join_team(tenants[:3]+Applicants is_pass=1), request_join_team(Applicants is_pass=0)}。tenants=get_tenants_by_user_id_and_eid(PermRelTenant by enterprise.ID+user→tenant PK→Tenants,-ID 去重)。active_teams num=ServiceGroup count、按 num 降序[:3]、role 经 get_role_names(tenant_perms.role_id join tenant_user_role；interop role_id NULL→owner 回退)。region=get_team_region_names(TenantRegionInfo)。create_time ISO。

## Goals / Non-Goals
**Goals:** active_teams + new_join_team 纯 DB 读 + 对 7070(interop)校准。
**Non-Goals:** Applicants 加入申请(join/request 段)、非-creater tenant_user_role 遗留角色、region 后端。

## Decisions
### 决策 1：modules/enterprise 新增 EnterpriseTeamOverviewService + controller；复用现有仓储
### 决策 2：active_teams——PermRelTenant(user)→Tenants(enterprise 过滤,-ID 去重)→仅有 region 者→{...,num=ServiceGroup count,role=creater?"owner":null}→num 降序[:3]。create_time 原始 LocalDateTime(Jackson ISO)
### 决策 3：new_join_team——同 tenants[:3]，roles=getUserTeamRoles 角色名+creater"owner"，is_pass:true
### 决策 4：join/request 段空(Applicants defer)；role 非 creater 时 null(tenant_user_role 遗留 defer)——interop 实测 join/request 空、role owner，一致
### 决策 5：owner_name=owner.nick_name(get_name)；JWTAuthApiView 仅登录

## Risks / Trade-offs
- **[Applicants defer]** join/request 空；interop 无申请，一致；有申请的企业会缺——文档标注。
- **[role 遗留机制]** 非 creater 用 null(tenant_user_role 表+raw SQL defer)；interop=creater→owner，一致。
- **[num=ServiceGroup count]** 复用 ServiceGroupRepository.findByTenantId(tenant_id).size。

## Migration Plan
无 schema 变更，纯只读。校准：interop deep-diff（active_teams/new_join_team 全字段、create_time ISO、role owner、num 0、request 空）。

## Open Questions
- owner_name get_name() 取 nick_name 还是 real_name——以 interop 实测(=nick "interop")校准。
