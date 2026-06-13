# P3-h 组件实例列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/pods?region_name=rainbond`
- rainbond: `ListAppPodsView.get` → `region_api.get_service_pods` → region `GET /v2/tenants/{region_tenant_name}/services/{service_alias}/pods?enterprise_id=`。
- 转换(foobar)：每 pod → {pod_name, pod_status, manage_name="manager", container:[...]}；container 跳过 "POD" 键；内存 **bytes→MB**(/1024/1024，round2)；usage_rate=memory_usage*100/memory_limit(limit 0→0)；**主容器**(k8s_component_name 命中且非 default-tcpmesh)与首元素交换。
- **`data.list` 是 dict** `{new_pods, old_pods}`（非数组！），`data.bean={}`；old_pods 为 null 时保留 null。`msg_show=操作成功`。
- 复用 P3-a RegionClient(mTLS，dev 需 REGION_URL_OVERRIDE)。

## 校准结果（8000 vs 7070，team=default，running 探针 grc128fc）
```
data 键: ['bean','list']  (bean={}, list=dict) 一致
7070/8000 list.new_pods[0]:
  {pod_name:"parity-app-parity-pods-66cb75b795-c296m", pod_status:"RUNNING",
   manage_name:"manager",
   container:[{container_name:"parity-pods", memory_limit:512.0,
               memory_usage:3.76, usage_rate:0.73}]}
  old_pods: null
稳定字段一致: MATCH ✓ (pod_name/pod_status/manage_name/container_name/memory_limit/结构；连活体 memory_usage/usage_rate 同刻也相同)
```
- 探针组件经 [[kuship-region-component-provision]] 配方部署（本次用独立 app 组 parity-app 避免 is_demo 的 image-demo k8s app 名冲突）；校准毕已删除组件+空组，库恢复原状。
- 实现要点：`data.list` 放 dict 用 `GeneralMessage.message(...).putExtra("list", map)` 覆盖默认 []；内存 round 用 BigDecimal HALF_EVEN 对齐 Python round。
