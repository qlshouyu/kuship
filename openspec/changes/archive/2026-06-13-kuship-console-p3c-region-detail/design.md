## Context
EnterpriseRegionsRUDView.get→get_enterprise_region(eid,region_id,check_status=False)→conver_region_info(check_status=False,level="open")。check_status 为布尔 False(≠"yes")→跳过 region 后端，纯 `__init_region_resource_data(level="open")`。level=open 比 list 的 level=safe 多 6 字段(wsurl/httpdomain/tcpdomain/ssl_ca_cert/cert_file/key_file，插在 provider_cluster_id 与 desc 之间)。create_time=ISO(Jackson 默认)。scope=os.getenv("IS_STANDALONE", region.scope)。

## Goals / Non-Goals
**Goals:** 单集群 open 级详情读 + 对 7070 逐字节校准。
**Non-Goals:** PUT/DELETE 写集群、check_status=yes 富化(P2-e 已覆盖)、namespace/resource。

## Decisions
### 决策 1：EnterpriseRegionReadService 加 getRegion(eid,region_id)：findByRegionId→toOpenDict(28 字段，open 块插在 provider_cluster_id 后/desc 前)；region_id 不存在→null
### 决策 2：scope 用 System.getenv("IS_STANDALONE") 回退 region.scope；JWTAuthApiView 仅登录；create_time ISO 无 formatter

## Risks / Trade-offs
- 响应含 ssl 证书明文——与 rainbond 一致(parity)，不额外脱敏。
- URL `regions/{region_id}` 与 `regions/{region_id}/namespace` 等更具体路径共存，Spring 按段数精确匹配，无冲突。

## Migration Plan
无 schema 变更。校准：8000 vs 7070 bean 28 字段逐字节一致。

## Open Questions
- 无。
