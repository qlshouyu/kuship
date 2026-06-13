# component-ports-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3j-component-ports. Update Purpose after archive.
## Requirements
### Requirement: 组件端口列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/ports`（对齐 `AppPortView.get`）：对每个端口返回 port.to_dict + service_alias + environment(inner service 连接变量) + inner_url + outer_url + bind_domains +（非 http）bind_tcp_domains，并据 region 网关 routes/{http|tcp}/domains 结果设置 is_outer_service，`msg_show=查询成功`。

#### Scenario: 内部 TCP 端口
- **WHEN** 已认证用户请求且组件有一个 is_inner_service 的 TCP 端口
- **THEN** environment 含 {desc,name,value} 连接变量、inner_url=HOST:PORT、outer_url=""(未开外网)、bind_domains=[]、bind_tcp_domains=[]、is_outer_service=false，与 7070 一致

#### Scenario: 无端口
- **WHEN** 组件无端口
- **THEN** list=[]

