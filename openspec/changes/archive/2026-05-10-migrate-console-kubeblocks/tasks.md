# Tasks — migrate-console-kubeblocks

## 1. 校验既有桩与依赖

- [ ] 1.1 确认 `kuship-console/src/main/java/cn/kuship/console/modules/misc/kubeblocks/controller/KubeBlocksController.java` 仍持有 8 个 stub endpoint（`supportedDatabases` / `storageClasses` / `backupRepos` / `detail` / `backupConfig` / `backups` / `parameters` / `restore`）；如签名 / 路径已被其他 change 修改，先与作者对齐
- [ ] 1.2 确认 `migrate-console-misc` 已归档（提供 stub controller）+ `migrate-console-region-cluster` 已归档（提供 `RegionClientFactory` mTLS 客户端 + `region_info` entity + `RegionResponseProcessor` helper），本 change 直接复用
- [ ] 1.3 确认 `cn.kuship.console.modules.application.repository.TenantServiceRepository` 已落地 `findByTenantIdAndServiceAlias` finder（由 `migrate-console-application-core` 提供），本 change 在 controller 内复用解析 service entity
- [ ] 1.4 确认项目 `cn.kuship.console.modules.account.constant.PermCode` 枚举中存在 `APP_OVERVIEW_DESCRIBE` / `APP_CREATE_PERMS` / `TEAM_REGION_DESCRIBE` 三个码值；如缺失（如 `APP_OVERVIEW_MANAGE` 未定义），按 design.md 决策 5 退化为 `APP_CREATE_PERMS`
- [ ] 1.5 探测 region 端：用本地 8080 console 已注入的 RegionClient，curl 验证 13 个 URL 形状（实施期可推迟到 §7 联动验证）：
  - `GET /v2/cluster/kubeblocks/supported-databases` → 期望 200 + bean 含 supported types
  - `GET /v2/cluster/kubeblocks/storage-classes` → 期望 200 + list
  - `GET /v2/cluster/kubeblocks/backup-repos` → 期望 200 + list
  - `GET /v2/cluster/kubeblocks/clusters/<sid>` → 期望 200 + bean
  - `POST /v2/cluster/kubeblocks/clusters` body=<sample> → 期望 200/201
  - `PUT /v2/cluster/kubeblocks/clusters/<sid>` body=<scale> → 期望 200
  - `DELETE /v2/cluster/kubeblocks/clusters` body=<delete_data> → 期望 200
  - `GET /v2/cluster/kubeblocks/clusters/<sid>/backups?page=1&page_size=10` → 期望 200 + list
  - `POST /v2/cluster/kubeblocks/clusters/<sid>/backups` 无 body → 期望 200/202
  - `DELETE /v2/cluster/kubeblocks/clusters/<sid>/backups` body=`{backups:[...]}` → 期望 200
  - `PUT /v2/cluster/kubeblocks/clusters/<sid>/backup-schedules` body=<config> → 期望 200
  - `GET /v2/cluster/kubeblocks/clusters/<sid>/parameters?page=1&page_size=6` → 期望 200 + list
  - `POST /v2/cluster/kubeblocks/clusters/<sid>/parameters` body=<params> → 期望 200
  - `GET /v2/cluster/kubeblocks/clusters/<sid>/pods/<pn>/details` → 期望 200 + bean
- [ ] 1.6 把 1.5 探测结果如有差异（如 region 端某 URL 已重命名或 method 改变）写进 `design.md` "Region API URL 表" 下方"实施期探测结果"段，明确最终路径与差异

## 2. 新建 KubeBlocksOperations 接口与默认实现

- [ ] 2.1 新建 `cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperations` 接口，声明 13 个 method（签名按 design.md "Region API URL 表" 列）：
  - `Map<String, Object> listSupportedDatabases(String regionName);`
  - `Map<String, Object> listStorageClasses(String regionName);`
  - `Map<String, Object> listBackupRepos(String regionName);`
  - `Map<String, Object> getClusterDetail(String regionName, String serviceId);`
  - `Map<String, Object> listClusterParameters(String regionName, String serviceId, int page, int pageSize, String keyword);`
  - `Map<String, Object> listClusterBackups(String regionName, String serviceId, int page, int pageSize);`
  - `Map<String, Object> getClusterPodDetail(String regionName, String serviceId, String podName);`
  - `Map<String, Object> createCluster(String regionName, Map<String, Object> body);`
  - `Map<String, Object> expansionCluster(String regionName, String serviceId, Map<String, Object> body);`
  - `Map<String, Object> deleteCluster(String regionName, Map<String, Object> body);`
  - `Map<String, Object> deleteClusterBackups(String regionName, String serviceId, List<String> backups);`
  - `Map<String, Object> updateBackupConfig(String regionName, String serviceId, Map<String, Object> body);`
  - `Map<String, Object> createManualBackup(String regionName, String serviceId);`
  - `Map<String, Object> updateClusterParameters(String regionName, String serviceId, Map<String, Object> body);`
- [ ] 2.2 新建 `KubeBlocksOperationsDefaultImpl` 类（同包），全部 13 method `default throw new UnsupportedOperationException("not yet implemented; will be filled in by migrate-console-kubeblocks");`，**不**加 `@Service` / `@Primary` 注解（仅作为接口默认占位，便于真实 impl 替换前的编译通过）
- [ ] 2.3 接口 KubeBlocksOperations 上加 javadoc 标注："业务自治接口，非 14 核心 region 骨架；归属 modules/misc/kubeblocks/ 业务域"

## 3. 实现 KubeBlocksOperationsImpl

- [ ] 3.1 新建 `cn.kuship.console.modules.misc.kubeblocks.api.KubeBlocksOperationsImpl`，标注 `@Primary @Service`，构造器注入：
  - `RegionClientFactory regionClientFactory`
  - `tools.jackson.databind.ObjectMapper objectMapper`
  - `RegionResponseProcessor processor`
- [ ] 3.2 实现 3 个 region-level GET method（无 service_id 路径段）：
  - `listSupportedDatabases(rn)` → `GET /v2/cluster/kubeblocks/supported-databases`
  - `listStorageClasses(rn)` → `GET /v2/cluster/kubeblocks/storage-classes`
  - `listBackupRepos(rn)` → `GET /v2/cluster/kubeblocks/backup-repos`
  - 全部用 `processor.checkStatus + extractRoot` 拿响应根节点，返 Map（含 list 字段）
- [ ] 3.3 实现 3 个 cluster-level GET method（有 service_id 路径段）：
  - `getClusterDetail(rn, sid)` → `GET /v2/cluster/kubeblocks/clusters/{service_id}`
  - `listClusterBackups(rn, sid, page, pageSize)` → `GET /v2/cluster/kubeblocks/clusters/{service_id}/backups?page=&page_size=`
  - `getClusterPodDetail(rn, sid, podName)` → `GET /v2/cluster/kubeblocks/clusters/{service_id}/pods/{pod_name}/details`，`podName` URL encode
- [ ] 3.4 实现 1 个带 keyword query 的 GET method：
  - `listClusterParameters(rn, sid, page, pageSize, keyword)` → `GET /v2/cluster/kubeblocks/clusters/{service_id}/parameters?page=&page_size=&keyword=`
  - 按 design.md 决策 4 规则：page/pageSize 用入参（不传 null），keyword 仅非空+非空白时拼入；URL encode 全部 query value
- [ ] 3.5 实现 2 个 POST 写 method：
  - `createCluster(rn, body)` → `POST /v2/cluster/kubeblocks/clusters`，body 是 Map → `objectMapper.writeValueAsString(body)`
  - `createManualBackup(rn, sid)` → `POST /v2/cluster/kubeblocks/clusters/{service_id}/backups` 无 body（rainbond Python `_post(url, headers, region=)` 不传 body 参数）
  - `updateClusterParameters(rn, sid, body)` → `POST /v2/cluster/kubeblocks/clusters/{service_id}/parameters`
- [ ] 3.6 实现 1 个 PUT method：
  - `expansionCluster(rn, sid, body)` → `PUT /v2/cluster/kubeblocks/clusters/{service_id}`
  - `updateBackupConfig(rn, sid, body)` → `PUT /v2/cluster/kubeblocks/clusters/{service_id}/backup-schedules`
- [ ] 3.7 实现 2 个 DELETE method（with body）：
  - `deleteCluster(rn, body)` → `DELETE /v2/cluster/kubeblocks/clusters` body 含 `service_ids` 数组等
  - `deleteClusterBackups(rn, sid, backups)` → `DELETE /v2/cluster/kubeblocks/clusters/{service_id}/backups` body=`{"backups": backups}`（rainbond Python 行为）
  - 用 Spring 6 RestClient 写法：`client.method(HttpMethod.DELETE).uri(url).contentType(JSON).body(bodyJson).retrieve()...`
- [ ] 3.8 全部 method 异常处理：region 错误一律抛 `RegionApiException`（由 `processor.checkStatus` 自动抛出），不在 impl 层 catch；team-not-found / service-not-found 等业务校验放 controller 层（见任务 §4）
- [ ] 3.9 单测 `KubeBlocksOperationsImplTest`（用 `MockRestServiceServer`），13 method × (1 happy + 1 region 5xx 透传) = **26 用例全过**：
  - 断言 URL / HTTP method / Content-Type / body shape
  - 特别用例：
    - `listClusterParameters` 含 keyword 与不含 keyword 两个用例（验证 query string 拼接规则）
    - `deleteClusterBackups` 断言 DELETE 请求 body 形状 `{"backups":["b1","b2"]}`
    - `getClusterPodDetail` podName=`mysql-cluster-0.example.com` 验证 URL encode 不丢点号
    - `createManualBackup` 断言 POST 请求 body 为空（content-length=0 或 body=null）
    - 13 method 各 1 个 region 5xx 透传断言（mock 返 503 + msg_show）

## 4. KubeBlocksController 接线（去 stub）

- [ ] 4.1 在 `KubeBlocksController.java` 上注入：
  - `KubeBlocksOperations kubeblocksOps`
  - `TenantsRepository tenantsRepo`
  - `TenantServiceRepository serviceRepo`
- [ ] 4.2 抽出公共 helper `private TenantService resolveService(String teamName, String serviceAlias)`：
  - `tenantsRepo.findByTenantName(teamName).orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"))`
  - `serviceRepo.findByTenantIdAndServiceAlias(tenant.getTenantId(), serviceAlias).orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"))`
- [ ] 4.3 替换 3 个 region-level GET endpoint 的 stub（保留路径不变）：
  - `supportedDatabases` → `kubeblocksOps.listSupportedDatabases(region)`
  - `storageClasses` → `kubeblocksOps.listStorageClasses(region)`
  - `backupRepos` → `kubeblocksOps.listBackupRepos(region)`
  - 全部 `@RequirePerm(PermCode.TEAM_REGION_DESCRIBE)`（按决策 5）
- [ ] 4.4 替换 `detail` endpoint 的 stub + 追加 `expansion`（同 URL 不同 HTTP method）：
  - `@GetMapping(...)`：调 `getClusterDetail(service.serviceRegion, service.serviceId)`，`@RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)`
  - `@PutMapping(...)`：调 `expansionCluster(...)`，`@RequirePerm(PermCode.APP_CREATE_PERMS)`，body 直接 `@RequestBody Map<String, Object>` 透传
- [ ] 4.5 **删除** stub 中的 `GET /kubeblocks/backup-config` endpoint（按决策 6，UI 改用 `getDetail` 取 `bean.backup_config`）；保留 `PUT /kubeblocks/backup-config`：
  - `@PutMapping(...)`：调 `updateBackupConfig(...)`，`@RequirePerm(PermCode.APP_CREATE_PERMS)`
- [ ] 4.6 替换 `backups` endpoint 的 stub + 追加 POST/DELETE 同 URL：
  - `@GetMapping(...)`：调 `listClusterBackups(...)`，按决策 4 接 `@RequestParam(required=false) Integer page` / `Integer pageSize`，缺省 page=1 / pageSize=10
  - `@PostMapping(...)`：调 `createManualBackup(...)`，无 body，`@RequirePerm(PermCode.APP_CREATE_PERMS)`
  - `@DeleteMapping(...)`：调 `deleteClusterBackups(...)`，`@RequestBody Map<String, Object>` body 取 `backups` 字段（List<String>），`@RequirePerm(PermCode.APP_CREATE_PERMS)`
- [ ] 4.7 替换 `parameters` endpoint 的 stub + 追加 POST 同 URL：
  - `@GetMapping(...)`：调 `listClusterParameters(...)`，接 page / pageSize / keyword query 参数
  - `@PostMapping(...)`：调 `updateClusterParameters(...)`，body 透传，`@RequirePerm(PermCode.APP_CREATE_PERMS)`
- [ ] 4.8 在文件顶部添加 cluster-level POST endpoint `createCluster`：
  - 路径：`/console/teams/{team_name}/regions/{region_name}/kubeblocks/clusters`（与 rainbond `KubeBlocksClusterCreateView` 路径对齐——但若 rainbond 该 URL 无独立 view，保留与现有 stub 集合的命名一致性，使用 `/console/teams/{team_name}/regions/{region_name}/kubeblocks/clusters`）
  - **注意**：rainbond Python 端实际"创建集群"是通过 `KubeBlocksComponentCreateView` （`urls.py:506` `^teams/<tn>/apps/kubeblocks$`）走的——它走的是"创建组件 + 集群"复合流程，与本 change 的 `createCluster` 单纯透传 region 不同；本 change 推荐**不暴露独立 createCluster controller endpoint**，由后续 `add-kubeblocks-create-flow` hardening 接 region API；本 change 仅落地 `KubeBlocksOperations.createCluster` 供内部其他 service 调用 + 单测覆盖
  - 实施期先按"接口落地 + 不暴露 controller endpoint" 推进；如确需 console URL 暴露，由实施工程师按 rainbond 上游 view 决定
- [ ] 4.9 在文件顶部添加 cluster-level DELETE endpoint `deleteCluster`：
  - 同 4.8 推理：rainbond Python 端"删除集群"也走 `KubeBlocksClusterDetailView`/复合 view，本 change 仅落地 Operations method，不暴露独立 controller endpoint；如需暴露，由后续 hardening 决定
- [ ] 4.10 保留 `restore` endpoint 现有 stub（按决策 7）：
  - 添加 INFO 日志 `[KubeBlocks][stub] restore endpoint hit; full restore flow pending add-kubeblocks-restore`
  - 注释行标注 `// TODO(add-kubeblocks-restore): 接 ServiceOperations.createService + KubeBlocksOperations.restoreFromBackup（hardening change）`
- [ ] 4.11 全部 controller method 路径变量统一 snake_case，trailing slash 双声明（沿用 stub 已有写法），不显式 ApiResult 包（advice 自动）

## 5. SecurityConfig 评估

- [ ] 5.1 13 个 method 中暴露给 controller 的 endpoint（按 4.3-4.10 实际落地的 12 个 HTTP method，剔除 4.5 删除的 GET backup-config + 4.8/4.9 不暴露的 createCluster/deleteCluster controller endpoint）全部走默认 JWT 鉴权链 + RBAC 注解切面，**不**加 permitAll
- [ ] 5.2 不修改 `SecurityConfig.java`；本 change 不引入新 SecurityConfig 配置项
- [ ] 5.3 抽烟测试：用任意 PAT 调 `GET /console/teams/<tn>/regions/<rn>/kubeblocks/supported_databases` 验证 200；不带 token 调用验证 401（沿用项目既定 401 响应形状 `general_message + msg_show=未认证或 token 失效`）

## 6. 集成测试

实施合并到一个 `KubeBlocksIntegrationTest` 类（`@SpringBootTest + @ActiveProfiles({"local","contract-test"}) + @MockitoBean KubeBlocksOperations` + 真实 DB seed），降低 fixture 重复成本。至少 10 用例：

- [ ] 6.1 `listSupportedDatabases` happy path：mock 返 `Map.of("list", List.of(Map.of("name","mysql","versions",...)))`，断言响应 200 + `data.list` 字段透传 + general_message 五项形状
- [ ] 6.2 `getClusterDetail` happy path：mock 返 bean 含 `kubeblocks_status` / `backup_config` 字段，断言 `data.bean.kubeblocks_status` 与 `data.bean.backup_config` 透传
- [ ] 6.3 `expansionCluster` happy + body 透传：PUT body=`{"replicas":3,"cpu":"1000m"}`，ArgumentCaptor 断言 Operations 入参 body 含两个 key
- [ ] 6.4 `listClusterBackups` 分页 happy：query `?page=2&page_size=20`，断言 controller 解析 page/pageSize 并传给 Operations
- [ ] 6.5 `createManualBackup` happy：POST 无 body，verify Operations 入参仅 `(rn, sid)` + 调用次数 = 1
- [ ] 6.6 `deleteClusterBackups` happy：DELETE body=`{"backups":["b1","b2"]}`，ArgumentCaptor 断言 Operations 入参 backups list 大小 = 2 + 元素正确
- [ ] 6.7 `updateBackupConfig` happy：PUT body 透传 + verify 调用 `updateBackupConfig` 而非 `updateClusterParameters`
- [ ] 6.8 `listClusterParameters` 含 keyword：query `?page=1&page_size=6&keyword=innodb`，断言 keyword 透传
- [ ] 6.9 `updateClusterParameters` happy：POST body 透传，verify Operations 入参 body 含原始字段
- [ ] 6.10 region 异常透传：mock `getClusterDetail` 抛 `RegionApiException(503, "region service unavailable", "集群不可用")`，断言响应 503 + body 含 `data.bean.trace_id` + `msg_show=集群不可用`
- [ ] 6.11 团队不存在 404：调 `GET /console/teams/no-such-team/apps/<sa>/kubeblocks/detail`，断言响应 404 + `msg_show=团队不存在`，**未发起** Operations 调用（verify Operations 0 次）
- [ ] 6.12 service 不存在 404：team 存在但 service 不存在，断言响应 404 + `msg_show=组件不存在`，**未发起** Operations 调用

## 7. 文档与归档

- [ ] 7.1 更新 `kuship-console/CLAUDE.md`，在 misc 段或独立追加 "KubeBlocks 数据库托管（migrate-console-kubeblocks）" 段：列 1 个新接口（13 method）+ 8 endpoint × 12 HTTP method（去除决策 6 删除的 GET backup-config）+ region URL 路径表 + 与 `add-kubeblocks-restore` / `add-kubeblocks-cluster-events` / `add-kubeblocks-cluster-actions` / `add-kubeblocks-connect-info` 的扩展点边界 + 决策 7（restore 保留 stub）说明 + decision 6 的 UI 端调整提示（前端 backup-config 编辑改用 detail bean 字段）
- [ ] 7.2 母路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把 "migrate-console-kubeblocks" 行（P1 #1）标注为 已完成 + 写入实际工时（归档时执行）
- [ ] 7.3 把 §1.5 / §1.6 探测结果（如有）记录到 `design.md` "实施期探测结果"段；如全部 13 URL 与 rainbond Python 一致无差异，仅写入"探测验证：13 URL 形状与 rainbond Python 端 1:1 对齐，无路径差异"
- [ ] 7.4 走归档流程 `openspec archive migrate-console-kubeblocks --skip-specs`（如 spec.md 已通过 PR review）；产出归档目录 `openspec/changes/archive/<date>-migrate-console-kubeblocks/`

## 8. 编译 / 重启 / 联动验证

- [ ] 8.1 `cd kuship-console && mvn -DskipTests package` 通过（无编译错误）
- [ ] 8.2 `mvn test -Dtest='*KubeBlocks*'` 通过：`KubeBlocksOperationsImplTest`（26 用例）+ `KubeBlocksIntegrationTest`（≥10 用例）= 至少 36 用例全过
- [ ] 8.3 `mvn -Pnative,native-test test -Dtest='*KubeBlocks*'`（如已加 native 兼容）通过；如新增 controller / service 类需在 `NativeTestRuntimeHintsRegistrar` 自动扫描覆盖范围内（按既定包路径约定 `cn.kuship.console.modules.**` 自动 pick），无需手动注册（**需用户本地 GraalVM 21 已装时联动**）
- [ ] 8.4 重启 console；`curl -s -H "Authorization: GRJWT $TOKEN" "http://localhost:8080/console/teams/<tn>/regions/<rn>/kubeblocks/supported_databases" | jq .` 返 200 + `data.list` 含支持的数据库类型（**需用户本地起 console + region 后联动**）
- [ ] 8.5 `curl ... /console/teams/<tn>/apps/<sa>/kubeblocks/detail | jq .` 返 200 + `data.bean.kubeblocks_status` + `data.bean.backup_config`（**需用户联动**）
- [ ] 8.6 `curl -X POST -H "Content-Type: application/json" -d '{"replicas":3,"cpu":"1000m"}' .../apps/<sa>/kubeblocks/detail` 返 200 验证扩容（**需用户联动**）
- [ ] 8.7 不存在的 service `/apps/no-such-svc/kubeblocks/detail` → 404 + `msg_show=组件不存在`，本地不缓存（**需用户联动**）
- [ ] 8.8 region 端被关停时调任意 endpoint → 503 + `data.bean.trace_id`（**需用户联动**）
- [ ] 8.9 kuship-ui 数据库托管页打开（DB 列表 / 详情 / 创建向导 / 备份列表 / 参数管理）—— UI 端从空白变成有数据，验证 13 method 真实调通（**需用户联动**）
