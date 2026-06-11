# region API: services_status 调用契约（rainbond 端真值）

## Region URL

```
POST /v2/tenants/{region_tenant_name}/services_status
```

- `region_tenant_name` 是 **`Tenants.namespace`**（rainbond `tenant_region.region_tenant_name`），不是 `tenant_name`
- rainbond 源：`www/apiclient/regionapi.py:838`

## Request body

```json
{
  "service_ids": ["605f3cf7250b4ca49a3a0df8b6512dd5", "..."],
  "enterprise_id": "f69054e43377650c9340ea905d756d04"
}
```

- `service_ids` 是 **JSON 数组**（非 comma-separated）
- rainbond `service_status` 直接 `json.dumps(body)`，无字段名转换

## Response body（region Go 端，**根级 list，无 data 封装**）

```json
{
  "list": [
    {
      "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
      "status": "running",
      "status_cn": "运行中",
      "used_mem": 64,
      ...
    }
  ]
}
```

- 响应**根**上直接 `list` 字段
- rainbond `status_multi_service` 读法：`body["list"]`
- **不是** `data.list` 也不是 `data.bean.list`

## kuship 当前 bug

`ServiceStatusOperationsImpl.serviceStatus` 现状：

```java
String url = "/v2/tenants/" + encode(tenantName) + "/services_status";   // ❌ 应用 namespace
return safeBean(processor.extractBean(resp, Map.class, ...));            // ❌ extractBean 读 data.bean
```

两处错误叠加 → 任何调用都返空 Map → status 永远 fallback 到 `unknow`。

## 修复

1. 调用方传 `tenant.getNamespace()`（fallback `tenant_name`）作为 `tenantName` 参数
2. `serviceStatus` 改用 `processor.checkStatus()` 拿完整 JsonNode → 整树 `convertValue` 为 Map

唯二调用点：

- `AppGroupServiceAggregator.aggregate`（本 change 主战场）
- `AppTopologyController.topological`（占位实现，旧 String.join 也是错的）
