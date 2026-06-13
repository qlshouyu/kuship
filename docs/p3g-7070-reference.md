# P3-g 组件状态读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/status?region_name=rainbond`
- rainbond: `AppStatusView.get` → `app_service.get_service_status(tenant, service)` → region `GET /v2/tenants/{region_tenant_name}/services/{service_alias}/status?enterprise_id=` 取 `cur_status/start_time/vm_restore`；`get_status_info_map(status)` 用 `www/utils/status_translate.status_map` 表算 `status_cn/disabledAction/activeAction`；region 异常 → status=`unKnow`、vm_restore={}。bean 加 `check_uuid`=service.check_uuid。
- bean 顺序：check_uuid, status, status_cn, disabledAction, activeAction, start_time, vm_restore。
- region token 空 → mTLS 认证（复用 P3-a RegionClient，dev 需 REGION_URL_OVERRIDE）。

## 校准结果（8000 vs 7070，team=default，running 探针组件）
```
7070/8000 bean:
  {check_uuid:"", status:"running", status_cn:"运行中",
   disabledAction:["restart"],
   activeAction:["stop","deploy","visit","manage_container","reboot"],
   start_time:"2026-06-13T16:18:22+08:00", vm_restore:{}}
键顺序一致: True    稳定字段差异: MATCH ✓ (连 start_time 都因 pod 持续运行而相同)
```
- 探针组件经 [[kuship-region-component-provision]] 配方部署（registry:2.6.2，arm64，手补 image 字段），校准毕已删除（tenant_service=0、default ns pods=0）。
- status_map 全表（running/starting/.../succeeded 24 档 + 表外→未知/空动作）已 1:1 移植至 `StatusTranslate`；"deployed"(原 Python 裸字符串会崩)按表外处理。
