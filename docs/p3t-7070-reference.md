# P3-t 可被依赖但未依赖的组件列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/dependency-reverse?page=&page_size=&search_key=&condition=`
- rainbond: `AppDependencyReverseView.get` → `get_reverse_undependencies` —— 纯 DB。
- 来源 = team region 组件(get_tenant_region_services，排除自己) 排除已反向依赖本组件的；每项 dep_info 6 字段(service_cname,service_id,service_type,service_alias,group_name,group_id)；search_key+condition(group_name|service_name；无 condition 时两者；无 search_key 全取)过滤；本应用(group_id==current)在前/其他在后；分页默认 25。
- envelope：general_message(list=, total=) → data={bean:{}, list, total}。

## 校准结果（8000 vs 7070，team=default，单组件 grb496c7）
```
[无参]  data={bean:{},list:[],total:0}  MATCH ✓
[过滤 search_key=foo&condition=service_name]  MATCH ✓
```
- 单组件团队 → list 空。排除自己/已反向依赖、search 过滤(group_name/service_name/both)、current/other 分组由单测覆盖。
- 纯 DB；探针仅创建即可校准。校准毕清理恢复原状。
- **defer**：建立/解除反向依赖(POST/DELETE)。
