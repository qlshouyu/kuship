## 1. 实现
- [x] 1.1 TenantServiceVolume 实体+repo；TenantServiceInfo.getCreateStatus
- [x] 1.2 ComponentVolumesService（to_dict+status+dep_services+first+region 卷状态）
- [x] 1.3 ComponentVolumesController GET .../volumes
- [x] 1.4 单测：not_bound/first/字段顺序/空
## 2. 校准
- [x] 2.1 未部署组件+volume deep-diff 8000 vs 7070
- [x] 2.2 全量构建+单测；docs；清理；openspec 校验归档
