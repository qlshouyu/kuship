## Context
AppBriefView.get：非 market → bean=service.to_dict()(BaseModel.to_dict 全列)，msg="查询成功"；market → check_market_service_info 异常时改 msg。复用 P3-i 的 TenantServiceInfo.toDict()(63 列、datetime 空格格式)。与 detail 区别：brief 不附加 group_name/group_id/disk_cap，namespace 为 DB 原值(不覆盖)。

## Goals / Non-Goals
**Goals:** 组件概览 bean=to_dict 全列 对 7070 逐字节。
**Non-Goals:** market 校验分支(msg 变化)、put 改名。

## Decisions
### 决策 1：ComponentBriefService.getBrief=findByServiceAlias→toDict()；ComponentBriefController GET .../brief→bean；msg 恒 "查询成功"(market defer)

## Risks / Trade-offs
- market 组件 msg 可能不同——defer(探针为 docker_image 非 market)。

## Migration Plan
无 schema 变更。校准：镜像组件 8000 vs 7070 bean 63 字段逐字节一致。

## Open Questions
- 无。
