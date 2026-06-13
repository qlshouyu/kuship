# P2-h（企业信息 enterprise/{eid}/info）7070 校准基线
- bean = tenant_enterprise.to_dict(ID/enterprise_id/enterprise_name/enterprise_alias/create_time(**空格 yyyy-MM-dd HH:mm:ss**)/enterprise_token/is_active/enable_team_resource_view(裸 bool)) + default_region({}) + 配置项(OAUTH_SERVICES+23 cfg_keys，console_sys_config 按 key 全局唯一读，key.lower()→{enable,value})+default_market_url("")+disable_logo(false)。
- **console_sys_config.key UNIQUE → 按 key 全局查，无 eid 作用域回退**(eid 仅元数据)。json 类型 value 是 **Python repr**(单引号/None)，转 JSON(None→null/True→true/False→false/单→双引号，值内无嵌套引号)+jackson 解析。string：NULL→null 否则原串(含"")。
- enable_team_resource_view 是 tenant_enterprise 列(裸 bool)。create_time 空格格式(≠P2-c/e 的 ISO——企业 to_dict 自定义)。
- pom 显式加 jackson-databind(原仅 runtime 传递，编译不可见)。
- 校准：interop deep-diff 8000 vs 7070 **首次即全 0**(24 配置项+json 值+企业字段+create_time)。单测 98/98。
- defer：config 写(PUT)、ENABLE_CLUSTER provision、自定义/企业版字段(env，本环境默认)。
