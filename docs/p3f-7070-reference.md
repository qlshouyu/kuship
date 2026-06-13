# P3-f 集群 CNB 框架读 — 7070 校准基线

## 接口
`GET /console/enterprise/{enterprise_id}/regions/{region_id}/cnb/frameworks?lang=nodejs`
- rainbond: `EnterpriseRegionCNBFrameworks.get` → `region_cnb_config.show_cnb_frameworks` → `region_api.get_cnb_frameworks` → region `GET /v2/cluster/cnb/frameworks?lang=`。
- view `list = body.get("list", [])`；整体 try/except → 异常回退 `code=400 msg=failed msg_show=获取CNB框架列表失败`。
- 响应包络用 **list=**（非 bean）。复用 P3-a RegionClient mTLS（dev 需 REGION_URL_OVERRIDE）。

## 校准结果（8000 vs 7070，eid=b16bf28d..., rid=beb16025...，多 lang）
```
lang=nodejs : 14 项  MATCH ✓
lang=(缺省) : 14 项  MATCH ✓   (缺省=nodejs)
lang=go     : 0 项   MATCH ✓
lang=java   : 0 项   MATCH ✓
lang=python : 0 项   MATCH ✓
```
框架定义为集群静态配置 → 逐字节稳定可比对（nodejs 返回 nextjs/nuxt/docusaurus 等 14 项）。
