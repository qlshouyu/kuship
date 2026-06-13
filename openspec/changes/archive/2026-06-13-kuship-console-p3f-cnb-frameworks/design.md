## Context
EnterpriseRegionCNBFrameworks.get→region_cnb_config.show_cnb_frameworks→region_api.get_cnb_frameworks→region GET /v2/cluster/cnb/frameworks?lang→body{list:[...]}；view list=body.get("list",[])，整体 try/except→400。复用 P3-a RegionClient mTLS。框架定义为集群静态配置→稳定非空可逐字节比对。

## Goals / Non-Goals
**Goals:** CNB 框架读 + 对 7070 逐字节校准 + 异常 400 回退。
**Non-Goals:** lang_version CRUD、CNB 配置写。

## Decisions
### 决策 1：RegionCnbService.showFrameworks：findByRegionId→exchange GET /v2/cluster/cnb/frameworks?lang(URLEncode,缺省 nodejs)→parse body.list
### 决策 2：响应用 list= 包络(非 bean)；controller try/catch→GeneralMessage.message(400,failed,获取CNB框架列表失败)；JWTAuthApiView 仅登录

## Risks / Trade-offs
- region 后端依赖(dev 需 REGION_URL_OVERRIDE)；框架定义静态→可 deep-diff。
- 400 回退仅 HTTP body 对齐(envelope)，HTTP 状态码取决于全局包络，happy 路径为校准重点。

## Migration Plan
无 schema 变更。校准：8000 vs 7070 list 逐字节一致。

## Open Questions
- 无。
