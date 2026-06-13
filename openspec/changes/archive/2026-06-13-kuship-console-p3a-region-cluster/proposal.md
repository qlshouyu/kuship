## Why
region 专题首刀：打通 kuship→region Go 后端的双向 TLS 调用底座，并落地首个 region 后端依赖接口——企业集群列表 `check_status=yes` 实时资源/版本/节点富化。region 活体指标随时刻变，验证策略由"byte-deep-diff"转为"结构正确 + 稳定字段(容量/版本/架构)与 7070 一致"。

## What Changes
- **RegionClient 双向 TLS 底座**（生产关键基础）：由 region_info 的 PEM（ssl_ca_cert/cert_file/key_file，client key 为 PKCS1）经 BouncyCastle 构建 SSLContext（客户端证书+私钥 KeyStore；sslVerify=false 时 trust-all + Noop hostname），HttpClient 接入；`url-override` 配置（开发环境 region_info.url 为集群内 DNS `rbd-api-api:8443` 不可达时覆盖为 `https://localhost:8443`）。
- **企业集群列表 check_status=yes 富化**（对齐 `conver_region_info`）：`GET /console/enterprise/{eid}/regions?check_status=yes` → 调 region `/v2/cluster`(资源)+`/v2/show`(rbd_version)+`/v2/cluster/nodes`(arch)，富化 total_*/used_*(=cap/req)/resource_proxy_status/k8s_version/all_nodes/run_pod_number/services_status/pods/node_ready/arch/rbd_version；异常→rbd_version=""/health_status="failure"。
- **不包含**：check_status="" 路径不变（P2-e safe 级，纯 DB，不触 region）；region 写/provision、其它 region 接口。

## Capabilities
### New Capabilities
- `region-cluster-read`: RegionClient 双向 TLS 底座 + 企业集群列表实时资源富化（region 后端依赖）。

## Impact
- 代码：RegionClient 实现 mTLS（BC PEM→SSLContext）+ url-override；RegionProperties.urlOverride；EnterpriseRegionReadService.listRegions 加 checkStatus 富化（注入 RegionClient）；application.yml 加 region.url-override。
- 数据：只读 region_info（连接信息/证书）+ 实时调 region 后端；无 schema 变更。
- 验证：**稳定字段 parity**（容量 total_*/all_nodes/k8s_version/rbd_version/arch/resource_proxy_status 与 7070 一致）；volatile（used_*/run_pod_number/node_ready/services_status/pods）live 值不参与逐字节断言。
- 运维：开发环境需 `REGION_URL_OVERRIDE=https://localhost:8443`（prod 集群内 region_info.url 直达，无需覆盖）。
