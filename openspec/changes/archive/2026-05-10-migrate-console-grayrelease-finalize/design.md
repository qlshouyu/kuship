# Design — migrate-console-grayrelease-finalize

## 路线锚点

引用 `migrate-region-coverage-roadmap` 的 "Region API 覆盖度路线" Requirement：本 change 是 **P1 #5**（`grayrelease-finalize`，等 app-install 子 change 配合），估计 method 数 **3**（路线图 design.md "推进顺序"图明确归类于"等 app-install 子 change 配合"），工作量约 1-2 天，远低于 ≤ 30 上限。归档时反向更新路线表 P1 行 + 决策 5 "推进顺序"图中对应分支为已完成。

依赖：

- **强依赖**：`add-gray-release`（2026-05-04 已归档）—— `GrayReleaseRecord` entity / `GrayReleaseStatus` 状态机 / `GrayReleaseService` / `ApisixRouteWeightUpdater` / `GrayReleaseTemplateInstaller`（stub 形态）必须先在
- **软依赖（建议在前）**：本 change 建议**先于** `migrate-console-app-install` 落地（路线图原文："建议在 `migrate-console-app-install` 之前；本子 change 只对接 region API"）—— 这是为了让 region 调用通路与本地写 stub 解耦推进，便于回归独立验证 region 通信契约
- **非依赖**：`migrate-console-cluster-extras` / `migrate-console-monitor-extras` / `migrate-console-build-versions` 等 P0 / P1 兄弟子 change 互不阻塞

## Region API URL 表

| method                                                    | HTTP | 路径                                                                                                          | rainbond 锚点                              |
|-----------------------------------------------------------|------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `createAppGrayRelease(rn, tn, regionAppId, body)`         | POST | `/v2/tenants/{namespace}/apps/{region_app_id}/gray_release`                                                  | `regionapi.py:2937-2943 create_app_gray_release` |
| `updateAppGrayRelease(rn, tn, regionAppId, body)`         | PUT  | `/v2/tenants/{namespace}/apps/{region_app_id}/gray_release`                                                  | `regionapi.py:2945-2951 update_app_gray_release` |
| `operateAppGrayRelease(rn, tn, regionAppId, ns, opMethod)` | PUT  | `/v2/tenants/{namespace}/apps/{region_app_id}/operate_gray_release?namespace={ns}&app_id={region_app_id}&operation_method={opMethod}` | `regionapi.py:2953-2960 operate_app_gray_release` |

（可选 / 决策 4 不实现）`get_app_gray_release` GET `/v2/tenants/{namespace}/apps/{region_app_id}/gray_release?namespace={ns}&app_id={region_app_id}&component_id={cid}` —— 本 change **不**新增对应 region method 与 console URL；`GrayReleaseInfoController` 现有端点直读本地 `GrayReleaseRecord` 已能覆盖 UI "判断按钮态" 场景。

`tenant_name` 路径段：rainbond `regionapi.py:2940/2948/2956` 用的是 `tenant_region.region_tenant_name`（即 namespace），与 `migrate-console-cluster-extras.getResources` / `migrate-console-helm-release` 一致。kuship 端在 `GrayReleaseOperationsImpl` 内部从 `TenantsRepository.findByTenantName(...).getNamespace()` 取 namespace，缺失时 fallback 为 `tenant_name`（与已有套路保持一致）。

`region_app_id` 路径段：是 rainbond region 端的 app id（与 console 本地 `service_group.ID` 通过 `region_app` 表映射）；本 change 不新建 `region_app` 映射查询逻辑，调用方（`GrayReleaseTemplateInstaller`）传入已经查询好的 `region_app_id`（rainbond Python 同样在 service 层先查 `region_app_repo.get_region_app_id(region_name, app_id)` 再调 region method）。

`operation_method` query 参数：rainbond Python 端约定为 `start` / `stop` / `restart` / `rollback` / `promote` 等动词（与 region Go 端保持一致），本 change 接口签名透传 `String operationMethod`，由 caller 传入；语义见决策 3。

## Controller 路径锚点

本 change **不新建任何 controller 类**，`GrayReleaseInfoController` 已在 `add-gray-release` 落地。仅在 `GrayReleaseService` / `GrayReleaseTemplateInstaller` 内部接线 `GrayReleaseOperations`。

| 既有 Controller / endpoint                                                                                       | 改造点                                              | rainbond 锚点                                  |
|------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|-----------------------------------------------|
| `POST /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-release`（在 `OpenApiAppController`） | 通过 `GrayReleaseService.createGrayRelease` 间接触发 `GrayReleaseTemplateInstaller` 调 region `createAppGrayRelease` | rainbond Python `OpenApiGrayReleaseView.post`（OpenAPI v1） |
| `PUT /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-ratio`                                 | `GrayReleaseService.updateGrayRatio` 在 ratio 变更后调 region `updateAppGrayRelease`，与既有 ApisixRoute 权重更新串联 | rainbond Python `OpenApiGrayRatioView.put`     |
| `POST /openapi/v1/teams/{team_id}/regions/{region_name}/apps/{app_id}/gray-rollback`                             | `GrayReleaseService.rollback` 触发 `GrayReleaseTemplateInstaller.uninstallGrayServiceGroup` → 调 region `operateAppGrayRelease(operationMethod="rollback")` | rainbond Python `OpenApiGrayRollbackView.post` |
| `POST /console/teams/{team_name}/regions/{region_name}/apps/{app_id}/gray-release-info`（`GrayReleaseInfoController`）| **不变**，仅读本地 record；不调 region GET                                                                       | rainbond Python `GrayReleaseInfoView.post`     |

trailing slash 兼容沿用既定规则（既有端点已有 `path` + `path/` 双声明，本 change 不改动）。

## 决策 1 — 与 `add-gray-release` change 的边界

`add-gray-release` 已落地的不动：

- `GrayReleaseRecord` / `GrayReleaseStatus` entity / enum
- `GrayReleaseRecordRepository`（含 8 个 finder）
- `GrayReleaseService` 状态机（ACTIVE → COMPLETED / CANCELLED）+ ratio 校验 + tenant / app 归属校验 + 并发 ACTIVE 拒绝（409）
- `ApisixRouteWeightUpdater`（走 `GatewayOperations.apiGatewayProxy` 改 ApisixRoute 权重）
- 5 个 endpoint 契约（4 OpenAPI v1 + 1 console，URL / 鉴权 / 响应形状不变）
- `kuship.gray-release.skip-apisix-update` / `kuship.gray-release.max-active-per-app` 配置项语义不变

本 change **新增**的：

- `GrayReleaseOperations` 接口（3 method）+ `GrayReleaseOperationsImpl @Primary @Service`
- `GrayReleaseTemplateInstaller` 内部多注入 `GrayReleaseOperations` + `TenantsRepository`，`installGrayServiceGroup` / `uninstallGrayServiceGroup` 在生成合成 id 之**外**追加 region 调用
- `GrayReleaseService.updateGrayRatio` 在 ApisixRoute 权重切换后追加 `updateAppGrayRelease` 调用（事务内，region 失败回滚）

边界规则（**职责分层**）：

| 组件                            | 负责                                                                          |
|---------------------------------|-------------------------------------------------------------------------------|
| `GrayReleaseService`            | 状态机推进、参数校验、record CRUD、调用编排                                  |
| `GrayReleaseTemplateInstaller`  | 模板实例化（region 创建 / 卸载 service group + 本地批量 INSERT 仍 stub）     |
| `GrayReleaseOperations`         | **本 change 新增**，对 region 端发 create / update / operate 三个动词        |
| `ApisixRouteWeightUpdater`      | 通用 ApisixRoute 权重切换（流量数据面），与 region gray_release 命令面互补   |

**`GrayReleaseTemplateInstaller` 不直接拼 region URL**；URL 拼装收口到 `GrayReleaseOperationsImpl`（与项目其他子 change 同套路：业务层只持接口）。

## 决策 2 — 与未来 `migrate-console-app-install` 子 change 的边界（**最关键决策**）

路线图原文："本子 change 只对接 region API，template 实例化的本地写仍走 stub 直至 install 子 change 落地"。

本决策把"本地写仍 stub"的范围**显式列出**：

| 行为                                                                                            | 本 change 是否落地 | 落地节点                                  |
|-------------------------------------------------------------------------------------------------|--------------------|-------------------------------------------|
| 调 region `createAppGrayRelease` 创建灰度对象                                                   | ✅                 | 本 change                                 |
| 解析 region 响应回填 `original_service_id` / `gray_service_id` / `*_upgrade_group_id`           | ✅                 | 本 change                                 |
| 写 `gray_release_record` 行                                                                     | ✅（已 in）        | `add-gray-release` 已落地                 |
| **本地批量 INSERT `tenant_service`（灰度组件）**                                                | ❌（仍 stub）       | `migrate-console-app-install` 接管        |
| **本地批量 INSERT `service_group_relation`**                                                    | ❌（仍 stub）       | `migrate-console-app-install` 接管        |
| **本地批量 INSERT `tenant_service_env_var` / `tenant_services_port` / `tenant_service_volume`** | ❌（仍 stub）       | `migrate-console-app-install` 接管        |
| 调 region `operateAppGrayRelease(operation_method="rollback")`                                  | ✅                 | 本 change                                 |
| 本地软删除 stub 创建的"灰度 service group"行                                                    | ❌（无行可删）      | `migrate-console-app-install` 接管        |
| 调 region `updateAppGrayRelease` 同步新 ratio 给 region 灰度对象                                | ✅                 | 本 change                                 |

本 change 落地后，`GrayReleaseTemplateInstaller.installGrayServiceGroup` 实际行为：

```java
// pseudo
public Result installGrayServiceGroup(...) {
    String namespace = tenantsRepo.findByTenantName(tenantName).map(Tenants::getNamespace).orElse(tenantName);
    Map<String, Object> body = Map.of(
        "template_id", templateId,
        "version", version,
        "market_name", marketName,
        "install_from_cloud", installFromCloud
        // ... 其他 region 端期望字段，对齐 rainbond Python region body
    );
    Map<String, Object> regionResp = grayReleaseOps.createAppGrayRelease(regionName, tenantName, regionAppId, body);
    // 解析 regionResp 提取 original/gray service_id + upgrade_group_id
    String origSvcId = (String) regionResp.get("original_service_id"); // 由 region 端实际生成
    String graySvcId = (String) regionResp.get("gray_service_id");
    Integer origUgId = ((Number) regionResp.get("original_upgrade_group_id")).intValue();
    Integer grayUgId = ((Number) regionResp.get("gray_upgrade_group_id")).intValue();

    // 仍 stub 的本地写
    log.warn("[GrayRelease][stub] local service_group write bypassed; tenant={} app={} pending migrate-console-app-install",
            tenantId, appId);

    return new Result(origSvcId, /* originalServiceCname */ "original",
                      graySvcId, "gray-" + (templateId == null ? "v1" : templateId),
                      origUgId, grayUgId);
}
```

**降级阀**：当 region 调用失败 / `kuship.gray-release.skip-region-template-install=true` 时，回退到 `add-gray-release` 原有的合成 id 路径，保持集成测试可在无 region 环境下跑。新增配置项 `kuship.gray-release.skip-region-template-install`（默认 `false`）由本 change 引入。

## 决策 3 — operate 的多动作语义（合一签名）

`operate_app_gray_release` 在 rainbond region 端是一个动词路径 + query 参数 `operation_method`，五种语义合一：

| operation_method | 语义                                                | 本 change 是否暴露 controller endpoint |
|------------------|-----------------------------------------------------|----------------------------------------|
| `start`          | 启动灰度 deployment（从 stopped 到 running）        | ❌（无对应 console endpoint）          |
| `stop`           | 停止灰度 deployment（保留对象，不流量）              | ❌                                     |
| `restart`        | 重启灰度 deployment（重新拉副本）                   | ❌                                     |
| `rollback`       | 回滚（卸载灰度 service group，权重归 0）             | ✅（`gray-rollback` endpoint 已在）    |
| `promote`        | 推进（ratio=100 + 灰度提升为正式版本）               | ❌（`add-grayrelease-promote-endpoint` 独立 hardening） |

接口签名 `operateAppGrayRelease(regionName, tenantName, regionAppId, namespace, operationMethod)` 不在 method 数上区分动作（动作是 query 参数），调用方传字符串。**本 change 仅在 `GrayReleaseTemplateInstaller.uninstallGrayServiceGroup` 内部使用 `operationMethod="rollback"` 一种**；其他四种动作的 controller endpoint 由独立 hardening 提案承载，本 change 提前打通调用通路（避免后续端点新增重复写 region URL 拼装逻辑）。

**幂等性**：rainbond region 端 `operate_app_gray_release` 自身幂等（重复 stop / rollback 不报错），本 change 不在客户端额外去重。

## 决策 4 — `get_app_gray_release` 的归属

rainbond `regionapi.py:2928-2935 get_app_gray_release` 是只读查询，返 region 端 K8s 实时状态（灰度 deployment 副本数 / 健康度 / 流量）。本 change **不**新增 region 调用 method 与对应 console URL，理由：

1. UI 当前仅按本地 `GrayReleaseRecord.status` 判定按钮态（ACTIVE / COMPLETED / CANCELLED 三态），`GrayReleaseInfoController.grayReleaseInfo` 已能覆盖
2. region 实时状态查询属"监控视角"，与 `migrate-console-monitor-extras` / `migrate-console-resource-center` 关注点重合，更适合在那两个子 change 体系内统一暴露
3. method 数从 4 降到 3，与路线图 design.md "≈3 method" 描述一致

如未来 UI 需要展示"灰度 deployment 副本健康度"，新增独立 hardening 提案 `add-grayrelease-runtime-status`（届时复用本 change `GrayReleaseOperations` 接口扩 1 method）。

## 决策 5 — 错误透传与降级

3 个 region method 全走标准透传：region 错误抛 `RegionApiException`（含 `httpStatus` + `code` + `msgShow`），由 `GlobalExceptionHandler` 自动映射为 general_message 形状响应。HTTP 状态码 = region httpStatus，与 `align-error-http-status` 既定规则一致。

**降级路径**（与决策 2 配合）：

- `kuship.gray-release.skip-region-template-install=true` 时，`GrayReleaseTemplateInstaller` 不调 `GrayReleaseOperations`，回退到 `add-gray-release` 原合成 id 行为；用于本 change 集成测试 / 无 region 环境
- `kuship.gray-release.skip-apisix-update=true` 时，跳过 `ApisixRouteWeightUpdater`（该开关由 `add-gray-release` 引入，本 change 不动）

**事务边界**：`GrayReleaseService.createGrayRelease` 仍 `@Transactional` 包 record 写入；`GrayReleaseTemplateInstaller.installGrayServiceGroup` 调 region 在事务内**先于** record INSERT。region 失败抛异常 → 事务自动回滚 → record 不落库 + ApisixRoute 不切权重（如此前 add-gray-release 的串联顺序：先 install 后 apisix 后 record）。`updateGrayRatio` 同样 region 调用在事务内、ratio 写入 record 之前。

`rollback` 路径下保留 `add-gray-release` 既定行为：region 调用失败仍把 record.status 写 CANCELLED + WARN 日志（避免 record 卡 ACTIVE 阻塞下次 create）；本 change 在 `uninstallGrayServiceGroup` 调 region 失败时同样 WARN 不抛，与 `ApisixRouteWeightUpdater` 行为对齐。

## 决策 6 — 测试用 region 端真实数据 vs Mock

`GrayReleaseOperationsImplTest` 走 `MockRestServiceServer`（与 `TenantOperationsImplTest` / `HelmOperationsImplTest` / `ClusterOperationsImplTest` 同模式）：

- `createAppGrayRelease` 1 happy + 1 region 5xx 透传 + 1 namespace fallback（namespace 缺失走 tenant_name）
- `updateAppGrayRelease` 1 happy + 1 region 4xx 透传
- `operateAppGrayRelease` 1 happy（operation_method=rollback）+ 1 query 参数拼装断言（namespace / app_id / operation_method 三个 key）

集成测试 `GrayReleaseFinalizeIntegrationTest`：

- `@SpringBootTest + @ActiveProfiles({"local","contract-test"})`
- `@MockitoBean GrayReleaseOperations` 替换 `@Primary` impl，`@MockitoBean GatewayOperations`（让 `ApisixRouteWeightUpdater` 不发真实 region 请求）
- `kuship.gray-release.skip-region-template-install=false`（默认 false，让接线生效）

不依赖本地起 region 容器（项目既定测试规约）。

## 测试约定

集成测试覆盖（每用例必断言 `code/msg/msg_show/data.bean/data.list` 五项契约形状）：

- `createGrayRelease_happy_path`：调 OpenAPI POST `/gray-release`；`@MockitoBean` 让 `createAppGrayRelease` 返 `{original_service_id:"o1",gray_service_id:"g1",original_upgrade_group_id:1,gray_upgrade_group_id:2}`；ArgumentCaptor 断言 `body` 含 `template_id`；响应 200 + `data.bean.gray_service_id="g1"`
- `updateGrayRatio_propagates_to_region`：调 OpenAPI PUT `/gray-ratio` 改 `gray_ratio=70`；ArgumentCaptor 断言 `updateAppGrayRelease` body 含 `gray_ratio:70`；响应 200
- `rollback_invokes_operate_with_rollback_method`：调 OpenAPI POST `/gray-rollback`；ArgumentCaptor 断言 `operateAppGrayRelease(... operationMethod="rollback")`；响应 200 + record.status="cancelled"
- `region_5xx_transparently_propagates_via_RegionApiException`：mock `createAppGrayRelease` 抛 `RegionApiException(503, "region down", "集群不可用")`；断言响应 503 + msg_show="集群不可用"
- `coexistence_with_apisix_route_weight_updater`：mock `GatewayOperations.apiGatewayProxy` 返 `Map.of()`；mock `createAppGrayRelease` 返 happy；断言两个组件**同事务**调用顺序为 `installGrayServiceGroup → ApisixRouteWeightUpdater.update → record save`（用 InOrder 验证）

## 非决策（明确不做）

- **不**修改 `add-gray-release` 既定 5 个 endpoint 的 URL / 鉴权 / 响应形状
- **不**新建 `GrayReleaseRecord` 之外的本地表
- **不**实现本地 `tenant_service` / `service_group_relation` 批量 INSERT（属 `migrate-console-app-install`）
- **不**新增 `GrayReleaseTemplateInstaller` 的 stub 之外的"假 service" 行为（不写 fake `tenant_service` 行）
- **不**实现 region GET `get_app_gray_release` 透传（决策 4）
- **不**实现 `start` / `stop` / `restart` / `promote` 子动作的 controller endpoint（属未来独立 hardening 提案）
- **不**修改 `ApisixRouteWeightUpdater` 调用顺序 / body 形状
- **不**加 DB 唯一索引（属 `enforce-grayrelease-uniqueness` 独立 hardening）
- **不**引入 Redis 跨实例并发去重（属 `add-distributed-grayrelease-coordination` 独立 hardening）

## 实施期探测结果（2026-05-10 落地）

- **新增配置项 `kuship.gray-release.skip-region-template-install`**：默认 `false`（生产真实调 region），contract-test profile 在 `application-contract-test.yaml` 默认设 `true` 让既有 `GrayReleaseIntegrationTest` 7 用例继续无破跑通；region 接线由新增 `GrayReleaseOperationsImplTest` 6 单测验证
- **`installGrayServiceGroup` / `uninstallGrayServiceGroup` 签名扩展**：原签名 `(tenantId, appId, ...)` 改为 `(regionName, tenantName, tenantId, appId, regionAppId, ...)`；`uninstallGrayServiceGroup` 进一步新增 `namespace` 参数。`GrayReleaseService` 同步改造调用点：`createGrayRelease` / `rollback` 传 `team.getTenantName()` + `team.getTenantId()` + `appId`（`regionAppId` 临时复用 `appId`，TODO 注释指向 `migrate-console-app-install` 引入正式 `RegionApp` 映射查询）
- **`updateGrayRatio` 双面同步**：先调 `apisixUpdater.update(...)` 切流量（数据面），后调 `grayReleaseOps.updateAppGrayRelease(...)` 同步 region 灰度对象 desired_replicas（命令面）；`skipApisixUpdate` 与 `skipRegionTemplateInstall` 两个降级阀互不影响
- **rollback 路径降级行为**：`uninstallGrayServiceGroup` 内调 `operateAppGrayRelease(rollback)` 失败仅 WARN 不抛（与 `ApisixRouteWeightUpdater` rollback 失败行为一致：record 已要写 CANCELLED，不能让用户 rollback 卡死）
- **`GrayReleaseOperations.operateAppGrayRelease` 默认 namespace**：当 caller 传 `namespace=""` 时 impl 内部 fallback 到 `tenantsRepo.findByTenantName(tn).getNamespace()`，避免空段进 URL；query string `?namespace=&app_id=&operation_method=` 三段全部 URL encode
- **测试结果**：`GrayReleaseOperationsImplTest` 6 单测 + `GrayReleaseIntegrationTest` 7 既有集成测试零回归 = 13 用例全过；`mvn -DskipTests package` 编译通过
