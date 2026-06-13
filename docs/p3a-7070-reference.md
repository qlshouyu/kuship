# P3-a（region 集群读 + RegionClient mTLS 底座）7070 校准基线
## RegionClient 双向 TLS（生产关键基础）
- region_info 的 ssl_ca_cert/cert_file/key_file(client key=PKCS1)→BouncyCastle 解析→SSLContext(客户端证书+私钥 KeyStore)；sslVerify=false→trust-all + NoopHostnameVerifier(CN≠localhost)；HC5 SSLConnectionSocketFactory。
- url-override(RegionProperties，env REGION_URL_OVERRIDE)：dev host 无法解析集群内 rbd-api-api:8443→覆盖 https://localhost:8443(all-in-one 映射)；prod 留空直达。
- **已验证**：8000 经 mTLS 调 /v2/cluster /v2/show /v2/cluster/nodes 成功取真实数据。
## GET /console/enterprise/{eid}/regions?check_status=yes
- 对齐 conver_region_info：/v2/cluster(cap_mem→total_memory,req_mem→used_memory,cap_cpu→total_cpu,req_cpu→used_cpu,cap_disk÷1G→total_disk,req_disk÷1G→used_disk,resource_proxy_status,k8s_version,all_node→all_nodes,run_pod_number→services_status.running+pods+node_ready)+/v2/show→rbd_version(纯文本)+/v2/cluster/nodes→arch(distinct architecture)。异常→rbd_version=""/health_status=failure。
- **验证策略=稳定字段 parity**（活体指标不可 byte-diff）：稳定字段(total_memory=7936/total_cpu=10/total_disk/all_nodes=1/k8s_version=1.33.10+k3s1/rbd_version/resource_proxy_status/arch=['arm64'])8000 vs 7070 **全一致**；volatile(used_*/run_pod_number/node_ready/services_status)live 值仅验存在。
- pods({} vs null)：瞬时 pod 状态差异，归 volatile。
- check_status="" 回归 P2-e safe 级纯 DB(rbd_version=unknown)，不触 region。
## 运维
- dev：REGION_URL_OVERRIDE=https://localhost:8443 启动 kuship。prod：region_info.url 集群内直达。
