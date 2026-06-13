## Context
EnterpriseRegionNamespace.get→get_namespaces(eid,region_id,content)→region_api.list_namespaces→region GET /v2/cluster/namespace?eid&content→body{list:[...]}；console bean=body.list。region_id 路径变量，region 调用用 region_name(由 region_id 解析)。复用 P3-a RegionClient mTLS。稳定(命名空间名)。

## Goals / Non-Goals
**Goals:** 命名空间读 + 对 7070 校准(bean=数组)。
**Non-Goals:** 命名空间资源/转换、lang_version。

## Decisions
### 决策 1：RegionConfigRepository.findByRegionId(uuid)→region_name；RegionNamespaceService.listNamespaces 调 RegionClient.exchange GET /v2/cluster/namespace?eid=&content=(URLEncode)→parse body.list
### 决策 2：bean=list 数组(空→[])；content 默认 all；JWTAuthApiView 仅登录

## Risks / Trade-offs
- region 后端依赖(dev 需 REGION_URL_OVERRIDE)；命名空间名稳定→可直接对 7070 比对。

## Migration Plan
无 schema 变更。校准：8000 vs 7070 bean=["default"] 一致。

## Open Questions
- 无。
