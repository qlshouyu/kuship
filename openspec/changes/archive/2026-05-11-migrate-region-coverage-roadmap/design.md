# Region API 覆盖度补齐路线（migrate-region-coverage-roadmap）

## 现状 — 全景表

rainbond `www/apiclient/regionapi.py` 共 ~353 个 method（按 unique 名）。kuship-console 已实现约 **70 个**，剩余 **~153 个** 待补，分布于 18 个业务域。

| #  | 业务域                          | rainbond | kuship | 缺口 | 优先级 | UI 影响          |
|----|---------------------------------|----------|--------|------|--------|------------------|
| 01 | 应用生命周期 / 部署             | 10       | 10     | 0    | ✅     | 已通             |
| 02 | 服务 CRUD / 构建（基础）        | 7        | 7      | 0    | ✅     | 已通             |
| 03 | Helm Chart 全套                 | 14       | 14     | 0    | ✅     | 已通             |
| 04 | 插件系统                        | 18       | 18     | 0    | ✅     | 已通             |
| 05 | Pod 状态 / 日志 / 事件          | 12       | 12     | 0    | ✅     | 已通             |
| 06 | Tenant CRUD                     | 5        | 5      | 0    | ✅     | 已通             |
| 07 | Cluster 基础                    | 13       | 8      | **5** | P0    | 部分             |
| 08 | Volume                          | 9        | 3      | **6** | P0    | 加卷弹窗空       |
| 09 | Service Dependency 补齐         | 5        | 2      | **3** | P0    | 卷依赖未走       |
| 10 | HTTP/TCP 域名 + 网关路由        | 30       | 1      | **29**| P0    | 网关页整页空     |
| 11 | 证书管理                        | 5        | 0      | **5** | P0    | 证书页整页空     |
| 12 | 集群节点（nodes/labels/taints） | 12       | 0      | **12**| P0    | 集群资源页空     |
| 13 | 资源中心（workload/pod 详情）   | 10       | 0      | **10**| P0    | 跳详情即空       |
| 14 | 第三方组件运行时                | 6        | 0      | **6** | P0    | 第三方运行时空   |
| 15 | KubeBlocks 数据库托管           | 13       | 0      | **13**| P1    | 全 stub          |
| 16 | App 分享流程                    | 7        | 0      | **7** | P1    | 分享按钮卡死     |
| 17 | Monitor 指标 + 资源中心事件     | 10       | 4      | **6** | P1    | 部分图表空       |
| 18 | Service 构建版本 / 部署版本     | 17       | 2      | **15**| P1    | 构建历史页空     |
| 19 | API Gateway 灰度规则去 stub     | 4        | 1      | **3** | P1    | 灰度规则不真实   |
| 20 | App 导入 / 导出 / 迁移          | 22       | 0      | **22**| P2    | 导入导出不可用   |
| 21 | 治理模式 / 限流 / 认证授权      | 12       | 0      | **12**| P2    | 治理菜单不可用   |
| 22 | Maven Setting / 多语言版本      | 8        | 0      | **8** | P2    | 自定义构建受限   |
| 23 | 服务标签 / 节点亲和             | 4        | 0      | **4** | P2    | 调度配置不可用   |
| 24 | Backup 列表 / 状态 / 迁移补齐   | 5        | 4      | **1** | P2    | 部分备份页空     |
| —  | Service Env region 调用         | 3        | 0      | 3    | skip   | rainbond 历史本地为主 |

## 决策 1 — 不打成"一个超大 change"

153 个 method 横跨 18 个业务域，每个域有自己的：
- region API URL 前缀（如 `/v2/gateway/*`、`/v2/cluster/*`、`/v2/kubeblocks/*`、`/v2/app/*`）
- 共享 entity（部分是 `service_domain` / `service_tcp_domain` 已新增、部分需新建如 `Maven`、`ServiceMonitor`）
- 状态机（如 share record 6-step / app upgrade parent_id 链 / import dir 生命周期）
- Controller 路径前缀（`/console/teams/{team_name}/domain/*`、`/console/teams/{team_name}/regions/{region_name}/kubeblocks/*` 等）

强行打包：
- proposal 会变成 8000+ 字
- design 决策互相纠缠，评审无从下手
- 无法分阶段验证（一次部署 153 个 method 同时回归）
- 违反 OpenSpec "每 change 聚焦一个 capability" 的核心原则

**结论**：本 change 仅作"路线母提案"，落 18 个独立子 change 的命名、范围、依赖。

## 决策 2 — 子 change 切分原则

每个子 change 必须满足：

1. **覆盖一个 region API URL 前缀的子集**（避免同一前缀被多个 change 抢占）
2. **≤ 30 个 method 上限**（保证可在 1-2 周内闭合）
3. **自治**：可独立部署、独立回归测试、不依赖其他 backlog 子 change 完成（除文档化的硬依赖）
4. **遵循既定模式**：
   - 14 接口骨架内的 method 走 `infrastructure/region/api/<X>Operations.java` 加 default 占位 + 业务模块下 `<X>OperationsImpl @Primary` 实现
   - 14 接口外的新接口（如 `KubeBlocksOperations` / `GatewayDomainOperations`）放在业务模块下 `modules/<domain>/api/`，与 rainbond `regionapi.py` 的语义边界对齐
   - controller 路径与 rainbond `console/urls/__init__.py` 严格一致（snake_case 路径变量、trailing slash 兼容）
   - 响应包装由全局 `GeneralMessageResponseBodyAdvice` 自动完成，业务层 `return Map`/`Page`/POJO 即可
   - region 错误消息走 `RegionErrorMsgEnricher` 兜底，不在业务层硬编码中文

## 决策 3 — 18 个子 change 范围卡片

### P0（用户每天用，目前 broken；共 8 个，~76 method）

#### `migrate-console-cluster-extras` (≈5 method)
- **rainbond 锚点**：`regionapi.py:get_cluster_nodes_arch`、`get_cluster_resource`、`get_region_info`、`get_region_alerts`、`watch_operator_managed`
- **Operations 接口**：扩 `ClusterOperations`（+5 method：getClusterInfo / getClusterEvents / getNodes / getNodeDetail / getResources）
- **Controller**：`EnterpriseRegionsController` 已有路由占位待补；新增 `ClusterEventsController`
- **本地 entity**：无新增
- **依赖**：无
- **价值**：补齐 ClusterOperations 接口里既有的 5 个 default unsupported method，让既有 controller 路由不再 throws

#### `migrate-console-gateway-domain` (≈29 method, 拆 2 章) — **路线起点**
- **rainbond 锚点**：`regionapi.py:bind_http_domain` / `unbind` / `update_http_domain` / `delete_http_domain` / `bindDomain` / `unbindDomain` / `add_gateway_http_route` / `update_gateway_http_route` / `delete_gateway_http_route` / `list_gateway_http_route` / `api_gateway_bind_http_domain` / `api_gateway_bind_http_domain_convert` / `api_gateway_bind_tcp_domain` / `api_gateway_get_proxy` / `api_gateway_post_proxy` / `api_gateway_put_proxy` / `api_gateway_delete_proxy` / `list_gateways` / `get_api_gateway` / `get_query_domain_access` / `get_query_service_access` 等
- **Operations 接口**：新增 `GatewayDomainOperations`（HTTP rule 8 method）+ `GatewayTcpRuleOperations`（TCP rule 5 method）+ 扩 `GatewayOperations`（apiGatewayProxy 已有）
- **Controller**：新增 `GatewayDomainController` (`/console/teams/{team_name}/domain/*`)、`GatewayTcpRuleController` (`/console/teams/{team_name}/tcp/rule/*`)、`GatewayHttpRouteController` (`/console/teams/{team_name}/regions/{region_name}/http-routes/*`)
- **本地 entity**：复用前两轮已新增的 `ServiceDomain` / `ServiceTcpDomain`；新增 `GatewayCustomConfigure`（`gateway_custom_configure` 表，HTTP rule 高级参数 K-V）
- **依赖**：无（与 cluster-extras 无序）
- **价值**：UI 网关 / 路由页整页空，是迁移 kuship-ui 之后用户第一个反馈的痛点

#### `migrate-console-gateway-certificate` (≈5 method)
- **rainbond 锚点**：`regionapi.py:get_gateway_certificate` / `create_gateway_certificate` / `update_gateway_certificate` / `delete_gateway_certificate` / `update_ingresses_by_certificate`
- **Operations 接口**：扩 `GatewayOperations`（+5 method，已 default 占位）
- **Controller**：新增 `CertificateController` (`/console/teams/{team_name}/certificates`)
- **本地 entity**：新增 `ServiceTlsCertificate`（`service_tls_certificate` 表）
- **依赖**：可与 gateway-domain 串联（建议先 domain 再 certificate；前者出现"绑域名时引用证书"场景）

#### `migrate-console-cluster-nodes` (≈12 method)
- **rainbond 锚点**：`regionapi.py:get_cluster_nodes` / `get_node_info` / `get_node_labels` / `update_node_labels` / `get_node_taints` / `update_node_taints` / `operate_node_action` / `get_resource_center_*`（节点维度）/ `manage_cluster_status`
- **Operations 接口**：新增 `ClusterNodeOperations`（12 method）
- **Controller**：新增 `ClusterNodeController` (`/console/enterprise/{eid}/regions/{rid}/nodes*`)
- **本地 entity**：无（节点状态全部走 region 实时）
- **依赖**：无

#### `migrate-console-resource-center` (≈10 method)
- **rainbond 锚点**：`regionapi.py:get_resource_center_pod_detail` / `get_resource_center_pod_log` / `get_resource_center_workload_detail` / `get_resource_center_events` / `get_pod` / `get_pod_volume` / `get_container_disk` / `get_services_pods`
- **Operations 接口**：新增 `ResourceCenterOperations`（10 method）
- **Controller**：新增 `ResourceCenterController`（资源中心相关路径）
- **本地 entity**：无
- **依赖**：无

#### `migrate-console-volume-extras` (≈6 method)
- **rainbond 锚点**：`regionapi.py:get_volume_options` / `get_service_volumes` / `get_service_volumes_status` / `get_service_dep_volumes` / `add_service_dep_volumes` / `delete_service_dep_volumes`
- **Operations 接口**：扩 `ServiceVolumeOperations`（+6 method，已 default 占位）
- **Controller**：补 `AppVolumeController` 既有路由的 stub 实现
- **本地 entity**：无
- **依赖**：无

#### `migrate-console-dependency-extras` (≈3 method)
- **rainbond 锚点**：`regionapi.py:add_service_dependencys`（批量）/ `add_service_volume_dependency` / `delete_service_volume_dependency`
- **Operations 接口**：扩 `ServiceDependencyOperations`（+3 method）
- **Controller**：补 `AppDependencyController` 中现有 stub
- **本地 entity**：复用 `TenantServiceRelation`
- **依赖**：无

#### `migrate-console-third-party-runtime` (≈6 method)
- **rainbond 锚点**：`regionapi.py:get_third_party_service_pods` / `get_third_party_service_health` / `post_third_party_service_endpoints` / `put_third_party_service_endpoints` / `put_third_party_service_health` / `delete_third_party_service_endpoints`
- **Operations 接口**：新增 `ThirdPartyServiceOperations`（6 method）
- **Controller**：新增 `ThirdPartyEndpointController` / `ThirdPartyHealthController`
- **本地 entity**：复用 `ThirdPartyServiceEndpoints`（`third_party_service_endpoints` 表，需要新建 entity 映射）
- **依赖**：无（appcreate 中的 third_party 创建已实现）

### P1（高频但有降级；共 5 个，~44 method）

#### `migrate-console-kubeblocks` (≈13 method)
- **rainbond 锚点**：`regionapi.py:get_kubeblocks_*`（8 method）/ `create_kubeblocks_cluster` / `expansion_kubeblocks_cluster` / `delete_kubeblocks_cluster` / `delete_kubeblocks_backups` / `update_kubeblocks_backup_config` / `update_kubeblocks_cluster_parameters` / `kubeblocks_cluster_pod_detail` / `create_kubeblocks_manual_backup`
- **Operations 接口**：新增 `KubeBlocksOperations`（13 method）
- **Controller**：去 `KubeBlocksController` 8 个 stub，注入真实 region 调用
- **本地 entity**：无
- **依赖**：无

#### `migrate-console-app-share` (≈7 method)
- **rainbond 锚点**：`regionapi.py:share_clound_service` / `share_service` / `share_service_result` / `share_plugin` / `share_plugin_result` / `get_service_publish_status` / `list_app_releases`
- **Operations 接口**：扩 `HelmOperations` 之外新增 `ShareOperations`（7 method）
- **Controller**：补 `ServiceShareController` / `PluginShareController` 现有 6-step 状态机内部的 region 调用
- **本地 entity**：复用 `ServiceShareRecord` / `ServiceShareRecordEvent` / `TenantPluginShare` / `PluginShareRecordEvent`
- **依赖**：无（状态机骨架已存在）

#### `migrate-console-monitor-extras` (≈6 method)
- **rainbond 锚点**：`regionapi.py:get_monitor_metrics` / `get_resource_center_events`（监控视角）/ `get_query_data` / `get_query_range_data` / `get_query_domain_access` / `get_query_service_access`（部分已实现）
- **Operations 接口**：扩 `MonitorOperations`（+2 method）+ 把 domain/service access 监控并入
- **Controller**：补 `AppMonitorController` 现有 stub
- **本地 entity**：复用 `ServiceMonitor`（`service_monitor` 表，需要新建 entity）
- **依赖**：无

#### `migrate-console-build-versions` (≈15 method)
- **rainbond 锚点**：`regionapi.py:get_service_build_versions` / `get_service_build_version_by_id` / `update_service_build_version_by_id` / `delete_service_build_version` / `get_service_deploy_version` / `get_team_services_deploy_version` / `get_service_check_info` / `service_source_check` / `get_build_status` / `get_lang_version` / `create_lang_version` / `update_lang_version` / `delete_lang_version` / `get_cnb_frameworks` / `batch_operation_service`
- **Operations 接口**：扩 `ServiceOperations`（+5 method 关于版本）+ 新增 `LangVersionOperations`（5 method）+ `BatchServiceOperations`（1 method）
- **Controller**：新增 `AppVersionsController` (`/console/teams/{team_name}/apps/{service_alias}/build-versions`) / `LangVersionController` / 补 `BatchActionsController`
- **本地 entity**：新增 `ServiceBuildVersion`（`service_build_version` 表）/ `LangVersion`（`lang_version` 表）
- **依赖**：无

#### `migrate-console-grayrelease-finalize` (≈3 method)
- **rainbond 锚点**：`regionapi.py:create_app_gray_release` / `update_app_gray_release` / `operate_app_gray_release` / `get_app_gray_release`
- **Operations 接口**：新增 `GrayReleaseOperations`（3 method，区别于已有 `ApisixRouteWeightUpdater`）
- **Controller**：去 `GrayReleaseTemplateInstaller` 的 stub，对接 `add-gray-release` change 留下的 hardening 链路（template 实例化 + 真实 service group 创建）
- **本地 entity**：复用 `GrayReleaseRecord`
- **依赖**：建议在 `migrate-console-app-install` 之前；本子 change 只对接 region API，template 实例化的本地写仍走 stub 直至 install 子 change 落地

### P2（低频 / 边缘；共 5 个，~50 method）

#### `migrate-console-app-import-export` (≈22 method)
- **rainbond 锚点**：`regionapi.py:import_app` / `import_app_` / `export_app` / `get_files` / `get_app_export_status` / `get_app_import_status` / `get_apps_migrate_status` / `get_enterprise_app_import_status` / `star_apps_migrate_task` / `batch_update_service_app_id` / `get_tar_load_result` / `load_tar_image` / `resource_import` / `yaml_resource_*`（4 method）/ `list_convert_resource` / `parse_app_services` / `create_import_file_dir` / `get_import_file_dir` / `delete_import_file_dir` / `delete_enterprise_import` / `delete_enterprise_import_file_dir` / `get_enterprise_import_file_dir`
- **Operations 接口**：新增 `AppImportOperations`（12 method）+ `AppExportOperations`（4 method）+ `YamlResourceOperations`（4 method）+ `MigrateOperations`（2 method）
- **Controller**：新增 `AppImportController` / `AppExportController` / `YamlResourceController` / `AppMigrateController`
- **本地 entity**：新增 `AppExportRecord` / `AppImportRecord`（rainbond `app_export_record` / `app_import_record` 表）
- **依赖**：无（功能独立，但工作量最大；建议放在 P0/P1 全清后）

#### `migrate-console-governance-policy` (≈12 method)
- **rainbond 锚点**：`regionapi.py:create_governance_mode_cr` / `delete_governance_mode_cr` / `update_governance_mode_cr` / `list_governance_mode` / `check_app_governance_mode` / `app_authorization_policy` / `app_peer_authentications` / `get_component_authorization_policy` / `get_app_authorization_policy` / `get_app_peer_authentications` / `create_http_limiting_policy` / `delete_http_limiting_policy` / `update_http_limiting_policy`
- **Operations 接口**：新增 `GovernanceOperations`（5 method）+ `AuthPolicyOperations`（4 method）+ `LimitingPolicyOperations`（3 method）
- **Controller**：新增 `GovernanceModeController` / `AuthPolicyController` / `LimitingPolicyController`
- **本地 entity**：可能需新增 `AuthorizationPolicy` / `PeerAuthentication`（需 schema 真相校验）
- **依赖**：无

#### `migrate-console-maven-setting` (≈8 method)
- **rainbond 锚点**：`regionapi.py:get_maven_setting` / `list_maven_settings` / `add_maven_setting` / `update_maven_setting` / `delete_maven_setting` / `get_lang_version`（多语言相关重叠 `build-versions`）/ `get_protocols`
- **Operations 接口**：新增 `MavenSettingOperations`（5 method）
- **Controller**：新增 `MavenSettingController` (`/console/enterprise/{eid}/maven-setting/*`)
- **本地 entity**：复用 region 端 schema（kuship 端可能不需本地表，全 region 透传）
- **依赖**：与 `build-versions` 在多语言版本上有少量重叠，建议同窗口推进或合并

#### `migrate-console-service-labels` (≈4 method)
- **rainbond 锚点**：`regionapi.py:addServiceNodeLabel` / `deleteServiceNodeLabel` / `add_service_state_label` / `update_service_state_label`
- **Operations 接口**：扩 `ServiceOperations`（+4 method）
- **Controller**：新增 `ServiceLabelController` (`/console/teams/{team_name}/apps/{service_alias}/labels/*`)
- **本地 entity**：复用 `TenantServiceLabel`（`tenant_service_label` 表）
- **依赖**：依赖 `cluster-nodes` 完成（先有节点列表才能挑节点贴标签）

#### `migrate-console-backup-extras` (≈5 method)
- **rainbond 锚点**：`regionapi.py:get_backup_status_by_group_id` / `delete_backup_by_backup_id` / `backup_group_apps` / `copy_backup_data` / `restore_cluster_from_backup`
- **Operations 接口**：扩 `BackupOperations`（+5 method）
- **Controller**：补 `EnterpriseBackupController` / `GroupCopyMigrateController` 现有 stub
- **本地 entity**：复用 `ServiceGroupBackup`
- **依赖**：无

## 决策 4 — 共享规约（每个子 change 落地必须遵守）

### 命名

- 子 change 名：`migrate-console-<area>` 全小写连字符
- Operations 接口：`<Area>Operations.java`，包路径 `infrastructure/region/api/`（核心接口）或 `modules/<domain>/api/`（业务自治接口）
- Impl 类：`<Area>OperationsImpl.java` `@Primary @Service`，与接口同包 `modules/<domain>/api/`
- DefaultImpl：仅当接口允许全 default 占位时新建（推荐避免，直接接口加 default 即可）

### Region URL 前缀分配

| 业务域                | URL 前缀                                              |
|-----------------------|-------------------------------------------------------|
| gateway-domain        | `/v2/tenants/{name}/http-rule/*`、`/api-gateway/v1/*`  |
| gateway-certificate   | `/v2/tenants/{name}/certificate/*`                     |
| cluster-nodes         | `/v2/cluster/nodes*`                                   |
| resource-center       | `/v2/cluster/resources*`、`/v2/tenants/{name}/pods*`   |
| kubeblocks            | `/v2/kubeblocks/*`                                     |
| app-share             | `/v2/cloud-service/*`、`/v2/tenants/{name}/share/*`    |
| import-export         | `/v2/app/import/*`、`/v2/app/export/*`、`/v2/yaml-resource-*` |
| governance-policy     | `/v2/governance-mode/*`、`/v2/authorization-policy/*`  |
| maven-setting         | `/v2/maven-setting/*`                                  |

每个子 change 必须在 design.md 标注完整 URL 表。

### Controller 路径回归

每个 controller 必须在 design.md 列出 rainbond `console/urls/__init__.py` 中对应行号锚点；trailing slash 兼容仍按 `migrate-console-response-contract` 既定规则（每 endpoint 同时声明 `path` 与 `path/`）。

### 错误处理

region 异常一律抛 `RegionApiException` 子类，由 `GlobalExceptionHandler` 自动映射。错误消息汉化优先用 region `msg_show` 直传，缺失才走 `RegionErrorMsgEnricher` 兜底。**不在业务层 controller / service 内硬编码中文 msg_show**。

### 测试

- 集成测试：`@SpringBootTest + @ActiveProfiles({"local","contract-test"})`，挂 `@MockitoBean` 替换 Operations Impl
- 契约形状断言：`code/msg/msg_show/data.bean/data.list` 五项必须断言
- region 异常路径断言：至少 1 个 `RegionApiException` 透传案例

## 决策 5 — 推进顺序

```
P0 ──────────────────────────────────────────────────────────────► time
│
├─ #1 cluster-extras ─────► gateway-domain ─────► gateway-certificate
│                          (起点 / 工作量大)         (与 domain 联动)
│
├─ 并行可起：cluster-nodes ────► resource-center ────► volume-extras
│                                                       dependency-extras
│                                                       third-party-runtime

P1 ──────────────────────────────────────────────────────────────►
│
├─ kubeblocks（独立）
├─ app-share（与 helm 已有体系挂钩）
├─ monitor-extras
├─ build-versions ─► maven-setting（多语言重叠）
└─ grayrelease-finalize（等 app-install 子 change 配合）

P2 ──────────────────────────────────────────────────────────────►
│
├─ app-import-export（最大）
├─ governance-policy
├─ service-labels（依赖 cluster-nodes）
└─ backup-extras
```

## 非决策（明确不做）

- **不打包**：本路线图不"借机"做 region client 重构 / mTLS 优化 / 全局响应包装变更，那些是独立 hardening。
- **不替换** rainbond 现有 14 接口骨架命名（保留 14 接口的好处：业务方接口注入路径稳定，已迁移代码不回填）。
- **不动** Service Env region 调用（rainbond 历史选择本地为主 + 重启同步，沿用）。
- **不预先生成** 18 个子 change 的目录结构 —— 各子 change 在被认领时按 OpenSpec 标准流程自己 propose / design / tasks / specs。
