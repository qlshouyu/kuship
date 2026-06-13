## Context
AppStatusView.get：bean["check_uuid"]=service.check_uuid；status_map=app_service.get_service_status(tenant,service)；bean.update(status_map)。get_service_status：region check_service_status(region,tenant_name,service_alias,eid)→bean.cur_status/start_time/vm_restore；异常→status="unKnow",vm_restore={}；get_status_info_map(status) 用 www/utils/status_translate.status_map 表→{status,status_cn,disabledAction,activeAction}；+start_time+vm_restore。region path `/v2/tenants/{region_tenant_name}/services/{service_alias}/status?enterprise_id=`(region_tenant_name 来自 tenant_region)。region token 空→走 mTLS（复用 RegionClient）。

## Goals / Non-Goals
**Goals:** 组件状态读 + status_map 策略表逐字移植 + 对 7070 稳定字段 parity。
**Non-Goals:** kubeblocks 特判、detail/brief/pods/monitor、组件归属严格鉴权（与既有 app 接口一致按登录）。

## Decisions
### 决策 1：StatusTranslate.getStatusInfoMap(status) 移植 status_map 全表(LinkedHashMap 保序 status→status_cn/disabled/active)；表外 status→status_cn=未知,空动作；"deployed"(原 Python 为裸字符串会崩)按表外处理(未知)
### 决策 2：ComponentStatusService：findByServiceAlias→TenantServiceInfo;按 tenant_name 取 Tenants(eid)+TenantRegionInfo(region_tenant_name);exchange GET status→parse bean.cur_status/start_time/vm_restore;异常 status=unKnow,vm_restore={}
### 决策 3：bean 顺序 check_uuid,status,status_cn,disabledAction,activeAction,start_time,vm_restore(LinkedHashMap)；start_time 缺省""、vm_restore 缺省{}

## Risks / Trade-offs
- region 运行态依赖：需集群有 running 探针组件(provision 配方见 [[kuship-region-component-provision]])；start_time 随每次部署变→稳定字段 parity(status/动作列表比对)。
- region_tenant_name 取 tenant_region；多 region 时按 region_name 过滤。

## Migration Plan
无 schema 变更。校准：running 探针组件 8000 vs 7070 status/status_cn/disabledAction/activeAction 一致(start_time 排除)。

## Open Questions
- 无。
