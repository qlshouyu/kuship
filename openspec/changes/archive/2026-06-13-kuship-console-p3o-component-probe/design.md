## Context
AppProbeView.get：third_party→get_service_probe(first)；非 third_party：无 mode→get_service_probe(first)、有 mode→get_service_probe_by_mode(mode first)；404→general_message(404,"get probe error","探针不存在...")(HTTP 默认 200)；成功→bean=probe.to_dict()(service_probe 15 列,is_used bool)。

## Goals / Non-Goals
**Goals:** 探针读 有/无 两情形 对 7070 逐字节。
**Non-Goals:** 增删改、第三方组件特殊处理。

## Decisions
### 决策 1：ServiceProbe(to_dict 15 列)+repo findFirstByServiceId/findFirstByServiceIdAndMode
### 决策 2：getProbe→Optional；controller 找到 bean(200)、否则 message(404,"get probe error","探针不存在，您可能并未设置检测探针")(HTTP 200 body code 404)

## Risks / Trade-offs
- 无。
## Migration Plan
无 schema 变更。校准：有探针 to_dict + 无探针 404 两情形逐字节一致。
## Open Questions
- 无。
