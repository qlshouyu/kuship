# Design — migrate-console-service-labels

> **路线图位置**：母路线图 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.4（**P2 #4**，估计 4 method）
>
> rainbond 参照：
> - region API：`www/apiclient/regionapi.py:337-388`（4 method：`get_region_labels` / `addServiceNodeLabel` / `deleteServiceNodeLabel` / `update_service_state_label`）
> - service：`console/services/app_config/label_service.py`（150 行）
> - urls：`console/urls/__init__.py:759-762`（2 个 URL）
> - views：`console/views/app_config/app_label.py`（4 个 view 类：AppLabelView GET/POST/DELETE + AppLabelAvailableView GET）

## 1. 范围（4 region method + 4 controller endpoint）

| # | rainbond region method | kuship `ServiceLabelOperations` method | rainbond URL | kuship controller endpoint |
|---|------------------------|-----------------------------------------|--------------|----------------------------|
| 1 | `get_region_labels` | `listRegionLabels(rn, tn)` | GET `/v2/resources/labels` | GET `/console/teams/{tn}/apps/{alias}/labels/available` |
| 2 | `addServiceNodeLabel` | `addServiceNodeLabel(rn, tn, alias, body)` | POST `/v2/tenants/{tn}/services/{alias}/label` | POST `/console/teams/{tn}/apps/{alias}/labels`（**先本地写，后调 region**）|
| 3 | `deleteServiceNodeLabel` | `deleteServiceNodeLabel(rn, tn, alias, body)` | DELETE `/v2/tenants/{tn}/services/{alias}/label`（DELETE with body）| DELETE `/console/teams/{tn}/apps/{alias}/labels` |
| 4 | `update_service_state_label` | `updateServiceStateLabel(rn, tn, alias, body)` | PUT `/v2/tenants/{tn}/services/{alias}/label` | （内部用，由 OS label 切换流程调用，**不暴露独立 endpoint**）|

**决策 1**：第 4 个 method（state label）按 rainbond 实践仅供 console 内部调用（OS 切换 / 有状态/无状态切换），本 change 不暴露独立 controller endpoint，但保留接口供后续调用方使用。

**决策 2**：rainbond 还有 5 个 method（`add_service_state_label` POST + `set_service_os_label` console 内部 + 等），本 change 范围限于路线图 4 method 估计；其它 method 留给 hardening。

## 2. 数据模型

### 2.1 `service_labels` 表（rainbond schema 已存在；kuship-console 复用）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | INT PK | 自增 |
| tenant_id | VARCHAR(32) | 租户 id |
| service_id | VARCHAR(32) | 组件 id |
| label_id | VARCHAR(32) | 标签 id（引用 region 端 label） |
| region | VARCHAR(30) | 区域名 |
| create_time | DATETIME | 创建时间 |

JPA Entity：`TenantServiceLabel`（`@Entity @Table(name = "service_labels")`）。
Repository：`TenantServiceLabelRepository` extends `JpaRepository<TenantServiceLabel, Integer>`，含 `findByServiceId(String)` / `findByServiceIdAndLabelId(String, String)` / `deleteByServiceIdAndLabelId(String, String)`。

### 2.2 `labels` 表（可选；查询缓存）

rainbond 有 `labels` 表存 label 元数据（label_name / label_alias / category），但实际可用 label 来自 region API 实时查询。**决策 3**：本 change SHALL NOT 落地 `labels` 本地表，所有 label 元数据从 region 实时拉取（`listRegionLabels`），减少同步成本。

## 3. 接口

### 3.1 `ServiceLabelOperations`（业务自治）

```java
package cn.kuship.console.modules.application.api;

public interface ServiceLabelOperations {

    /** 列出 region 端所有可用 label（企业级，按 region 集合） */
    List<Map<String, Object>> listRegionLabels(String regionName, String tenantName);

    /** 给组件添加 node label。body: {"label_ids": ["x", "y"]} */
    Map<String, Object> addServiceNodeLabel(String regionName, String tenantName,
                                            String serviceAlias, Map<String, Object> body);

    /** 删除组件 node label。body: {"label_ids": ["x"]}（DELETE with body） */
    Map<String, Object> deleteServiceNodeLabel(String regionName, String tenantName,
                                               String serviceAlias, Map<String, Object> body);

    /** 更新组件有无状态 label（state label）。body: {"label_ids": ["x"]} */
    Map<String, Object> updateServiceStateLabel(String regionName, String tenantName,
                                                String serviceAlias, Map<String, Object> body);
}
```

### 3.2 Impl 要点（ServiceLabelOperationsImpl @Primary）

- 模式：`clientFactory.getClient(rn, "")` + `RegionApiSupport.exchange(...)` + `processor.checkStatus / extractBean`
- DELETE with body：用 `c.method(HttpMethod.DELETE).uri(url).contentType(JSON).body(body)`
- `listRegionLabels` 返回 List：`processor.checkStatus(resp, ...)` 后从 `data.list` 提取（参考 `LangVersionOperations` 的实现模式）

## 4. Controller（AppLabelController）

```java
@RestController
public class AppLabelController {
    // 注入 ServiceLabelOperations + TenantServiceLabelRepository + TenantsRepository + TenantServiceRepository

    @GetMapping("/console/teams/{team_name}/apps/{service_alias}/labels")
    public ApiResult listServiceLabels(...) {
        // 列本地 service_labels（含 label_id）；不调 region；UI 自行用 available 拼 label_alias
    }

    @PostMapping("/console/teams/{team_name}/apps/{service_alias}/labels")
    @Transactional
    public ApiResult addServiceLabels(...) {
        // body: {"label_ids": ["x", "y"]}
        // 1. 本地批量写 TenantServiceLabel（去重）
        // 2. 调 region addServiceNodeLabel；失败回滚本地写
    }

    @DeleteMapping("/console/teams/{team_name}/apps/{service_alias}/labels")
    @Transactional
    public ApiResult deleteServiceLabel(...) {
        // body: {"label_id": "x"}
        // 1. 调 region deleteServiceNodeLabel
        // 2. 本地删 TenantServiceLabel
    }

    @GetMapping("/console/teams/{team_name}/apps/{service_alias}/labels/available")
    public ApiResult listAvailableLabels(...) {
        // 调 listRegionLabels 实时拉 region；UI 拼按钮列表
    }
}
```

## 5. 事务边界

- **添加**：先本地 INSERT 后 region POST；事务内执行；region 失败 → 抛 `RegionApiException`，事务回滚本地 INSERT
- **删除**：先 region DELETE 后本地 DELETE；region 失败 → 抛异常本地不删（避免脏数据）；region 404（label 已不存在）兼容仍删本地
- **可用列表 / 列组件 label**：只读，无事务

## 6. 错误处理

- region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- `addServiceNodeLabel` body 中的 `label_ids` 为空时 SHALL 返回 400 `"label_ids is empty"`，不调 region
- 同名 label 重复添加 SHALL 静默幂等（本地 INSERT IGNORE 风格 + region 调用兼容）

## 7. 测试

- `ServiceLabelOperationsImplTest`（单测）：4 method × 1 happy + 1 错误 = 8 用例
- `ServiceLabelIntegrationTest`（@SpringBootTest）：4 endpoint × happy/error = ~8 用例

## 8. 实施期决策（占位段）

待 apply 阶段补：
- region body shape 真相（`label_ids` array vs single `label_id`）
- DELETE with body 的 Spring 6 RestClient 行为是否有 corner case
- `update_service_state_label` 是否真的不暴露独立 endpoint，还是 OS 切换流程需要
