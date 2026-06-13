## Context
AppDetailView.get(非 vm/非 market)：service_model=service.to_dict()(BaseModel.to_dict 全列、model 顺序、datetime '%Y-%m-%d %H:%M:%S')；group_map=get_services_group_name([sid])(service_group_relation→service_group，无则 group_name=未分组/group_id=-1/k8s_app=default)；service_model+=group_name/group_id；service_model["namespace"]=tenant.namespace；disk_cap=30 if vm else 10，有 volume 则=volumes[0].volume_capacity；bean={service}, +event_websocket_url(region.wsurl=="auto"?ws://{host}:6060/event_log : wsurl+"/event_log"), +is_third(service_source=="third_party")。纯 DB，无 region 调用。

## Goals / Non-Goals
**Goals:** 组件详情(镜像类)纯 DB 全列 to_dict + 组/命名空间/磁盘/ws/is_third，对 7070 逐字节。
**Non-Goals:** vm/market 分支、volume 覆盖 disk_cap、third_party endpoints。

## Decisions
### 决策 1：TenantServiceInfo 扩全列(63)+toDict()(LinkedHashMap 精确顺序，datetime 空格格式，null 保留)；ServiceGroupRelation 实体+findByServiceId
### 决策 2：ComponentDetailService：toDict→put namespace=tenant.namespace(原位覆盖)→append group_name/group_id/disk_cap→bean{service,event_websocket_url,is_third}
### 决策 3：disk_cap=vm?30:10(volume 覆盖 defer)；event_websocket_url 非 auto→wsurl+"/event_log"，auto→ws://{request host}:6060/event_log

## Risks / Trade-offs
- to_dict 全列(63)——大但纯机械，model 顺序即 DB SHOW COLUMNS 顺序，已逐字段核对。
- event_websocket_url auto 分支依赖 request host(本环境 wsurl 非 auto，已具体值)。

## Migration Plan
无 schema 变更。校准：镜像组件 8000 vs 7070 bean 逐字节一致。

## Open Questions
- 无。
