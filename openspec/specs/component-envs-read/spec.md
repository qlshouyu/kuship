# component-envs-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3k-component-envs. Update Purpose after archive.
## Requirements
### Requirement: 组件环境变量列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/envs`（对齐 `AppEnvView.get`）：按 `env_type`(inner/outer) 查 tenant_service_env_var(scope=env_type)，可选 `env_name` 模糊(attr_name)，按 attr_name 排序分页（page/page_size），返回 `bean={total}`、`list=[10 字段 env_dict]`（is_change 为整数 0/1，create_time ISO 微秒），`msg_show=查询成功`。

#### Scenario: 返回环境变量列表
- **WHEN** 已认证用户请求 env_type=outer 且组件有 outer 环境变量
- **THEN** bean.total 与匹配数一致，list 各项 10 字段与 7070 逐字节一致

#### Scenario: env_type 缺失或非法
- **WHEN** env_type 不在 {inner,outer}
- **THEN** 返回 code=400、msg_show=参数异常

