## Context
EnterpriseNamespaceResource.get→get_namespaces_resource→region GET /v2/cluster/resource?eid&content&namespace→body{bean:{...,unclassified:{...}}}；view 把 bean 的 unclassified pop 再回填(移到末尾)。复用 P3-a RegionClient mTLS。bean 内容是工作负载/资源**名**(非运行态)，空闲 all-in-one 下系统组件(rbd-*/coredns 等)固定→可比对。

## Goals / Non-Goals
**Goals:** 命名空间资源读 + unclassified 末位 + 对 7070 比对。
**Non-Goals:** convert-resource(GET/POST)、lang_version、运行态指标。

## Decisions
### 决策 1：RegionNamespaceService.listNamespaceResources：exchange GET /v2/cluster/resource(eid/content/namespace URLEncode)→parse body.bean(LinkedHashMap 保序)→moveKeyToEnd("unclassified")
### 决策 2：content 默认 all、namespace 默认空；bean 为 null→{}；JWTAuthApiView 仅登录

## Risks / Trade-offs
- region 后端依赖(dev 需 REGION_URL_OVERRIDE)；bean 为活体集群资源清单，空闲下名稳定可 deep-diff，若 pod 重启致 others 段微变则退稳定子集比对。

## Migration Plan
无 schema 变更。校准：8000 vs 7070 bean 结构 + unclassified 末位一致。

## Open Questions
- 无。
