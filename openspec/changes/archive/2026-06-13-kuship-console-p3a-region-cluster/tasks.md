## 1. RegionClient mTLS 底座
- [x] 1.1 RegionProperties.urlOverride + application.yml region.url-override
- [x] 1.2 RegionClient：baseUrl(override 优先) + clientFor mTLS(BC PEM→SSLContext，trust-all+noop hostname)
## 2. 集群富化
- [x] 2.1 EnterpriseRegionReadService.listRegions(eid,status,checkStatus)：check_status=yes 调 /v2/cluster+/v2/show+/v2/cluster/nodes 富化
- [x] 2.2 controller check_status 参数
## 3. 校准
- [x] 3.1 mTLS 端到端：8000 check_status=yes 经 REGION_URL_OVERRIDE 取真实集群数据
- [x] 3.2 稳定字段 parity 8000 vs 7070（容量/版本/arch/all_nodes/resource_proxy_status 一致）；volatile 仅验存在
- [x] 3.3 check_status="" 回归 P2-e 纯 DB（不触 region）
- [x] 3.4 全量构建+单测 117/117
- [x] 3.5 docs + openspec 校验归档
