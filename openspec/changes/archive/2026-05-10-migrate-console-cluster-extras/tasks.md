# Tasks — migrate-console-cluster-extras

## 1. 校验既有接口与依赖

- [x] 1.1 确认 `infrastructure/region/api/ClusterOperations.java` 仍持有 5 个 default unsupported method（`getResources` / `getClusterInfo` / `getClusterEvents` / `getNodes` / `getNodeDetail`）；如签名已被其他 change 修改，先与作者对齐
- [x] 1.2 确认 `cn.kuship.console.modules.region.api.ClusterOperationsImpl`（`@Primary`）已实现既有 8 method（`migrate-console-region-cluster` 落地的 license / features / namespaces 等），本 change 在此类上 **追加** 5 个 override，不新建文件
- [ ] 1.3 探测 region 端：用本地 8080 console 已注入的 RegionClient，curl 各 region 真实节点验证 5 个 URL（**实施期推迟到 task §7 联动验证**：实施环境无在线 region 实例，先按 rainbond Python `get_cluster_resource` 默认路径实现，404 时降级到本地 `region_info` entity）：
  - `GET /v2/tenants/<ns>/resources?enterprise_id=<eid>` → 期望 200 + bean 含 `cpu/memory/disk` 等
  - `GET /v2/cluster/info` → 验证 200 或 404，404 时切到 `GET /v2/cluster`
  - `GET /v2/cluster/events` → 验证 200 或 404
  - `GET /v2/cluster/nodes` → 期望 200 + list
  - `GET /v2/cluster/nodes/<node_name>/detail` → 期望 200 + bean
- [x] 1.4 把 1.3 的探测结果写进 `design.md` 决策 3（getClusterInfo 路径推断）下方"探测结果"小节，明确最终路径（实施期写入"实施期探测结果"段，标注真实 region 联动验证留 task §7）

## 2. 实现 ClusterOperationsImpl 5 method

- [x] 2.1 注入新依赖到 `ClusterOperationsImpl`：`TenantsRepository`（用于 `getResources` 取 namespace）+ `RegionInfoEntityRepository`（getClusterInfo 404 降级用）+ `tools.jackson.databind.ObjectMapper`（events/nodes 响应解析用）
- [x] 2.2 实现 `getResources(regionName, tenantName, enterpriseId)`：
  - `Tenants tenant = tenantsRepo.findByTenantName(tenantName).orElseThrow(() -> ServiceHandleException(404, "team not found", "团队不存在"))`
  - `String namespace = tenant.getNamespace() != null ? tenant.getNamespace() : tenant.getTenantName()`（与 helm-release 同样 fallback）
  - URL = `/v2/tenants/{namespace}/resources?enterprise_id={enterpriseId}`
  - GET → `processor.extractBean(resp, Map.class, ...)`
- [x] 2.3 实现 `getClusterInfo(regionName)`：
  - URL = `/v2/cluster/info`（rainbond Python `get_cluster_resource(rn, "info")` 默认路径）
  - catch `RegionApiException` httpStatus==404 时降级为读本地 `region_info` entity（注入 `RegionInfoEntityRepository`），返 `region_name/region_alias/url/wsurl/tcpdomain/httpdomain/status/scope/provider/region_type` 字段子集；其他错误透传
- [x] 2.4 实现 `getClusterEvents(regionName, body)`：
  - `Map<String, Object> body` → query string：TreeMap 字典序排序遍历，URL encode key+value，按 `&` 拼接；空 value 跳过
  - URL = `/v2/cluster/events` + (queryString 非空时 `?` + queryString)
  - GET 透传，用 `processor.checkStatus` 拿 root JsonNode 后取 `data` 整体（含 `bean` / `list`）
- [x] 2.5 实现 `getNodes(regionName)`：
  - URL = `/v2/cluster/nodes`
  - GET 透传，返 `Map`（含 list 字段）
- [x] 2.6 实现 `getNodeDetail(regionName, nodeName)`：
  - URL = `/v2/cluster/nodes/{node_name}/detail`，`node_name` URL encode（防止节点名含 `.` 或其他字符）
  - GET 透传，返 `Map`
- [x] 2.7 单测 `ClusterOperationsImplExtraTest`：10 用例全过
  - 5 method 各 1 happy + 1 region 5xx 透传 / 404 降级
  - `MockRestServiceServer` 断言 URL 与 query string 形状
  - `getResources` 断言 namespace 来自 Tenants（含 namespace 缺失时 fallback tenant_name 用例）+ team 不存在 → 404
  - `getClusterEvents` 断言 query string 编码 `?since=1h&type=warning`（TreeMap 字典序）+ 空 body 不带 query
  - `getClusterInfo` 含 404 降级到本地 region_info 用例

## 3. 新建 4 个 controller

- [x] 3.1 新建 `modules/region/controller/cluster/ClusterInfoController.java`：
  - `@GetMapping({"/console/enterprise/{enterprise_id}/regions/{region_name}/info", ".../info/"})`
  - 调 `clusterOps.getClusterInfo(regionName)`，return Map
  - 选用 `@RequireEnterpriseAdmin`（视 rainbond 无明确 console URL 对应权限码，使用 enterprise admin 身份保护）
- [x] 3.2 新建 `ClusterEventsController.java`：
  - `@GetMapping({"/console/enterprise/{eid}/regions/{rn}/cluster-events", ".../"})`
  - `@RequestParam Map<String, String> queryParams` 直接接所有 query 参数
  - 调 `clusterOps.getClusterEvents(rn, new HashMap<>(queryParams))`
- [x] 3.3 新建 `ClusterNodesController.java`：
  - `@GetMapping({"/console/enterprise/{eid}/regions/{rn}/nodes", ".../nodes/"})` 列表
  - `@GetMapping({"/console/enterprise/{eid}/regions/{rn}/nodes/{node_name}", ".../"})` 详情
  - `node_name` 路径变量直接 `@PathVariable`（Spring 默认接受 `.`，与 rainbond `[\w\-.]+` 等价）
  - 注释标"扩展点：cluster-nodes 子 change 在本 controller 加 action / labels / taints / container 端点"
- [x] 3.4 新建 `TenantResourcesController.java`：
  - `@GetMapping({"/console/teams/{team_name}/resources", "/console/teams/{team_name}/resources/"})`
  - `@RequestParam String region_name` + `@RequestParam(required = false) String enterprise_id`（缺省从 RequestContext 拿）
  - 调 `clusterOps.getResources(regionName, teamName, enterpriseId)`
  - `@RequirePerm(PermCode.TEAM_REGION_DESCRIBE)`
  - **解冲突**：删除 `MiscOtherController.teamResources` 占位（与本路径冲突），由本 controller 接管 region 透传职责
- [x] 3.5 全部 controller 路径变量统一 snake_case，trailing slash 双声明，不显式 ApiResult 包（advice 自动）

## 4. SecurityConfig（如需）

- [x] 4.1 5 个 endpoint 全部走默认 JWT 鉴权链，**无需** 加 permitAll；本 change 不修改 SecurityConfig

## 5. 集成测试

实施合并到一个 `ClusterExtrasIntegrationTest` 类（@SpringBootTest + @MockitoBean ClusterOperations + JdbcTemplate seed），降低 fixture 重复成本。6 用例全过：

- [x] 5.1 ClusterInfo happy path：`getClusterInfo` 入参 regionName，响应 200 + general_message + `data.bean.version` 字段
- [x] 5.2 ClusterEvents query 透传：发 `?type=warning&since=1h`，ArgumentCaptor 断言 body 含两个 key
- [x] 5.3 ClusterNodes：
  - 列表 happy
  - 节点名 `worker-01.example.com` 详情 happy（断言 . 字符不丢）
  - region 5xx 透传：mock 抛 `RegionApiException(503,...)`，断言响应 503 + msg_show=集群不可用
- [x] 5.4 TenantResources happy：team_name 路径解析 + enterprise_id query 透传，verify clusterOps.getResources(REGION, TEAM, ENT)

## 6. 文档与归档

- [x] 6.1 更新 `kuship-console/CLAUDE.md` 在"集群管理（migrate-console-region-cluster）"段后追加"集群基础信息透传（migrate-console-cluster-extras）"段：列 4 controller、5 region method 路径、与 cluster-nodes 子 change 的扩展点边界、与 MiscOtherController.teamResources 占位的冲突解决；同步更新接口表 ClusterOperations 行标 13/13 完成
- [ ] 6.2 路线图 `migrate-region-coverage-roadmap` 的 Requirement 表中把 "migrate-console-cluster-extras" 行标注为已完成（归档时执行）
- [x] 6.3 记录 1.3 探测结果的最终 region URL 到 `design.md` 决策 3"实施期探测结果"小节，便于后续 cluster-nodes / resource-center 子 change 复用

## 7. 编译 / 重启 / 联动验证

- [x] 7.1 `cd kuship-console && mvn -DskipTests package` 通过（2026-05-10 实施期）；同时 `mvn test` 在 cluster-extras 范围内 16 用例全过（10 单测 + 6 集成）
- [ ] 7.2 重启 console；`curl -s -H "Authorization: GRJWT $TOKEN" "http://localhost:8080/console/enterprise/{eid}/regions/rainbond/nodes" | jq .` 返 200 + `data.list`（**需用户本地起 console + region 后联动**）
- [ ] 7.3 `curl ... /console/teams/default/resources?region_name=rainbond` 返 200 + `data.bean.cpu/memory/...`（**需用户联动**）
- [ ] 7.4 `curl ... /console/enterprise/{eid}/regions/rainbond/info` 返 200，根据 1.3 探测结果可能是 region 实时数据或本地降级（**需用户联动**）
- [ ] 7.5 不存在的节点详情 `/nodes/no-such-node` → 404 透传 region 错误，本地不缓存（**需用户联动**）
