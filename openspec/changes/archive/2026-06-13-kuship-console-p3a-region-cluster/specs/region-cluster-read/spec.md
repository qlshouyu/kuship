## ADDED Requirements

### Requirement: RegionClient 双向 TLS
系统 SHALL 以双向 TLS 调用 region Go 后端：从 `region_info` 的 `ssl_ca_cert`/`cert_file`/`key_file`（client key 为 PKCS1）构建客户端证书+私钥的 SSLContext（经 BouncyCastle 解析 PEM）；`sslVerify=false` 时信任服务端自签证书并禁用主机名校验。基地址 SHALL 支持配置覆盖（`url-override` 非空时优先于 `region_info.url`）。

#### Scenario: mTLS 成功调用 region 后端
- **WHEN** 配置了 region 客户端证书且 region 后端可达
- **THEN** RegionClient 能成功发起 `/v2/*` 请求并取回响应体

### Requirement: 企业集群列表实时资源富化
系统 SHALL 在 `GET /console/enterprise/{enterprise_id}/regions?check_status=yes` 时（对齐 `conver_region_info`）实时调用 region `/v2/cluster`、`/v2/show`、`/v2/cluster/nodes`，富化每个集群项：`total_memory/total_cpu/total_disk`（=容量 cap）、`used_memory/used_cpu/used_disk`（=请求 req）、`rbd_version`、`k8s_version`、`all_nodes`、`resource_proxy_status`、`run_pod_number`、`services_status`、`pods`、`node_ready`、`arch`；region 调用异常时 `rbd_version=""`、`health_status="failure"`。`check_status` 为空时不触 region（safe 级纯 DB）。

#### Scenario: check_status=yes 富化
- **WHEN** 带 `check_status=yes` 请求且 region 后端可达
- **THEN** 集群项含上述实时字段；容量/版本/架构等稳定字段与 7070 一致

#### Scenario: check_status 为空不触 region
- **WHEN** 不带 `check_status`（或非 yes）
- **THEN** 返回 safe 级（纯 DB、资源默认），不调用 region 后端
