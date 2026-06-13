## 1. 仓储与服务
- [ ] 1.1 `RegionConfigRepository.findByEnterpriseId(String)`（+ findByEnterpriseIdAndStatus 备 status 过滤）
- [ ] 1.2 `EnterpriseRegionReadService.listRegions(eid, status)`：region_info→22 字段 safe map（资源默认/region_type JSON/enterprise_alias/create_time ISO）
- [ ] 1.3 `EnterpriseRegionController GET /console/enterprise/{enterprise_id}/regions`
- [ ] 1.4 单测：字段集/默认值/region_type 解析/enterprise_alias
## 2. 校准
- [ ] 2.1 deep-diff 8000 vs 7070（真实 rainbond 集群；含 create_time、region_type、资源默认）
- [ ] 2.2 全量构建+单测；docs；openspec 校验归档
