## 1. 基础设施：ResourceCenterOperations 接口 + 实现

- [x] 1.1 新建 `infrastructure/region/api/ResourceCenterOperations.java`：包含 10 个 method 的接口，default 均抛 `unsupported`：`getNsResourceTypes` / `getNsResources` / `getNsResource` / `postNsResource` / `putNsResource` / `deleteNsResource` / `getHelmReleases` / `installHelmRelease` / `previewHelmChart` / `getHelmReleaseHistory` / `getHelmReleaseDetail` / `upgradeHelmRelease` / `rollbackHelmRelease` / `uninstallHelmRelease` / `getWorkloadDetail` / `getPodDetail` / `getEvents` / `getPodLogStream`
- [x] 1.2 新建 `ResourceCenterOperationsDefaultImpl.java`（@Component，继承所有 default，作为空实现占位）
- [x] 1.3 新建 `modules/region/resource/ResourceCenterOperationsImpl.java`（@Service @Primary）：完整实现上述 10 个 method，URL 对齐 rainbond regionapi.py 3670-3847 行；使用 `RegionClientFactory` + `RegionApiResponseProcessor`；`getPodLogStream` 返回 `ResponseEntity<Resource>` 以支持 SSE 流透传

## 2. 本地辅助表：TeamHelmReleaseSource

- [x] 2.1 新建 `modules/region/resource/entity/TeamHelmReleaseSource.java`：`@Entity @Table(name="team_helm_release_source")` + 16 列（`ID`/`team_name`/`region_name`/`namespace`/`release_name`/`source_type`/`repo_name`/`repo_url`/`chart_name`/`chart_version`/`values_yaml`/`creator`/`create_time`/`update_time`），唯一约束 `(region_name,namespace,release_name)`；`create_time` 用 `@Column(updatable=false)`，`update_time` 用 `@PreUpdate`
- [x] 2.2 新建 `modules/region/resource/repository/TeamHelmReleaseSourceRepository.java`：`JpaRepository<TeamHelmReleaseSource, Integer>`；`Optional<TeamHelmReleaseSource> findByRegionNameAndNamespaceAndReleaseName(...)` + `List<TeamHelmReleaseSource> findByRegionNameAndNamespaceAndReleaseNameIn(...)`

## 3. Service 层：HelmReleaseSourceService

- [x] 3.1 新建 `modules/region/resource/service/HelmReleaseSourceService.java`：`@Service @Transactional`；注入 `TeamHelmReleaseSourceRepository`
- [x] 3.2 `saveOrUpdate(String teamName, String regionName, String namespace, String releaseName, String sourceType, String repoName, String repoUrl, String chartName, String chartVersion, String valuesYaml, String creator)`：查 findByRegionNameAndNamespaceAndReleaseName，存在则 update 所有字段，不存在则 save 新 entity
- [x] 3.3 `Map<String,Object> getSourceInfo(String regionName, String namespace, String releaseName)`：查 repo，返回 `{source_type, repo_name, repo_url, chart_name, chart_version, upgrade_mode}` map；缺失时返回 `{source_type:"legacy", ...}`
- [x] 3.4 `Map<String,Map<String,Object>> listSourceInfoByReleases(String regionName, String namespace, List<String> releaseNames)`：批量查，key = `namespace/releaseName`
- [x] 3.5 `void deleteByRelease(String regionName, String namespace, String releaseName)`：删对应 source 行（uninstall 时调用）
- [x] 3.6 `String resolveNamespace(String tenantName, TenantsRepository tenantsRepo)`：static helper，先按 tenant_name 查 `Tenants.namespace`，非空则用，否则返回 tenant_name

## 4. Controllers

### 4.1 NsResourceController

- [x] 4.1.1 新建 `modules/region/resource/controller/NsResourceController.java`，`@RestController @RequestMapping("/console/teams/{team_name}/regions/{region_name}")`
- [x] 4.1.2 `GET /ns-resource-types` + `/ns-resource-types/`：调 `ops.getNsResourceTypes(region, team)` → `GeneralMessage.ok(bean)`
- [x] 4.1.3 `GET /ns-resources` + `/ns-resources/`：传递所有 query params → `ops.getNsResources(region, team, params)` → `GeneralMessage.ok(bean)`
- [x] 4.1.4 `POST /ns-resources` + `/ns-resources/`（`consumes = "*/*"`, `@RequestBody byte[]`）：传 body bytes + Content-Type header → `ops.postNsResource(region, team, body, params, contentType)` → 透传 region 响应状态码
- [x] 4.1.5 `GET /ns-resources/{name}` + `/{name}/`：查详情 → `GeneralMessage.ok(bean)`
- [x] 4.1.6 `PUT /ns-resources/{name}` + `/{name}/`（`consumes = "*/*"`）：更新 → `GeneralMessage.ok(bean)`
- [x] 4.1.7 `DELETE /ns-resources/{name}` + `/{name}/`：删除 → `GeneralMessage.ok()`

### 4.2 TeamComponentsController

- [x] 4.2.1 新建 `modules/region/resource/controller/TeamComponentsController.java`
- [x] 4.2.2 `GET /console/teams/{team_name}/regions/{region_name}/components` + `/components/`：从 `TenantServiceRepository` 查 `tenant_id + region_name` 的服务列表，返回 `list` of `{service_id, service_cname, service_alias}`

### 4.3 HelmReleaseController

- [x] 4.3.1 新建 `modules/region/resource/controller/HelmReleaseController.java`，`@RestController @RequestMapping("/console/teams/{team_name}/regions/{region_name}")`
- [x] 4.3.2 `GET /helm/releases` + `/helm/releases/`：调 `ops.getHelmReleases(region, team, namespace)` → enrich list with source_info → `GeneralMessage.ok(bean)`；namespace 从 `HelmReleaseSourceService.resolveNamespace` 取
- [x] 4.3.3 `POST /helm/releases` + `/helm/releases/`：调 `build_helm_install_body`（store→repo 替换逻辑，复刻 Python）→ `ops.installHelmRelease` → persist source → `GeneralMessage.ok(bean)`
- [x] 4.3.4 `POST /helm/chart-preview` + `/helm/chart-preview/`：build body → `ops.previewHelmChart` → `GeneralMessage.ok(bean)`
- [x] 4.3.5 `GET /helm/releases/{release_name}` + `/{release_name}/`：调 detail → enrich with source_info + values_yaml 注入 → `GeneralMessage.ok(bean)`
- [x] 4.3.6 `PUT /helm/releases/{release_name}` + `/{release_name}/`：upgrade → persist source → `GeneralMessage.ok(bean)`
- [x] 4.3.7 `DELETE /helm/releases/{release_name}` + `/{release_name}/`：uninstall → delete source → `GeneralMessage.ok()`
- [x] 4.3.8 `GET /helm/releases/{release_name}/history` + `.../history/`：透传 → `GeneralMessage.ok(bean)`
- [x] 4.3.9 `POST /helm/releases/{release_name}/rollback` + `.../rollback/`：透传 → `GeneralMessage.ok(bean)`

### 4.4 ResourceCenterController

- [x] 4.4.1 新建 `modules/region/resource/controller/ResourceCenterController.java`，`@RestController @RequestMapping("/console/teams/{team_name}/regions/{region_name}/resource-center")`
- [x] 4.4.2 `GET /workloads/{resource}/{name}` + `/{name}/`：透传 → `GeneralMessage.ok(bean)`
- [x] 4.4.3 `GET /pods/{pod_name}` + `/{pod_name}/`：透传 → `GeneralMessage.ok(bean)`
- [x] 4.4.4 `GET /events` + `/events/`：透传 + query params → `GeneralMessage.ok(bean)`
- [x] 4.4.5 `GET /pods/{pod_name}/logs` + `.../logs/`（`@SkipResponseWrapper`）：SSE 透传，`StreamingResponseBody`，Content-Type `text/event-stream`；先发一帧 `: heartbeat\n\n`，再 stream chunk

### 4.5 ResourceCenterWsInfoController

- [x] 4.5.1 新建 `modules/region/resource/controller/ResourceCenterWsInfoController.java`
- [x] 4.5.2 `GET /console/teams/{team_name}/regions/{region_name}/resource-center/ws-info` + `/ws-info/`：返回 `{event_websocket_url, namespace, tenant_name}`；`event_websocket_url` 从 `RegionInfo.websocketAddress` 拼 `/event_log`（对齐 rainbond `ws_service.get_event_log_ws`）

## 5. 单测

- [x] 5.1 新建 `modules/region/integration/ResourceCenterIntegrationTest.java`：`@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean ResourceCenterOperations`；seed 1 个 team + region + tenant，验证：
  - `GET /ns-resource-types` → 200 + data.bean 透传
  - `GET /ns-resources` → 200 + data.bean 透传
  - `GET /components` → 200 + data.list（空或有组件）
  - `GET /helm/releases` → 200 + source_info 被注入
  - `POST /helm/releases` → 200 + source 被写入 team_helm_release_source 表
  - `DELETE /helm/releases/{name}` → 200 + source 被删除
  - `GET /resource-center/events` → 200 + data.bean 透传
  - `GET /resource-center/ws-info` → 200 + bean 含 event_websocket_url + namespace
- [x] 5.2 新建 `HelmReleaseSourceServiceTest.java`（单元测试，不需 DB）：验证 `saveOrUpdate` 去重逻辑、`getSourceInfo` 缺失返回 legacy 默认、`listSourceInfoByReleases` 批量 map key 格式

## 6. OpenSpec 文件

- [x] 6.1 补全 `specs/kuship-console-app/spec.md` 资源中心相关 Requirement 段落

## 7. 编译 + 测试（人工验证）

- [x] 7.1 `cd kuship-console && mvn -DskipTests package` —— 编译通过（已验证：0 错误）
- [x] 7.2 `mvn -Dtest='HelmReleaseSourceServiceTest' test` —— 单元测试通过（7/7）；集成测试需真实 MySQL，在本地无 DB 环境下跳过 [ ] 需用户联动验证
- [ ] 7.3 curl 验证 `GET /console/teams/{team}/regions/{region}/resource-center/ws-info` 返回 200 —— 需用户联动验证
