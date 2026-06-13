## Context
`EnterpriseRUDView.get`：enter.to_dict() + default_region({}，ENABLE_CLUSTER!=true) + EnterpriseConfigService.initialization_or_get_config(base_cfg_keys[OAUTH_SERVICES]+cfg_keys[23] → {key.lower():{enable,value}})+default_market_url(env)+disable_logo(env)。console_sys_config.key UNIQUE → get_config_by_key 按 key 全局查(eid 仅元数据)。json value 为 Python repr(单引号/None)，rainbond eval 解析。enable_team_resource_view 是 tenant_enterprise 列(裸 bool，在 to_dict)。create_time 空格格式。

## Goals / Non-Goals
**Goals:** 企业 info 多源读 + 对 7070 校准(含 json 值解析/create_time 空格/24 配置项)。
**Non-Goals:** config 写、ENABLE_CLUSTER provision、自定义/企业版字段。

## Decisions
### 决策 1：ConsoleSysConfig 实体(@Table console_sys_config: key`backtick`/type/value/enable/enterprise_id) + findByKeyIn
### 决策 2：config 按 key 全局唯一读(无 eid 作用域回退——key UNIQUE)；type=json→pythonReprToJson(None→null/True→true/False→false/单→双引号)+jackson 解析；string→NULL→null 否则原串(含"")
### 决策 3：to_dict create_time 空格格式 yyyy-MM-dd HH:mm:ss(区别 users/regions 的 ISO)；logo 列不单列(配置 LOGO 覆盖)；enable_team_resource_view 裸 bool
### 决策 4：default_region={}；default_market_url="";disable_logo=false(env 未设)；pom 显式 jackson-databind
### 决策 5：JWTAuthApiView 仅登录

## Risks / Trade-offs
- **[Python-repr 解析]** 值内无嵌套引号/特殊字符(实测：路径/空串/None)，naive 转换安全；deep-diff 兜底。
- **[create_time 空格]** 企业 to_dict 空格格式(≠P2-c/e 的 ISO)——显式 formatter。
- **[env 派生字段]** default_market_url/disable_logo 本环境默认("",false)，实测一致。

## Migration Plan
无 schema 变更，纯只读。校准：interop deep-diff(24 配置项+企业字段+json 值)迭代收敛。

## Open Questions
- 无（全字段/解析已对 7070 实测；diff 迭代验证 Python-repr 解析正确性）。
