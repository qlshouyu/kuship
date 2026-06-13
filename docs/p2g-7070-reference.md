# P2-g（企业团队概览 overview/team）7070 校准基线
- `GET /console/enterprise/{eid}/overview/team` → bean {active_teams, new_join_team, request_join_team}。
- active_teams 项：{tenant_id, team_alias, owner, owner_name, enterprise_id, create_time(ISO), team_name, region, region_list, num(=ServiceGroup 数), role}；按 num 降序[:3]；role=creater→"owner"。
- new_join_team 项（tenants[:3]）：{team_name, team_alias, team_id, create_time(ISO), region, region_list, enterprise_id, owner, owner_name, roles(角色名+creater 加"owner"), is_pass:true}。
- request_join_team: []（Applicants 加入申请域 defer；interop 实测空）。
- 源：tenants=get_tenants_by_user_id_and_eid(PermRelTenant by 企业PK+user,-ID 去重)；region=TenantRegionInfo；num=ServiceGroup count；owner_name=get_name(real_name||nick_name)；create_time ISO(Jackson 默认)。
- defer：① Applicants join(is_pass=1 并入 new_join)/request 段；② active_teams 非-creater 角色(get_role_names 走 tenant_perms.role_id join tenant_user_role 遗留表+raw SQL；interop role_id NULL→owner 回退)。interop 实测两 defer 项均空/owner，一致。
- 校准：interop deep-diff 8000 vs 7070 全 0 ✓。单测 97/97。
