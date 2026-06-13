## Why
region 运行态域开张：组件状态读 `teams/{team}/apps/{serviceAlias}/status`（`AppStatusView`），region 后端返回实时状态，console 侧按状态计算动作策略（disabledAction/activeAction）。已用可丢弃运行组件（[[kuship-region-component-provision]]）在集群部署 running 工作负载，可对 7070 做稳定字段 parity 校准。

## What Changes
- 组件状态（对齐 `AppStatusView.get` → `app_service.get_service_status` → region `GET /v2/tenants/{region_tenant_name}/services/{service_alias}/status?enterprise_id=`）：`GET /console/teams/{tenantName}/apps/{serviceAlias}/status?region_name=` → `bean` = {check_uuid, status, status_cn, disabledAction[], activeAction[], start_time, vm_restore}，其中 status/start_time/vm_restore 来自 region 的 cur_status/start_time/vm_restore；status_cn/disabledAction/activeAction 由 `status_map` 策略表按 status 计算；region 调用异常 → status=`unKnow`、vm_restore={}。
- **不包含**：kubeblocks 组件特判分支（is_kubeblocks → kubeblocks_service.get_kubeblocks_service_status，本探针非 kubeblocks，留后续）；组件 detail/brief/pods/monitor 等其它运行态读。

## Capabilities
### New Capabilities
- `component-status-read`: 组件状态读（region 运行态 + console 动作策略表）。

## Impact
- 代码：TenantServiceInfo 实体(tenant_service 只读)+repo；StatusTranslate(status_map 移植)；ComponentStatusService；ComponentStatusController。
- 数据：只读 tenant_service/tenant_region/tenant_info + 实时调 region；无 schema 变更。
- 鉴权：AppBaseView（登录+组件归属校验，本期仅登录，归属/权限码留与既有一致）。
- 验证：稳定字段 parity（status/status_cn/动作列表稳定；start_time 易变）对 7070 一致。dev 需 REGION_URL_OVERRIDE + 集群有 running 探针组件。
