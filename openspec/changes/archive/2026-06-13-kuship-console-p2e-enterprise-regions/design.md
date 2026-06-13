## Context
`EnterpriseRegionsLCView.get` → `get_enterprise_regions(eid, level="safe", status, check_status=request 默认"")` → `conver_region_info` → check_status!="yes" 时仅 `__init_region_resource_data(region, "safe")`（纯 DB region_info 列 + 固定资源默认 + enterprise_alias）。已实测 7070 该路径 22 字段、资源为默认值。

## Goals / Non-Goals
**Goals:** safe 级集群列表纯 DB 读 + 对 7070 校准（真实 rainbond 集群）。
**Non-Goals:** check_status=yes 实时资源/健康(region 后端)、region 增删改、open 级敏感字段。

## Decisions
### 决策 1：modules/region 新增 EnterpriseRegionReadService + controller；RegionConfigRepository.findByEnterpriseId(+status)
### 决策 2：22 字段按序构建 LinkedHashMap；资源字段固定默认(0/"unknown"/"ok"/false)；region_type JSON 解析(空→[])；create_time 原始 LocalDateTime(Jackson ISO，对齐 DRF，同 P2-c)
### 决策 3：enterprise_alias 由 TenantEnterpriseRepository.findByEnterpriseId(eid).enterpriseAlias
### 决策 4：scope 取 region.scope（IS_STANDALONE env 覆盖暂不处理，kuship 环境未设，实测=region.scope）
### 决策 5：JWTAuthApiView 仅登录

## Risks / Trade-offs
- **[region_type JSON]** 列存 JSON 字符串(如"[]")，用 Jackson 解析为 List；null/空→[]。
- **[资源默认]** check_status 空→固定默认，与 7070 同路径一致；实时资源留后续(region 后端)。
- **[scope IS_STANDALONE]** 暂不处理 env 覆盖；实测一致。

## Migration Plan
无 schema 变更，纯只读。校准：真实 rainbond 集群 deep-diff（含 create_time ISO、region_type []、资源默认）。

## Open Questions
- 无（22 字段/默认值/safe 投影均已对 7070 实测）。
