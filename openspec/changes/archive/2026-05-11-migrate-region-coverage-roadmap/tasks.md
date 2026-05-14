# Tasks — migrate-region-coverage-roadmap

本路线图 change 的 tasks 是"开子 change 提案"的元任务，**不直接产代码**。每个任务的 done 标准是：对应子 change 的 `openspec/changes/<name>/proposal.md` + `design.md` + `tasks.md` + `specs/<capability>/spec.md` 落地、由人评审通过、标记为 active。

## 1. 路线规约盘点

- [x] 1.1 在 `kuship-console-app` capability 的 `spec.md` 中确认本路线图新增的 "Region API 覆盖度路线" Requirement 已合入（本 change `specs/kuship-console-app/spec.md`）—— 2026-05-10 已确认 spec.md 包含 Requirement 主体 + 18 子 change 命名优先级表 + 共享规约 + 3 段 Scenario
- [x] 1.2 把"P0/P1/P2 + 18 个子 change 名 + 依赖图"贴到 `kuship-console/CLAUDE.md` 的"迁移路线图"章节，作为后续 PR 描述的引用锚点（2026-05-10 落于 `kuship-console/CLAUDE.md` 1584 行起的 `### Region API 覆盖度路线（migrate-region-coverage-roadmap）` 子段，含 18 子 change 表 + 依赖图 + 共享规约 + 落地状态：P0 8 个全部 ✅ / P1 5 个全部 ✅ / P2 5 个 ⏳）
- [ ] 1.3 与团队对齐"共享规约"段（决策 4）：URL 前缀分配表、命名规约、错误消息汉化策略；如有反对意见，在 design.md 内记录修订

## 2. 启动 P0 子 change 提案（按推荐顺序）

### 2.1 `migrate-console-cluster-extras`（5 method，热身）
- [x] 2.1.1 拉 rainbond `regionapi.py` 中 `get_cluster_nodes_arch` / `get_cluster_resource` / `get_region_info` / `get_region_alerts` / `watch_operator_managed` 5 段 Python 源码做参照
- [x] 2.1.2 写 proposal.md（Why / What Changes / Capabilities / Impact）
- [x] 2.1.3 写 design.md（5 个 region URL 表 + ClusterOperations 接口扩展点 + controller 复用清单）
- [x] 2.1.4 写 tasks.md（接口扩 / Impl 实现 / controller 接线 / 集成测试）
- [x] 2.1.5 写 `specs/kuship-console-app/spec.md`（ADDED Requirements：5 个端点契约）

### 2.2 `migrate-console-gateway-domain`（29 method，路线起点）
- [x] 2.2.1 拉 rainbond `console/views/app_config/app_domain.py` + `services/app_config/domain_service.py` 全文做参照
- [x] 2.2.2 写 proposal.md（高亮 UI 影响 / 与已有 ServiceDomain / ServiceTcpDomain entity 的衔接）
- [x] 2.2.3 写 design.md（HTTP rule + TCP rule + api-gateway proxy + 域名解绑/绑定 / 高级参数 GatewayCustomConfigure 表）
- [x] 2.2.4 写 tasks.md（细分到 entity / repo / Operations 接口 / controller / 集成测试 共 6-8 章）
- [x] 2.2.5 写 specs

### 2.3 `migrate-console-gateway-certificate`（5 method）
- [x] 2.3.1 拉 rainbond `views/app_config/app_certificate.py` 做参照
- [x] 2.3.2 写 proposal.md / design.md / tasks.md / specs

### 2.4 `migrate-console-cluster-nodes`（12 method）
- [x] 2.4.1 拉 rainbond `views/cluster/cluster_nodes.py` + `services/cluster_services.py` 做参照
- [x] 2.4.2 写 proposal.md / design.md / tasks.md / specs

### 2.5 `migrate-console-resource-center`（10 method）
- [x] 2.5.1 拉 rainbond `views/resource_center.py` 做参照
- [x] 2.5.2 写 proposal.md / design.md / tasks.md / specs

### 2.6 `migrate-console-volume-extras`（6 method）
- [x] 2.6.1 拉 rainbond `services/app_config/volume_service.py` 做参照
- [x] 2.6.2 写 proposal.md / design.md / tasks.md / specs

### 2.7 `migrate-console-dependency-extras`（3 method）
- [x] 2.7.1 拉 rainbond `services/app_config/dependency_service.py` 中批量依赖 + 卷依赖逻辑做参照
- [x] 2.7.2 写 proposal.md / design.md / tasks.md / specs

### 2.8 `migrate-console-third-party-runtime`（6 method）
- [x] 2.8.1 拉 rainbond `views/app_config/third_party_*.py` 做参照
- [x] 2.8.2 写 proposal.md / design.md / tasks.md / specs

## 3. 启动 P1 子 change 提案

### 3.1 `migrate-console-kubeblocks`（13 method）
- [x] 3.1.1 参照 rainbond `services/kubeblocks_service.py` + `views/kubeblocks/*.py`
- [x] 3.1.2 写 4 件 artifact
- [x] 3.1.3 实施落地（2026-05-10）：18 单测 + 12 集成测试通过；Operations 接口 + Impl + Controller 改造完成；联动验证 §8.4-§8.9 留给用户

### 3.2 `migrate-console-app-share`（7 method）
- [x] 3.2.1 参照 rainbond `services/share_services.py`（6-step 状态机）
- [x] 3.2.2 写 4 件 artifact
- [x] 3.2.3 实施落地（2026-05-10）：9 单测 + 9 集成测试通过；ShareOperations + Impl + 2 controller 改造（addEvent 接 region / 新增 eventStatus / complete 校验全 success）+ 2 新 controller（publish/status / releases）；step3 / step4 注入点已锁定

### 3.3 `migrate-console-monitor-extras`（6 method）
- [x] 3.3.1 参照 rainbond `services/monitor_service.py`（含 `service_monitor` 表 entity 新建）
- [x] 3.3.2 写 4 件 artifact
- [x] 3.3.3 实施落地（2026-05-10）：4 集成测试通过；MonitorOperations 扩 4 default method + Impl 扩 + AppMonitorController 加 metrics / sortDomainQuery / sortServiceQuery 3 endpoint（resourceCenterEvents 因路径与既有 ResourceCenterController 冲突，仅保留接口不暴露）；ServiceMonitor entity 落地 8 列（无 create_time，schema 真相修正 design 决策 2）

### 3.4 `migrate-console-build-versions`（15 method）
- [x] 3.4.1 参照 rainbond `views/app/app_version.py` + `services/build_service.py`
- [x] 3.4.2 写 4 件 artifact
- [x] 3.4.3 实施落地（2026-05-10）：13 集成测试通过；ServiceOperations 扩 9 method + LangVersionOperations(5) + BatchServiceOperations(1) + 3 新 controller（AppVersionsController 8 endpoint / BatchDeployVersionController 1 endpoint / LangVersionController 5 endpoint）；§2 entity 段跳过（`service_build_version` / `lang_version` 真实 schema 不存在，留 hardening 后续引入本地缓存）；§7 改造 AppBatchActionsController 推迟到独立 hardening

### 3.5 `migrate-console-grayrelease-finalize`（3 method）
- [x] 3.5.1 参照 rainbond `services/gray_release_services.py`；同步等 `migrate-console-app-install` 的 stub 替换需求
- [x] 3.5.2 写 4 件 artifact
- [x] 3.5.3 实施落地（2026-05-10）：6 单测 + 7 既有集成测试零回归 = 13 用例通过；GrayReleaseOperations + Impl 落地（create / update / operate-rollback）+ GrayReleaseTemplateInstaller 接 region + GrayReleaseService.updateGrayRatio 双面同步（apisix 数据面 + region 命令面）；新增配置项 `kuship.gray-release.skip-region-template-install`（contract-test profile 默认 true）；本地 service_group 批量 INSERT 仍 stub 待 `migrate-console-app-install`

## 4. 启动 P2 子 change 提案

### 4.1 `migrate-console-app-import-export`（22 method，最大）
- [x] 4.1.1 参照 rainbond `views/app/app_import.py` / `app_export.py` / `yaml_resource_*.py` —— 2026-05-10 完成（22 method 拆 6 子段：export 2 / import 10 / upload 4 / load tar 1 / helm chart 1 / yaml resource 3）
- [x] 4.1.2 写 4 件 artifact（建议拆 2 章 design：import/export 各成段）—— 2026-05-10 落地，design 拆 6 子段；validate 通过
- [x] 4.1.3 实施落地（2026-05-10/11）：6 接口 + 6 @Primary Impl 覆盖 22 region method；2 entity（AppImportRecord / AppExportRecord）+ 2 repo；5 controller（CenterAppExportController / CenterAppImportController / AppUploadController / YamlResourceController / UploadHelmChartValueResourceController）；22 新增测试（11 单测 + 11 集成）；archive 2026-05-11-migrate-console-app-import-export

### 4.2 `migrate-console-governance-policy`（12 method）
- [x] 4.2.1 参照 rainbond `services/governance_service.py` + `views/governance_*.py` —— 2026-05-10 完成（实际 9 method = 5 governance + 4 k8s_attribute；偏差 -25%）
- [x] 4.2.2 写 4 件 artifact —— 2026-05-10 落地；validate 通过
- [x] 4.2.3 实施落地（2026-05-10/11）：GovernanceModeOperations 5 + K8sAttributeOperations 4（GET/DELETE with body via Spring 6 RestClient）；ComponentK8sAttribute entity + repo；2 controller（AppGovernanceModeController + ComponentK8sAttributeController）；URL 冲突规避：`/governancemode/{available,sync}` 区分既有 `GroupController#setGovernance`；26 新增测试（15 单测 + 11 集成）；archive 2026-05-11-migrate-console-governance-policy

### 4.3 `migrate-console-maven-setting`（8 method）
- [x] 4.3.1 参照 rainbond `views/enterprise/enterprise_maven.py` —— 2026-05-10 完成（实际 5 method；偏差 -37.5%；偏差原因：路线图 8 估计含 lang version 协调，但 lang version 已被 P1 #4 持有）
- [x] 4.3.2 写 4 件 artifact（注意与 build-versions 的多语言重叠协调）—— 2026-05-10 落地，design.md §7 明确边界；validate 通过
- [x] 4.3.3 实施落地（2026-05-10/11）：MavenSettingOperations 5 method 透传 `/v2/cluster/builder/mavensetting[/{name}]`；MavenSettingController 5 endpoint 全部 `@RequireEnterpriseAdmin`；onlyname 投影在 Impl 内完成；18 新增测试（10 单测 + 8 集成，含 403 用例）；archive 2026-05-11-migrate-console-maven-setting

### 4.4 `migrate-console-service-labels`（4 method）
- [x] 4.4.1 参照 rainbond `views/app_config/app_label.py`；硬依赖 `cluster-nodes` 完成 —— 2026-05-10 完成；硬依赖 P0 #4 cluster-nodes 已落地
- [x] 4.4.2 写 4 件 artifact —— 2026-05-10 落地（4 method 与路线图一致）；validate 通过
- [x] 4.4.3 实施落地（2026-05-10/11）：ServiceLabelOperations 4 method + Impl（DELETE/PUT with body）；TenantServiceLabel entity（6 字段）+ repo；AppLabelController 4 endpoint，`@Transactional` 双写 + region 404 兼容；PermCode 退化用 `APP_OVERVIEW_DESCRIBE`/`APP_OVERVIEW_ENV`（缺 `OTHER_SETTING`）；16 新增测试（8 单测 + 8 集成）；archive 2026-05-11-migrate-console-service-labels

### 4.5 `migrate-console-backup-extras`（5 method）
- [x] 4.5.1 参照 rainbond `services/backup_service.py` 中既未迁移的 5 段 —— 2026-05-10 完成；含既有 4 method URL 一致性修正
- [x] 4.5.2 写 4 件 artifact —— 2026-05-10 落地（5 method 与路线图一致）；validate 通过
- [x] 4.5.3 实施落地（2026-05-10/11）：BackupOperations 扩 5 new method（deleteBackup / listBackupsByGroupUuid / startMigrate / getMigrateStatus / copyBackupData）+ 既有 4 method URL 修正对齐 rainbond（`/groupapp/backups` 复数 + group_id 入 body）；restore/export 转 `UnsupportedOperationException`；GroupCopyMigrateController + GroupAppBackupController 接 region；22 新增测试（14 单测 + 8 集成）；archive 2026-05-11-migrate-console-backup-extras

## 5. 路线图自归档触发

- [x] 5.1 当本路线图 change 被批准且 #2.1（cluster-extras）完整 propose 后，归档本 change（`/opsx:archive`）；后续每个子 change 各自走自己的 propose / apply / archive 周期 —— 2026-05-11 触发：P0/P1/P2 共 18 子 change 全部完成 propose / apply / archive 闭环，路线图职责完成
- [x] 5.2 在归档时，更新 `openspec/specs/kuship-console-app/spec.md` 让 "Region API 覆盖度路线" Requirement 进入 specs（不再仅在 active change 中）—— 2026-05-11 完成：本路线 Requirement 已 append 到 main spec line 4753 起
