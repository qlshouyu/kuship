## Context
EnterpriseConvertResource.get→convert_resource→region GET /v2/cluster/convert-resource?eid&content&namespace→body{bean:{...,unclassified}}；view 同 resource：pop+回填 unclassified 到末尾。region 转换慢(rainbond 超时 30s)。复用 P3-a RegionClient mTLS + P3-d moveKeyToEnd。

## Goals / Non-Goals
**Goals:** 资源转换读 + unclassified 末位 + 对 7070 比对。
**Non-Goals:** POST 导入(建租户/应用)、lang_version。

## Decisions
### 决策 1：RegionNamespaceService.convertResource：exchange GET /v2/cluster/convert-resource(URLEncode)→parse body.bean→moveKeyToEnd("unclassified")；超时档 IMPORT_BACKUP(300s) 覆盖 rainbond 30s
### 决策 2：content 默认 all、namespace 默认空；bean null→{}；JWTAuthApiView 仅登录

## Risks / Trade-offs
- region 后端依赖(dev 需 REGION_URL_OVERRIDE)；转换结果由集群状态确定，空闲下稳定可 deep-diff。

## Migration Plan
无 schema 变更。校准：8000 vs 7070 bean 结构 + unclassified 末位一致。

## Open Questions
- 无。
