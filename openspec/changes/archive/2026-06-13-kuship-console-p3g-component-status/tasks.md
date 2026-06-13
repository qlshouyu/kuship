## 1. 实现
- [x] 1.1 TenantServiceInfo 实体(tenant_service 只读) + Repository.findByServiceAlias
- [x] 1.2 StatusTranslate（status_map 全表移植 + getStatusInfoMap）
- [x] 1.3 ComponentStatusService.getServiceStatus（region status + 动作策略 + 异常 unKnow）
- [x] 1.4 ComponentStatusController GET teams/{tenantName}/apps/{serviceAlias}/status
- [x] 1.5 单测：status_map 关键档（running/undeploy/表外）+ 异常 unKnow
## 2. 校准
- [x] 2.1 部署 running 探针组件；deep-diff 8000 vs 7070(稳定字段)
- [x] 2.2 全量构建+单测；docs；清理探针；openspec 校验归档
