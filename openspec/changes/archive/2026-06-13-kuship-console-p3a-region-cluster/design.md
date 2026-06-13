## Context
region 后端 Go 服务在集群内（rbd-api-api:8443，mTLS）。kuship-console 开发期跑在 host，经 all-in-one 映射的 localhost:8443 + region_info 证书 mTLS 可达（已验证 /v2/cluster 返回 cap_mem=7936/cap_cpu=10/all_node=1）。activity 指标(req_*/pods/node_ready)随时刻变→不可 byte-diff。BouncyCastle(bcpkix 1.78.1)已在依赖。

## Goals / Non-Goals
**Goals:** RegionClient mTLS 底座（PKCS1 key via BC）+ url-override + regions check_status=yes 富化；稳定字段 parity 校准。
**Non-Goals:** region 写/provision、应用/组件 region 操作、check_status="" 路径(P2-e 不变)。

## Decisions
### 决策 1：RegionClient mTLS——BC PEMParser 解析 ca/cert/key(PKCS1 PEMKeyPair→JcaPEMKeyConverter)，client 证书+私钥入 KeyStore→KeyManagerFactory；sslVerify=false→trust-all TrustManager + NoopHostnameVerifier(CN≠localhost)；HC5 SSLConnectionSocketFactory + PoolingHttpClientConnectionManager。按 baseUrl 缓存 client。
### 决策 2：url-override(RegionProperties)——非空优先于 region_info.url(集群内 DNS host 不可达)；env REGION_URL_OVERRIDE，prod 留空。
### 决策 3：富化对齐 conver_region_info——/v2/cluster bean(cap_*→total/req_*→used/cap_disk÷1G→total_disk/resource_proxy_status/k8s_version/all_node→all_nodes/run_pod_number→services_status.running+pods+node_ready)、/v2/show→rbd_version(纯文本 trim)、/v2/cluster/nodes→arch(distinct architecture)；异常→rbd_version=""/health_status=failure。
### 决策 4：验证=稳定字段 parity——容量/版本/架构/all_nodes/resource_proxy_status 与 7070 一致；volatile(used_*/run_pod_number/node_ready/services_status/pods)live 不断言。pods({}vs null)为瞬时 pod 状态差异。

## Risks / Trade-offs
- **[活体不可 byte-diff]** 验证转稳定字段 parity(文档化)；这是 region 域固有，非缺陷。
- **[host 不可达集群 DNS]** url-override 解决(dev)；prod 集群内直达。
- **[mTLS/PKCS1]** BC 解析；sslVerify=false trust-all + noop hostname(对齐 rainbond REGION_SSL_VERIFY=false)。
- **[total_disk float]** cap_disk÷1024³ double，Jackson 与 Python json repr 一致(实测匹配)。

## Migration Plan
无 schema 变更。回滚移除富化与 mTLS（RegionClient 回骨架）。dev 联调需 REGION_URL_OVERRIDE=https://localhost:8443。校准：check_status=yes 稳定字段 8000 vs 7070 一致；check_status="" 回归 P2-e 纯 DB。

## Open Questions
- pods({}/null)瞬时差异——属 live pod 状态，不影响稳定字段；已归入 volatile。
