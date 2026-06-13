# P3-d 命名空间资源读 — 7070 校准基线

## 接口
`GET /console/enterprise/{enterprise_id}/regions/{region_id}/resource?content=all&namespace=`
- rainbond: `EnterpriseNamespaceResource.get` → `region_api.list_namespace_resources` → region `GET /v2/cluster/resource?eid&content&namespace`。
- view 把 `bean` 的 `unclassified` 键 `pop` 后回填 → **移到字典末尾**。
- 复用 P3-a RegionClient mTLS（dev 需 REGION_URL_OVERRIDE=https://localhost:8443）。

## 校准结果（8000 vs 7070，eid=b16bf28d..., rid=beb16025...）
```
顶层键 7070: ['unclassified']
顶层键 8000: ['unclassified']
unclassified 末位 8000: True
结构(名集合)一致: True
键顺序差异: 无(全一致)
```
说明：bean 是命名空间内 k8s 资源**名**(workloads/others 分类)，非运行态指标，空闲 all-in-one 下系统组件(rbd-*/coredns/minio 等)固定 → 可对 7070 比对。本集群无 rainbond 托管应用，顶层仅 `unclassified`。
