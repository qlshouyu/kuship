## 1. 实体与服务
- [x] 1.1 ConsoleSysConfig 实体 + ConsoleSysConfigRepository.findByKeyIn
- [x] 1.2 EnterpriseInfoService.info：to_dict(空格 create_time)+default_region({})+config(24 键,key.lower→{enable,value},json 解析)+default_market_url/disable_logo
- [x] 1.3 EnterpriseController GET /console/enterprise/{eid}/info
- [x] 1.4 单测：config 项 enable/value、json 解析、string NULL→null/""、create_time 空格
## 2. 校准
- [x] 2.1 deep-diff 8000 vs 7070(interop；全字段+24 配置项+json 值+create_time)，迭代收敛
- [x] 2.2 全量构建+单测；docs；openspec 校验归档
