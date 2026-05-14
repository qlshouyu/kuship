## 1. team_helm_release_source 表 JPA 映射

- [x] 1.1 在 `kuship-console/src/main/java/cn/kuship/console/modules/team/entity/TeamHelmReleaseSource.java` 创建 entity，PK 字段名 `ID` 类型 `Integer` 自增，`@Table(name = "team_helm_release_source")`，含 `team_name(64)` / `region_name(64)` / `namespace(128)` / `release_name(128)` / `source_type(32)` / `repo_name(128, nullable)` / `repo_url(255, nullable)` / `chart_name(128, nullable)` / `chart_version(64, nullable)` / `values_yaml (TEXT, nullable)` / `creator(64, nullable)` / `create_time(DateTime, nullable)` / `update_time(DateTime, nullable)`，**不加 `@Version`**
- [x] 1.2 在 `modules/team/repository/TeamHelmReleaseSourceRepository.java` 创建 `JpaRepository<TeamHelmReleaseSource, Integer>`，提供 `findByRegionNameAndNamespaceAndReleaseName` / `findByRegionNameAndNamespaceAndReleaseNameIn` / `deleteByRegionNameAndNamespaceAndReleaseName` 派生查询
- [x] 1.3 启动 kuship-console 验证 `hibernate.ddl-auto=validate` 模式下不报错（`HelmReleaseIntegrationTest` 在 ActiveProfiles=local 下完成 schema validate）
- [x] 1.4 Repository 三组派生查询通过 `HelmReleaseIntegrationTest.install/upgrade/uninstall/detail` 路径间接验证（save → findByReleaseName → findByReleaseNameIn → deleteByReleaseName 全跑通）

## 2. HelmOperations 域接口扩充

- [x] 2.1 在 `infrastructure/region/api/HelmOperations.java` 追加 8 个 default method：`getTenantHelmReleases / installTenantHelmRelease / previewTenantHelmChart / getTenantHelmReleaseDetail / upgradeTenantHelmRelease / uninstallTenantHelmRelease / getTenantHelmReleaseHistory / rollbackTenantHelmRelease`，签名参考 rainbond `regionapi.py:3739-3805`
- [x] 2.2 把 `IMPLEMENTING_CHANGE` 常量更新为 `"migrate-console-helm-release"`
- [x] 2.3 在 `modules/appmarket/helm/api/HelmOperationsImpl.java`（`@Primary`）实现这 8 个 method：每个都用 `RegionApiSupport.exchange(clientFactory, regionName, "", "helm", url, method, caller)` 模板，URL 严格对齐 rainbond Python 端 `/v2/tenants/{tenant_name}/helm/releases*`
- [x] 2.4 list / detail / history method 用 `RegionApiResponseProcessor.extractBean(resp, Map.class, ...)` 解析返回 `Map<String, Object>`；install / upgrade / rollback / preview 同样返回 Map；uninstall 返回 void（仅 `processor.checkStatus`）
- [x] 2.5 GET 请求的 namespace 用 `?namespace={ns}` query param 传递（与 rainbond Python 端 `params=...` 一致）；URL encode 用 `URLEncoder.encode(s, UTF_8)`

## 3. 业务辅助逻辑迁移

- [x] 3.1 创建 `modules/team/service/HelmReleaseService.java`，注入 `HelmOperations` / `TenantsRepository` / `HelmRepoRepository`（已存在于 appmarket 模块）/ `TeamHelmReleaseSourceRepository`
- [x] 3.2 实现 `resolveNamespace(teamName)`：查 `Tenants`，先取 `namespace`，否则 fallback `tenant_name`；查不到则 `ServiceHandleException(404, "team not found", "团队不存在")`
- [x] 3.3 实现 `buildInstallBody(rawBody, namespace)`：当 `source_type=store` 且 `repo_name` 存在时，查 `helm_repo` 把 `source_type` 改为 `repo`，注入 `repo_url / username / password`；其他 source_type 透传
- [x] 3.4 实现 `enrichReleaseList(bean, regionName, namespace)`：从 `bean.list[]` 收集 release name，批量查 `TeamHelmReleaseSourceRepository.findByRegionNameAndNamespaceAndReleaseNameIn`，按 `namespace + "/" + name` 组 Map，给每个 release 注入 `source_info`（结构对齐 Python `build_helm_release_source_info` 输出）
- [x] 3.5 实现 `enrichReleaseDetail(bean, regionName, namespace, releaseName)`：查 `findByRegionNameAndNamespaceAndReleaseName`，注入 `summary.source_info`；当 `values_yaml` 非空时覆盖 `summary.values`
- [x] 3.6 实现 `persistReleaseSource(rawBody, installBody, responseBean, teamName, regionName, namespace, creator)`：用 `save_or_update` 语义（`existing.ifPresentOrElse(update, insert)`），保留**原始 `raw_body.source_type`** 而非转换后的 `repo`；values_yaml 用 `normalizeYaml`（dict/list → SnakeYAML dump，bytes/str 直接转 String）
- [x] 3.7 实现 `deleteReleaseSource(regionName, namespace, releaseName)`：在 `uninstallRelease` 内嵌调用 `deleteByRegionNameAndNamespaceAndReleaseName`，try-catch 仅打 ERROR 日志
- [x] 3.8 单元测试 `HelmReleaseServiceTest`：21 用例全绿，覆盖 `buildInstallBody` 5 分支（store→repo / store 但 repo_name 缺失 / store 但 unknown repo / non-store 透传 / 缺省 source_type）、`enrichReleaseList` 含命中/未命中 release / 空列表 / null bean、`persistReleaseSource` 保留原始 source_type / 缺 release_name / 复用现有 row、`normalizeYaml` 4 分支

## 4. Controller 落地

- [x] 4.1 创建 `modules/team/controller/HelmReleasesController.java`，类级 `@RequestMapping("/console")` 不要写 context-path，注入 `HelmReleaseService`
- [x] 4.2 端点 1：`@GetMapping(value = {"/teams/{team_name}/regions/{region_name}/helm/releases", "/teams/{team_name}/regions/{region_name}/helm/releases/"})` 调 `service.listReleases(teamName, regionName)`，return Map（advice 自动包成 bean）
- [x] 4.3 端点 2：`@PostMapping(value = {同上})` 接 `@RequestBody Map<String, Object> rawBody`，调 `service.installRelease(teamName, regionName, rawBody, creator)`
- [x] 4.4 端点 3：`@PostMapping(value = {"/teams/{team_name}/regions/{region_name}/helm/chart-preview", ".../"})` 调 `service.previewChart(teamName, regionName, rawBody)`
- [x] 4.5 端点 4：`@GetMapping/@PutMapping/@DeleteMapping(value = {"/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}", ".../"})` 三组 method 共用一个路径 mapping，分别调 `getDetail / upgrade / uninstall`
- [x] 4.6 端点 5：`@GetMapping(value = {"/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/history", ".../"})` 调 `service.getHistory(...)`
- [x] 4.7 端点 6：`@PostMapping(value = {"/teams/{team_name}/regions/{region_name}/helm/releases/{release_name}/rollback", ".../"})` 调 `service.rollback(teamName, regionName, releaseName, rawBody)`，service 内部在 body 缺 namespace 时自动注入
- [x] 4.8 controller 不加 `@RequirePerm`（与 rainbond-console `TenantHeaderView` 一致，仅依赖 JWT 认证 + tenant 上下文）
- [x] 4.9 controller 端到端覆盖通过 `HelmReleaseIntegrationTest` 完成（10 用例全绿）：9 个 HTTP 方法的路径匹配（list/install/preview/detail/upgrade/uninstall/history/rollback）+ trailing slash 兼容（list_trailing_slash_matches_same_handler）+ 路径变量 snake_case 解析（{team_name}/{region_name}/{release_name} 全部正确路由）

## 5. 异常 / 边界处理

- [x] 5.1 `enrichReleaseList` 与 `enrichReleaseDetail` 的 `bean / summary / list` 取值用 `Optional.ofNullable(...).orElse(Map.of() / List.of())` 兜底，覆盖 region 返回空体场景
- [x] 5.2 `persistReleaseSource` 落库失败、`deleteReleaseSource` 删行失败均仅 ERROR 日志（含 trace_id），不抛异常影响主流程
- [x] 5.3 region 异常透传：`HelmOperationsImpl` 不吞 region API 异常，由现有 `RegionApiResponseProcessor` 抛 `RegionApiException`（HTTP 状态对齐 region），中文文案由 `RegionErrorMsgEnricher` 已实现的 helm.sh annotation 规则处理
- [x] 5.4 入参校验：install / upgrade body 缺 `chart_name` 或 `release_name` 时，由 region 后端返回 400 + 中文文案，kuship-console 透传（不重复实现校验）

## 6. 集成测试与端到端验证

- [x] 6.1 集成测试 `HelmReleaseIntegrationTest`（10 用例全绿）：用 `@MockitoBean HelmOperations` 替代 region 后端（比 WireMock 更轻量），跑通"列表 → 安装（落库验证）→ 详情（values_yaml 覆盖）→ 升级（save_or_update 复用 row id）→ 卸载（删行验证）→ 历史 → 回滚（namespace 自动注入）"完整链路，断言每一步 `team_helm_release_source` 表状态
- [ ] 6.2 与 rainbond-console 的双向兼容验证（手测脚本，留待人工执行）：在同一 console 库下，用 rainbond-console 写一行，kuship-console 读 → 用 kuship-console 写一行，rainbond-console 读
- [ ] 6.3 端到端验证（kuship-ui，留待人工执行）：启动 kuship-console（8000）+ kuship-ui（8000 前端），访问应用市场 → Helm 应用，确认列表 200、source_info 字段存在；点击安装一个 nginx chart 全流程通；卸载后表行消失

## 7. 文档与归档

- [x] 7.1 更新 `kuship-console/CLAUDE.md`：在 plugin 章节后插入 "Helm Release 域（migrate-console-helm-release）" 章节，列出 controller / entity / region API / 业务规则迁移 / 写两阶段策略 / 测试覆盖
- [x] 7.2 更新 `kuship-console/README.md` 前置环境：补充"schema 由 rainbond-console (Django) 拥有 + ddl-auto=validate 启动失败兜底"通用说明（覆盖本 change 引入的 `team_helm_release_source` 与既有所有共享表）
- [x] 7.3 `mvn clean package -DskipTests` 通过（kuship-console.jar 已生成）；`mvn clean test` 跑完 209 个测试，1 个失败（`AccountAuthIntegrationTest.login_thenDetails_endToEnd`，**与本 change 无关**：`UserSelfController.details()` 返回 `user_name` 字段，测试断言 `nick_name`，是 main 分支预存在的字段命名不一致 bug）；排除该 flaky test 后 208 个全绿
- [x] 7.4 `openspec validate migrate-console-helm-release --strict` 通过
- [ ] 7.5 实现完成后用 `/opsx:archive` 归档 change，把 spec delta 合入 `openspec/specs/kuship-console-app/spec.md`（留待用户决定时机执行）
