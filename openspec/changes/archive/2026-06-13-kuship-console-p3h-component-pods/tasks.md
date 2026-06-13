## 1. 实现
- [x] 1.1 TenantServiceInfo 加 k8s_component_name
- [x] 1.2 ComponentPodsService.listPods（转换+主容器置首+内存换算）
- [x] 1.3 ComponentPodsController GET .../pods（list=dict）
- [x] 1.4 单测：转换/跳过 POD/主容器置首/usage_rate
## 2. 校准
- [x] 2.1 running 探针 deep-diff 8000 vs 7070（稳定字段）
- [x] 2.2 全量构建+单测；docs；清理探针；openspec 校验归档
