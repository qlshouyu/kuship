## 1. 服务与接口
- [x] 1.1 `EnterpriseTeamOverviewService`：getUserTeams(eid,user)→active_teams(num/role/region,num 降序[:3]) + new_join_team(tenants[:3],roles+owner,is_pass) + request_join_team([])
- [x] 1.2 `EnterpriseTeamOverviewController GET /console/enterprise/{enterprise_id}/overview/team`
- [x] 1.3 单测：active_teams owner/num、new_join roles 含 owner、有 region 过滤、request 空
## 2. 校准
- [x] 2.1 deep-diff 8000 vs 7070(interop；active_teams/new_join_team/create_time/role/num/request)
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
