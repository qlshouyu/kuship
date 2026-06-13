## Context
AppEnvView.get：env_type(inner/outer 必填，否则 400)，env_name 模糊(attr_name like)，page/page_size(默认 1/10)；raw SQL count + select ID,tenant_id,service_id,container_port,name,attr_name,attr_value,is_change,scope,create_time from tenant_service_env_var where tenant_id&service_id&scope=env_type[&attr_name like] order by attr_name limit start,end；env_dict 10 字段；bean={total}；general_message(bean=bean, list=env_list)。**is_change 来自 raw cursor → tinyint 为 int(0/1)**；create_time raw datetime → DRF isoformat(ISO 微秒)。

## Goals / Non-Goals
**Goals:** 环境变量列表(scope 分页) 对 7070 逐字节(含 is_change int、create_time ISO)。
**Non-Goals:** 增删改、build env。

## Decisions
### 决策 1：TenantServiceEnvVar.toEnvDict()(10 字段，is_change→int 0/1，create_time→LocalDateTime 由 Jackson ISO 微秒)；repo Page findBy...ScopeOrderByAttrName / ...ScopeAndAttrNameContainingOrderByAttrName
### 决策 2：ComponentEnvsService：env_type 校验→400；PageRequest(page-1,page_size)；Page.totalElements→bean.total
### 决策 3：controller bean={total} + putExtra("list")；page/page_size 默认 1/10

## Risks / Trade-offs
- is_change 必须 int 不能 bool（raw cursor 行为，区别于 to_dict 的 bool）——已显式 int。
- Spring Page 分页与 rainbond limit start,end 等价（offset=(page-1)*size）。

## Migration Plan
无 schema 变更。校准：探针 outer 环境变量 8000 vs 7070 逐字节。

## Open Questions
- 无。
