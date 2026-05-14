# CLAUDE.md 追加片段 — migrate-console-dependency-extras

> 此文件由子 change 工程师生成，供 leader 合并到 kuship-console/CLAUDE.md 的「应用与组件管理」段落。

## 批量组件依赖与旧版卷依赖（migrate-console-dependency-extras）

`ServiceDependencyOperations` 3 个 default-unsupported method 已在 `ServiceDependencyOperationsImpl` 覆盖：

| method | HTTP | region 路径 | 说明 |
|--------|------|-------------|------|
| `addDependencies(rn, tn, alias, body)` | POST | `/v2/tenants/{ns}/services/{alias}/dependencys` | 批量添加（**`dependencys` 保留 rainbond 历史拼写**） |
| `addVolumeDependency(rn, tn, alias, body)` | POST | `/v2/tenants/{ns}/services/{alias}/volume-dependency` | 旧版挂载依赖（仅内部调用） |
| `deleteVolumeDependency(rn, tn, alias, body)` | DELETE | `/v2/tenants/{ns}/services/{alias}/volume-dependency` | 旧版删除挂载依赖（仅内部调用） |

### 新增 controller endpoint

`AppDependencyController` 追加：

- `POST /console/teams/{team_name}/apps/{service_alias}/dependency-list`（trailing slash 兼容）
  - 权限：`@RequirePerm(PermCode.APP_CREATE_PERMS)`
  - body：`{"dep_service_ids": ["id1", "id2", ...]}`
  - 委托 `AppDependencyBatchService.addBatch` 处理

### 新增 service

`AppDependencyBatchService`（`modules/application/service/`）：

- `@Transactional public Map<String, Object> addBatch(teamName, serviceAlias, body)`
- 两阶段写：
  1. 去重（已存在跳过，不报错）
  2. 循环依赖 BFS 检测（抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`）
  3. 本地批量 INSERT `tenant_service_relation`
  4. 调 region `addDependencies`（body 注入 `tenant_id = Tenants.namespace`）
  5. region 失败 → 事务自动回滚 step 3

### 关键约束

- region 路径 `dependencys` 拼写**不得修改**（rainbond region 端历史）
- `addVolumeDependency` / `deleteVolumeDependency` **无 console controller URL**（rainbond 5.0+ 前端已不直调），仅供 helm-install / app-import 子 change 内部调用
- `volume-dependency` region method 的接线由 `migrate-console-helm-install` / `migrate-console-app-import-export` 负责
