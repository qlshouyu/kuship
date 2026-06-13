## Context
AppPortView.get：port_repo.get_service_ports(按 ID) 每 port → to_dict + service_alias + get_port_variables(environment: inner→env_by_container_port [{desc:env.name,name:env.attr_name,value:env.attr_value}]; outer_service: outer→tcp(tcpdomain,mapping/lb_mapping_port) / http(httpdomain 复合,port 80 或 split))；inner_url(environment&inner→HOST/PORT 解析,默认 host 127.0.0.1);outer_url(outer_service→domain:port);bind_domains=[];http→网关 routes/http/domains→bind_domains+is_outer;否则→网关 routes/tcp/domains→bind_tcp_domains+is_outer。**port.save() 持久化 is_outer**——kuship 读路径省去 save（共享库已由 7070 收敛）。网关 api_gateway_get_proxy=region.url+path（token 空走 mTLS，复用 RegionClient）。

## Goals / Non-Goals
**Goals:** 端口列表全字段（to_dict+env+url+网关绑定）对 7070 校准（内部 TCP 端口）。
**Non-Goals:** 端口增删改、save 副作用、http bind_domains 非空深度校准。

## Decisions
### 决策 1：TenantServicesPort(to_dict 12 列)+TenantServiceEnvVar 实体/repo(findBy tenant+service+container_port OrderById)
### 决策 2：ComponentPortsService 逐端口装配 environment/inner_url/outer_url/bind_domains/bind_tcp_domains/is_outer；网关经 RegionClient.exchange GET routes/{kind}/domains→body.list；异常/空→[]
### 决策 3：不 save；outer_url 用 DB is_outer（与 rainbond 一致：outer_url 在网关 override 前由 get_port_variables 算）；is_outer 显示值按网关结果

## Risks / Trade-offs
- 端口域名经网关 + save 副作用：kuship 读不写库，依赖共享库已被 7070 GET 收敛（is_outer 稳定）；校准在收敛态进行。
- http bind_domains 非空分支实现但用关闭外网 TCP 端口校准（bind_*=[]）。

## Migration Plan
无 schema 变更。校准：内部 TCP 端口 8000 vs 7070 逐字节一致。

## Open Questions
- 无。
