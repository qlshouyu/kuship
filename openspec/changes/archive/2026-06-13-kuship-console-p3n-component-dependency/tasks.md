## 1. 实现
- [x] 1.1 TenantServiceRelation 实体+repo；findByServiceIdIn；getServiceCname/getServiceType
- [x] 1.2 ComponentDependencyService（正向/反向+dep_info+分组+分页）
- [x] 1.3 ComponentDependencyController（dependency/dependency-list）
- [x] 1.4 单测：空依赖、bean 差异(service_id)、dep_info 分组、分页 clamp
## 2. 校准
- [x] 2.1 探针两端点 deep-diff 8000 vs 7070
- [x] 2.2 全量构建+单测；docs；清理；openspec 校验归档
