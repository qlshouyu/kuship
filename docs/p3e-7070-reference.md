# P3-e 命名空间资源转换读 — 7070 校准基线

## 接口
`GET /console/enterprise/{enterprise_id}/regions/{region_id}/convert-resource?content=all&namespace=`
- rainbond: `EnterpriseConvertResource.get` → `region_api.list_convert_resource` → region `GET /v2/cluster/convert-resource?eid&content&namespace`（rainbond 超时 30s）。
- view 把 `bean` 的 `unclassified` 键 `pop` 后回填 → 移到末尾。
- 复用 P3-a RegionClient mTLS（超时档 IMPORT_BACKUP 300s）+ P3-d moveKeyToEnd（dev 需 REGION_URL_OVERRIDE）。

## 校准结果（8000 vs 7070，eid=b16bf28d..., rid=beb16025...）
```
顶层键 7070/8000: ['unclassified']      unclassified 末位: True       键顺序: 全一致
有序逐元素差异: 仅 1 条
  → .unclassified.kubernetes_resources[28].content（rbd-system 下 rainbond-operator
     leader 选举租约 ConfigMap c3e7a49c.rainbond.io）的 renewTime / resourceVersion
     每 ~2s 心跳变化（7070=07:14:29Z/141351，8000=07:14:31Z/141354）
```
**结论**：除该 leader 选举租约（活体集群心跳状态，非 kuship 偏差）外，bean 逐字节一致 —— 符合 region "稳定字段 parity" 验证策略（见 docs/p3a-7070-reference.md）。透传 + unclassified 末位实现正确。
