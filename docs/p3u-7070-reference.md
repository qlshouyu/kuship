# P3-u 可依赖但未依赖的组件列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/un_dependency?page=&page_size=&search_key=&condition=`
- rainbond: `AppNotDependencyView.get` → `get_undependencies` —— 纯 DB。
- 候选 = team region 组件(排除自己) 中**有 is_inner_service 端口**(才能被依赖) 且 不在本组件正向 dep_service_ids；dep_info 6 字段(service_cname,service_id,service_type,service_alias,group_name,group_id)；search_key+condition(group_name|service_name；无 condition 两者；**非法 condition 且遍历到候选→400 condition参数错误**)；本应用(group_id==current)在前；分页默认 25。
- envelope：general_message(list=, total=) → data={bean:{}, list, total}。

## 校准结果（8000 vs 7070，team=default，单组件 gr5dfe03）
```
[无参]  data={bean:{},list:[],total:0}  MATCH ✓
[过滤 search_key=x&condition=group_name]  MATCH ✓
```
- 与 P3-t 区别：① 候选须开放对内端口；② 排除**正向**依赖；③ 非法 condition→400。单组件团队 → 空。inner 端口过滤/排除/condition 400/分组 由单测覆盖。
- 纯 DB；探针仅创建即可校准。校准毕清理恢复原状。
- **defer**：建立/解除依赖。
