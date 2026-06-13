# P3-c 单集群详情读 — 7070 校准基线

## 接口
`GET /console/enterprise/{enterprise_id}/regions/{region_id}`
- rainbond: `EnterpriseRegionsRUDView.get` → `get_enterprise_region(eid, region_id, check_status=False)` → `conver_region_info(check_status=False, level="open")` → `__init_region_resource_data(level="open")`。
- check_status=**False**(布尔，≠"yes") → 跳过 region 后端，资源/健康用固定默认 → **纯 DB，逐字节可比对**。
- level="open" 比列表 level="safe" 多 6 字段：wsurl/httpdomain/tcpdomain/ssl_ca_cert/cert_file/key_file（插在 provider_cluster_id 与 desc 之间）。
- scope = os.getenv("IS_STANDALONE", region.scope)；create_time = ISO（Jackson 默认）。

## bean 28 字段顺序
region_id, region_alias, region_name, status, region_type, enterprise_id, url, scope, provider, provider_cluster_id, **wsurl, httpdomain, tcpdomain, ssl_ca_cert, cert_file, key_file**, desc, total_memory, used_memory, total_cpu, used_cpu, total_disk, used_disk, rbd_version, health_status, resource_proxy_status, create_time, enterprise_alias

## 校准结果（8000 vs 7070，eid=b16bf28d..., rid=beb16025...）
```
字段顺序一致: True
DIFF: MATCH ✓  字段数=28
```
注：响应含 ssl 证书明文 —— 与 rainbond 一致(parity)，不额外脱敏。
