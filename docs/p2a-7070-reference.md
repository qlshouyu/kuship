# P2-a（应用列表读）7070 校准基线

> 环境同前；interop(700002) default 团队(tenant_id=a274b41c…) 无应用 → `list:[]`。

## GET /console/teams/{team_name}/groups?region_name=rainbond
```
{"code":200,"msg":"success","msg_show":"查询成功","data":{"bean":{},"list":[
  {"group_name":"<名>","group_id":<ID int>,"group_note":<note|null>}
]}}
```
- 源 `TenantGroupView.get`：`list_tenant_group_on_region` = `ServiceGroup.objects.filter(tenant_id=团队tenant_id, region_name=region).order_by("-update_time","-order_index")`，**纯 DB**；项仅 `{group_name, group_id(=ID), group_note(=note)}`，无 region 运行态聚合。
- interop default 无应用 → `list:[]`（已实测）。
- 非空校准方案：DB 直插临时 service_group 行(tenant_id=a274b41c…, region_name=rainbond) → 8000 与 7070 同读 deep-diff → 删除。
- GET 无权限码门槛（APP_CREATE_PERMS 的 get 所需码为空）。

## 实跑校准结果（8000 vs 7070）
- 空列表：default 团队 `list:[]`，8000 与 7070 完全一致。✓
- 非空：DB 直插临时行(group_name=p2a-tmpapp, tenant_id=a274b41c…, region=rainbond, k8s_app 必填) → 两端同读 `[{group_name,group_id,group_note}]` deep-diff 一致 → 删除。✓
- 清理：临时 service_group 行已删，default 应用数=0。
- 坑：service_group NOT NULL 无默认列含 **k8s_app**，DB 直插须提供。
结论：应用列表读与 7070 一致。create_app/运行态聚合(region)留后续。
