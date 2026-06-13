# P3-b 集群命名空间读 — 7070 校准基线

## 接口
`GET /console/enterprise/{enterprise_id}/regions/{region_id}/namespace?content=all`
- rainbond: `EnterpriseRegionNamespace.get` → `get_namespaces(eid, region_id, content)` → region `GET /v2/cluster/namespace?eid=&content=` → body `{list:[...]}`；console `bean = body["list"]`。
- region_id 路径变量；region 调用用 region_name（由 region_id 解析）。content 缺省 all。
- 复用 P3-a RegionClient mTLS（dev 需 REGION_URL_OVERRIDE=https://localhost:8443）。

## 校准结果（8000 vs 7070，eid=b16bf28d..., rid=beb16025...）
```
7070: {"code":200,"msg":"success","msg_show":"获取成功","data":{"bean":["default"],"list":[]}}
8000: {"code":200,"msg":"success","msg_show":"获取成功","data":{"bean":["default"],"list":[]}}
MATCH ✓
```
命名空间名稳定 → 可直接对 7070 比对（区别于实时资源指标）。
