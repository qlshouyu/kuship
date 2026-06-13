## Why
region 组件域续：环境变量列表读 `teams/{team}/apps/{serviceAlias}/envs`（`AppEnvView.get`）——**纯 DB 分页**，按 env_type(inner/outer) 查 tenant_service_env_var。可用探针组件对 7070 逐字节校准。

## What Changes
- 环境变量列表（对齐 `AppEnvView.get`）：`GET /console/teams/{tenantName}/apps/{serviceAlias}/envs?env_type=inner|outer&env_name=&page=&page_size=` → `bean={total}`、`list=[env_dict]`，每项=10 字段(ID,tenant_id,service_id,container_port,name,attr_name,attr_value,is_change,scope,create_time)，按 attr_name 排序、分页（page/page_size 默认 1/10）、env_name 模糊(attr_name LIKE)。`msg_show=查询成功`；env_type 缺失/非法 → 400 参数异常。
- **关键对齐**：is_change 输出**整数 0/1**（rainbond raw cursor 返回 tinyint 为 int）；create_time ISO 微秒（Jackson 默认）。
- **不包含**：环境变量增删改、build env（AppBuildEnvView）。

## Capabilities
### New Capabilities
- `component-envs-read`: 组件环境变量列表读（按 scope 分页，纯 DB）。

## Impact
- 代码：TenantServiceEnvVar 加 toEnvDict()+分页 repo；ComponentEnvsService；ComponentEnvsController。
- 数据：只读 tenant_service_env_var/tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：纯 DB，bean/list 全字段对 7070 逐字节一致。
