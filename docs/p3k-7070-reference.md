# P3-k 组件环境变量列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/envs?env_type=inner|outer&env_name=&page=&page_size=`
- rainbond: `AppEnvView.get` —— **纯 DB 分页**（raw SQL 游标）。
- env_type(inner/outer 必填，否则 code=400 msg_show=参数异常)；env_name 模糊(attr_name LIKE)；page/page_size 默认 1/10；ORDER BY attr_name；LIMIT 分页。
- env_dict 10 字段：ID, tenant_id, service_id, container_port, name, attr_name, attr_value, **is_change(整数 0/1)**, scope, create_time(**ISO 微秒**)。
- bean={total}，list=[env_dict]。

## 校准结果（8000 vs 7070，team=default，组件 gr2e1085，2 个 outer 环境变量）
```
[env_type=outer]            total=2  字段顺序+全字段 MATCH ✓ (is_change=0 整数, create_time ISO 微秒)
[env_type=inner]            total=0  MATCH ✓
[env_type=outer&env_name=HOST] total=1  MATCH ✓ (attr_name 模糊命中)
[env_type=bad]              两端 HTTP 400
```
- **关键对齐**：① is_change 是**整数 0/1**（rainbond 用 raw cursor，tinyint→int，区别于 to_dict 的 bool）；② create_time ISO 微秒（raw datetime→DRF isoformat，Jackson 默认 LocalDateTime 即匹配）。
- 探针经 [[kuship-region-component-provision]] 配方部署 + 加端口(自动生成 outer 连接变量)；校准毕删除组件+组，库恢复原状(tenant_service/env 全 0)。
