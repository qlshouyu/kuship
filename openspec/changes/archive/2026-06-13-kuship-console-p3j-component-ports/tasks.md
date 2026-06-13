## 1. 实现
- [x] 1.1 TenantServicesPort/TenantServiceEnvVar 实体+repo
- [x] 1.2 ComponentPortsService（to_dict+env+inner/outer url+网关 bind）
- [x] 1.3 ComponentPortsController GET .../ports
- [x] 1.4 单测：environment/inner_url/字段顺序/网关空→is_outer false
## 2. 校准
- [x] 2.1 内部 TCP 端口 deep-diff 8000 vs 7070
- [x] 2.2 全量构建+单测；docs；清理；openspec 校验归档
