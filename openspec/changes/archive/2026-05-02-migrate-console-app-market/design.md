## Context

第 9 阶段把"应用市场 / 模板 / 分享 / 备份 / 升级 / Helm"这一类**批量化、跨组件、与远程市场打交道**的能力搬到 kuship-console。涉及参考代码：

| view | LOC | 主要 endpoint 数 | 子域 |
|------|-----|------------------|------|
| `center_pool/apps.py` | 533 | 9 | 应用模板 CRUD + Tag |
| `service_share.py` | 846 | 12 | 服务分享 + AppMarket 凭据 |
| `helm_app.py` | 242 | 9 | Helm 安装 + Repo |
| `app_upgrade.py` | 155 | 9 | 应用升级生命周期 |
| `app_version.py` | 83 | 6 | 单组件版本快照 + 回滚 |
| `app_market.py` | 23 | 1 | TenantImageTags |
| `backup_data.py` | 62 | 2 | 企业级备份导出/上传 |
| `center_pool/groupapp_backup.py` | 396 | 6 | 整组应用备份 + 状态 + 导入/导出 |
| `center_pool/groupapp_copy.py` | 85 | 1 | 整组应用复制 |
| `center_pool/groupapp_migration.py` | 215 | 2 | 整组应用迁移 |
| `center_pool/app_export.py` | 87 | 1 | 应用模板导出 |
| `center_pool/app_import.py` | 234 | 3 | 应用模板导入 + 状态 |
| **合计** | **2961** | **~50 endpoint** | **6 子域** |

绝大多数 endpoint 是 `JWTAuthApiView` 或 `RegionTenantHeaderView` 类型，少数（如 share record 异步事件）有非平凡状态机。

数据库 schema 真相（已通过 `DESC` 校验）：
- `rainbond_center_app`（19 列）— `app_id` UUID 32-char，含 `is_ingerit`（rainbond 历史拼写错，不要纠正）
- `rainbond_center_app_version`（25 列）— `version` + `app_id` 联合唯一，`app_template` longtext 存模板 YAML/JSON
- `rainbond_center_app_tag`（4 列）+ `rainbond_center_app_tag_relation`（4 列）—— Tag 表使用这两个，**不是** 单独 `tag_info`/`app_tag`
- `app_market`（远程模板源凭据）
- `service_share_record`（19 列）+ `service_share_record_event`（事件子表）—— 分享异步流程
- `app_upgrade_record`（17 列）+ `app_upgrade_snapshots`（快照子表）+ `service_upgrade_record`（组件级子表）
- `groupapp_backup`（备份元）+ `groupapp_backup_import`（导入记录）+ `tenant_service_backup`（组件级备份）
- `helm_repo`（Helm 仓库）+ `app_helm_overrides`（应用级 values 覆写）+ `team_helm_release_source`（团队级 release 反向索引）
- `rainbond_center_app_inherit`（应用模板继承关系）

## Goals / Non-Goals

**Goals:**
- 让 kuship-ui 的"应用市场"、"我的应用"、"分享/导入/导出"、"Helm 商店"、"备份与迁移" 5 个标签页完全可用，不再依赖 rainbond-console。
- 落地 10 张本地 schema 表的 JPA Entity（按 `DESC` 真相，不擅自加列；前一阶段 autoscaler `create_time` 教训记忆深刻）。
- 完整实现 `HelmOperations` 14 接口骨架的 6 method，并新增 `BackupOperations` 4 method。
- share / backup / upgrade 三类异步流程通过状态轮询暴露给前端，不引入 SSE/WebSocket。

**Non-Goals:**
- 不实现 RainbondPlugin 仓库管理（属于第 10 阶段 plugin 范畴）。
- 不实现 Helm 集群级安装（rke2 / helm install rainbond）—— 属于独立 change `enable-rke2-cluster`。
- 不重构 `service_share_record` 16 阶段状态机；保持 rainbond 原状态值兼容。
- 不实现 `cloud/markets/{name}/organizations` 远程组织树（极少使用，留作 hardening）。
- 不引入 helm-client SDK；commandInstall 全靠 region grctl 拉 chart。
- 不实现 KubeBlocks DB cluster 备份（kubeblocks 域属于 misc 阶段）。

## Decisions

### 决策 1：appmarket 模块按 6 子域细分

```
modules/appmarket/
├── market/      —— rainbond_center_app + version + tag + app_market 远程源
├── share/       —— service_share_record + service_share_record_event
├── upgrade/     —— app_upgrade_record + snapshots + service_upgrade_record
├── backup/      —— groupapp_backup + groupapp_backup_import + 整组 copy/migrate
├── helm/        —— helm_repo + helm_chart 安装 + values 覆写
├── version/     —— 单组件版本快照（app_version.py）
├── controller/  —— 顶层一些跨子域 controller（如 image_tags）
└── service/     —— 跨子域 service
```

每个子域独立 `entity/`、`repository/`、`service/`、`controller/`，避免单包文件爆炸。

### 决策 2：rainbond_center_app + tag 三张表 join 走 Repository @Query

应用模板列表、按 tag 过滤、按 scope（team/enterprise/market）过滤是热路径，但 `rainbond_center_app_tag_relation` 与 `rainbond_center_app` 不能直接 FK（rainbond 无外键约束）。统一走 `@Query("...")` JPQL 三表 join，不引入 QueryDSL（编译期负担过重）。复杂分页通过 `Page<T>` + JPQL `countQuery=` 显式声明。

### 决策 3：service_share_record 状态机透明保留

rainbond 的 share record 有 6 个 step（0=未开始 / 1=确认 / 2=生成模板 / 3=镜像同步 / 4=plugin / 5=完成 / 6=失败）和 3 个 status（0=running / 1=success / 2=failed）。kuship-console **不重新设计**，全部透明继承 + 复用 region API（`POST /v2/share/services` / `GET /v2/share/services/{event_id}`）。controller 层仅做：参数校验 → 调 region → 写 `service_share_record_event` 一行。

### 决策 4：app_upgrade_record 升级流程

升级 record 的 `record_type` 字段值：`upgrade` / `rollback` / `cmd-upgrade` / `cmd-rollback`。流程：
1. POST `/upgrade-records` 拿到 record_id（status=0 init）
2. POST `/upgrade-records/{id}/upgrade` 推送至 region；region 返回 event_id；console 把 event_id + status=1 落表
3. 前端轮询 GET `/upgrade-records/{id}` 直至 status ∈ {2,3} 完结
4. 失败时 POST `/upgrade-records/{id}/rollback` 触发回滚（生成新 record_type=rollback record，linked via parent_id）

console 不在异步循环中主动拉 region 状态——前端轮询足够，避免引入定时任务。

### 决策 5：HelmOperations 6 method 覆盖

骨架已声明 `HelmOperations`，本 change 全实现：
- `addRepo(regionName, name, url, username, password)` —— region POST /v2/helm/repos
- `removeRepo(regionName, name)` —— region DELETE
- `listChart(regionName, repoName, page)` —— GET /v2/helm/charts
- `queryChart(regionName, repoName, chartName, version)` —— GET /v2/helm/chart/{name}
- `installChart(regionName, teamName, body)` —— POST /v2/helm/install
- `commandInstall(regionName, teamName, command)` —— POST /v2/helm/cmd（接受裸命令字符串如 `helm install foo bar/baz --set k=v`，由 region grctl 解析）

console 落地 `helm_repo` 表的 4 字段（`name` / `url` / `username` / `password`），密码 **AES-256 加密** 后存（kuship 已有 `LegacyPasswordEncoder` 不适用，需新引入 `AesGcmEncryptor`），加密 key 来自 `kuship.helm.repo-password-key` 配置项；缺失时 fallback 明文存（仅 dev profile）。

### 决策 6：BackupOperations 新接口 + 整组 backup/import 双地写

`BackupOperations`（不在 14 接口骨架中，作为本 change 新增）：
- `backup(regionName, teamName, body)` —— region POST /v2/tenants/{tenant}/groupapp/{group_id}/backup（异步）
- `backupStatus(regionName, teamName, backupId)` —— region GET status
- `restore(regionName, teamName, body)` —— region POST 恢复
- `export(regionName, teamName, backupId)` —— region GET 拉文件下载 URL

console 写本地 `groupapp_backup` 元数据 + 调 region；status 由前端轮询；导出文件 URL 由 region 返回直链给前端。

### 决策 7：分享 + 模板导出走 service_share_record + app_export_record

`POST /share/record` 创建 share_record（status=0）→ `POST /share/{share_id}/info` 推送应用模板 YAML 至 region 生成镜像 → `events` 流水写 service_share_record_event（每步骤一行）→ `complete` 设 status=1。

`app-models/export` 直接生成本地 zip（rainbond 在 console 端打包，不调 region），用 `java.util.zip` 在内存压缩 + StreamingResponseBody 返回。文件不落地，节省磁盘 IO。

### 决策 8：image_tags 端点复用第 5 阶段 hub registry

`/teams/{tenant}/apps/image_tags` 接收 `{image, hub_user, hub_password, hub_url}` body，调 hub registry HTTP API（如 `https://hub.docker.com/v2/repositories/{org}/{repo}/tags`）拿 tag 列表。**不调 region**，console 直连 hub。
- 默认 hub url = `index.docker.io`
- 凭据来自第 5 阶段 `team_registry_auths` 表（按 region_name+tenant_id+image prefix 匹配）；找不到则匿名调用
- 公网调用 5s timeout，失败返回空数组而非 5xx

### 决策 9：整组 copy / migrate 异步状态机

- `POST /groupapp/{group_id}/copy` 接收 `{target_team_name, new_group_name}` → 同 region 内复制：列出原 group 全部 service → 用现有 `AppCreateService.create` 逐个复制（**不要直接 INSERT 表副本，避免漏配**）→ 返回新 group_id。
- `POST /groupapp/{group_id}/migrate` 跨 region：调 region `/v2/migrate` 推送备份文件 + 触发恢复 → 写 `service_group_migration` 一行 → 返回 record_id 供轮询。

### 决策 10：Tag 三表 schema 修正

proposal 写的 `tag_info` + `app_tag` 是错的；实际是 `rainbond_center_app_tag`（Tag 字典）+ `rainbond_center_app_tag_relation`（应用-Tag 关联）。Entity 命名用 `CenterAppTag` + `CenterAppTagRelation`。

## Risks / Trade-offs

- **[Risk]** 50 endpoint + 10 entity 单 change 体量大 → Mitigation：6 子域并行实施，每个子域内部 controller + service 闭环；先做 market/version 子域跑通后端到端，再做 share / upgrade / backup / helm。
- **[Risk]** rainbond `app_template` 字段是 longtext JSON/YAML 嵌套深 → Mitigation：console 不解析模板内部结构，仅作 String 透传与持久化；模板的 schema 验证留给 region。
- **[Risk]** `service_share_record` 16 阶段状态机历史复杂 → Mitigation：完全透明继承 rainbond 状态值，不重命名不重编码；前端按 step+status 双值组合渲染界面。
- **[Risk]** Helm repo 密码敏感 → Mitigation：AES-GCM 加密落库；密钥配置缺失时 dev profile 警告 + 明文，prod profile 启动失败拒绝运行。
- **[Risk]** 整组备份接口阻塞 console 线程 → Mitigation：仅触发 + 立即返回 backup_id；status 由前端轮询；不在 console 端做长轮询。
- **[Risk]** `image_tags` 公网 hub 拉取慢 → Mitigation：5s timeout + 失败回空数组；不重试；前端按 endpoint 1 次性渲染。
- **[Trade-off]** 不实现 `cloud/markets/{name}/organizations` 远程组织树 → 影响 <2% 用户；如真实需要再独立 change 补。
- **[Trade-off]** 不实现 KubeBlocks 备份 → KubeBlocks 在 misc 阶段统一处理，本阶段不混入。

## Migration Plan

阶段 A：market 子域（rainbond_center_app + version + tag CRUD + AppMarket 远程源）—— 最大子域，~15 endpoint
阶段 B：version 子域（单组件 version + 回滚）—— ~6 endpoint
阶段 C：share 子域（service_share_record + record_event + 完整异步流程）—— ~12 endpoint
阶段 D：upgrade 子域（app_upgrade_record + snapshots + service_upgrade_record）—— ~9 endpoint
阶段 E：backup 子域（groupapp_backup + 整组 copy/migrate）—— ~9 endpoint
阶段 F：helm 子域（helm_repo + chart install + cmd）—— ~9 endpoint
阶段 G：image_tags + Region API（HelmOperations + BackupOperations 实现）+ 集成测试 + 文档 —— 收尾

阶段间无强依赖；可由不同贡献者并行推进。

## Open Questions

- **(Q1)** kuship-ui 是否真的会调 `/cloud/markets/{name}/organizations` 拿组织树？如不调用就不实现。
- **(Q2)** Helm 密码加密 key 用什么方式注入？环境变量 `HELM_REPO_PASSWORD_KEY` 还是 K8s Secret 挂载？默认走环境变量。
- **(Q3)** 整组 copy 是否也支持跨 team？rainbond 原版只支持同企业内同 team。本 change 保持同等约束。

不阻塞实施。
