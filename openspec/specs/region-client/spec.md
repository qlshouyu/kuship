# region-client Specification

## Purpose

TBD - created by archiving change kuship-console-p0-contract-auth. Update Purpose after archive.

## Requirements

### Requirement: 单 region-api 的 RegionClient 传输骨架

系统 SHALL 提供基于 Apache HttpClient 5 的 `RegionClient` 传输骨架，地址与凭证来自 `region_info` 表（`url/wsurl/httpdomain/tcpdomain`）。认证优先级 MUST 为：企业级 Token → 区域级 token/环境变量 → 双向 TLS；`REGION_SSL_VERIFY` 默认 false。骨架 MUST 预留多 region 扩展点；本轮 MUST NOT 实现具体 `/v2/tenants/...` 业务域方法。兼容基线钉死 Rainbond v6.9.0-release（`reference/rainbond@44c5c34d`）。

#### Scenario: 从 region_info 解析连接信息

- **WHEN** 给定一个 region 名称
- **THEN** RegionClient 从 `region_info` 表取得其 `url` 与凭证（token 或 TLS 证书）

#### Scenario: 认证优先级

- **WHEN** 同时存在企业级 Token 与区域级 token
- **THEN** 优先使用企业级 Token

#### Scenario: 重试与超时梯度

- **WHEN** 发起一次 region 调用
- **THEN** 默认重试 2 次，按调用类型采用超时梯度（约 2s 轻查询 → 10/15s 常规 → 20s 构建 → 300s 导入/备份）

#### Scenario: 本轮不含业务域方法

- **WHEN** 检视 RegionClient 本轮交付
- **THEN** 仅含传输/认证/连接池骨架，不含具体租户/应用/组件等域方法
