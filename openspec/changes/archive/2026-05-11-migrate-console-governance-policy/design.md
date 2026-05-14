# Design — migrate-console-governance-policy

> **路线图位置**：母路线图 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.2（**P2 #2**，路线图估计 12 / 实际 9，偏差 -25%）
>
> rainbond 参照：
> - region API：`www/apiclient/regionapi.py:2319-2353`（governance 5）+ `:2572-2598`（k8s_attribute 4）
> - urls：`console/urls/__init__.py:431-435`（governance 3 URL）+ `:636-638`（k8s_attribute 2 URL）
> - views：`console/views/group.py:376-450`（4 view 类）+ `console/views/k8s_attribute.py`（2 view 类）

## 1. 双域范围

### 1.1 应用治理模式（5 region method）

| # | rainbond region method | kuship method | rainbond URL |
|---|------------------------|----------------|--------------|
| 1 | `list_governance_mode` | `listGovernanceMode(rn, tn)` | GET `/v2/cluster/governance-mode` |
| 2 | `check_app_governance_mode` | `checkAppGovernanceMode(rn, tn, regionAppId, mode)` | GET `/v2/tenants/{tn}/apps/{app_id}/governance/check?governance_mode={mode}` |
| 3 | `create_governance_mode_cr` | `createGovernanceCr(rn, tn, regionAppId, body)` | POST `/v2/tenants/{tn}/apps/{app_id}/governance-cr` |
| 4 | `update_governance_mode_cr` | `updateGovernanceCr(rn, tn, regionAppId, body)` | PUT `/v2/tenants/{tn}/apps/{app_id}/governance-cr` |
| 5 | `delete_governance_mode_cr` | `deleteGovernanceCr(rn, tn, regionAppId)` | DELETE `/v2/tenants/{tn}/apps/{app_id}/governance-cr` |

### 1.2 组件 k8s 属性（4 region method）

| # | rainbond region method | kuship method | rainbond URL |
|---|------------------------|----------------|--------------|
| 6 | `get_component_k8s_attribute` | `getK8sAttribute(rn, tn, alias, body)` | GET `/v2/tenants/{tn}/services/{alias}/k8s-attributes`（**GET with body**）|
| 7 | `create_component_k8s_attribute` | `createK8sAttribute(rn, tn, alias, body)` | POST `/v2/tenants/{tn}/services/{alias}/k8s-attributes` |
| 8 | `update_component_k8s_attribute` | `updateK8sAttribute(rn, tn, alias, body)` | PUT `/v2/tenants/{tn}/services/{alias}/k8s-attributes` |
| 9 | `delete_component_k8s_attribute` | `deleteK8sAttribute(rn, tn, alias, body)` | DELETE `/v2/tenants/{tn}/services/{alias}/k8s-attributes`（DELETE with body）|

**决策 1**：rainbond `get_component_k8s_attribute` 使用 GET with body（HTTP RFC 不推荐但 region 端实际接受）。Spring 6 RestClient 支持 `c.method(HttpMethod.GET).uri(url).contentType(JSON).body(body)`。本 change 严格按 region 端契约执行。

## 2. Controller 路径

### 2.1 `AppGovernanceModeController`

| HTTP | URL | 说明 |
|------|-----|------|
| GET | `/console/teams/{tn}/groups/{app_id}/governancemode` | 列出可用治理模式 |
| PUT | `/console/teams/{tn}/groups/{app_id}/governancemode` | 切换应用治理模式 |
| POST | `/console/teams/{tn}/groups/{app_id}/governancemode-cr` | 创建治理 CR |
| PUT | `/console/teams/{tn}/groups/{app_id}/governancemode-cr` | 更新治理 CR |
| DELETE | `/console/teams/{tn}/groups/{app_id}/governancemode-cr` | 删除治理 CR |
| GET | `/console/teams/{tn}/groups/{app_id}/governancemode/check?governance_mode={mode}` | 检查模式可行性 |

### 2.2 `ComponentK8sAttributeController`

| HTTP | URL | 说明 |
|------|-----|------|
| GET | `/console/teams/{tn}/apps/{alias}/k8s-attributes` | 列组件 k8s 属性 |
| POST | `/console/teams/{tn}/apps/{alias}/k8s-attributes` | 创建 |
| GET | `/console/teams/{tn}/apps/{alias}/k8s-attributes/{name}` | 查单条 |
| PUT | `/console/teams/{tn}/apps/{alias}/k8s-attributes/{name}` | 更新 |
| DELETE | `/console/teams/{tn}/apps/{alias}/k8s-attributes/{name}` | 删除 |

## 3. 数据模型

### 3.1 `component_k8s_attributes` 表（rainbond schema 已存在）

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | INT PK | 自增 |
| tenant_id | VARCHAR(32) | 租户 id |
| component_id | VARCHAR(32) | 组件 id（service_id）|
| name | VARCHAR(64) | 属性名（如 `nodeSelector` / `tolerations`）|
| save_type | VARCHAR(20) | yaml / json / env |
| attribute_value | TEXT | 属性内容（yaml/json string）|
| create_time / update_time | DATETIME | |

JPA Entity：`ComponentK8sAttribute`；Repository：`ComponentK8sAttributeRepository`，含 `findByComponentId(String)` / `findByComponentIdAndName(String, String)`。

### 3.2 `k8s_resources` 表（应用级 governance CR）

rainbond 用 `k8s_resources` 表存应用级的所有 k8s 资源（含 governance CR、kubernetes_service、其它 yaml）。**决策 2**：governance CR 复用此表（kind = `governance`），但本 change 不重构其它 kind 的字段；只新增 `findByAppIdAndKind(Integer, String)` query。

## 4. 接口定义

```java
// modules/application/governance/api/GovernanceModeOperations.java
public interface GovernanceModeOperations {
    List<Map<String, Object>> listGovernanceMode(String regionName, String tenantName);
    Map<String, Object> checkAppGovernanceMode(String regionName, String tenantName, String regionAppId, String governanceMode);
    Map<String, Object> createGovernanceCr(String regionName, String tenantName, String regionAppId, Map<String, Object> body);
    Map<String, Object> updateGovernanceCr(String regionName, String tenantName, String regionAppId, Map<String, Object> body);
    Map<String, Object> deleteGovernanceCr(String regionName, String tenantName, String regionAppId);
}

// modules/application/k8sattr/api/K8sAttributeOperations.java
public interface K8sAttributeOperations {
    List<Map<String, Object>> getK8sAttribute(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);
    Map<String, Object> createK8sAttribute(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);
    Map<String, Object> updateK8sAttribute(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);
    Map<String, Object> deleteK8sAttribute(String regionName, String tenantName, String serviceAlias, Map<String, Object> body);
}
```

## 5. 业务流程要点

### 5.1 governance mode 切换（PUT）

1. 校验目标 mode 在 `listGovernanceMode` 返回的可用集合内
2. 调 `checkAppGovernanceMode` 检查可行性（如 mesh 是否已安装）；返回 412 时透传错误
3. 通过后切换：写本地 `app.governance_mode` + 调 `createGovernanceCr` / `updateGovernanceCr` / `deleteGovernanceCr` 落地 region CR

### 5.2 k8s_attribute 写入（POST/PUT）

1. 本地 INSERT/UPDATE `ComponentK8sAttribute`
2. 调 region create/update method 同步到 region 端
3. region 失败 → 本地回滚（@Transactional）

### 5.3 k8s_attribute 删除

1. 调 region delete
2. 本地 DELETE
3. region 404 兼容仍删本地

## 6. 错误处理

- region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- governance check 412（mesh 不可用）→ 透传 412 + msg_show
- k8s_attribute name 重名 → POST 返回 409 + msg `"属性名已存在"`

## 7. 权限

- governance mode 切换：要求 `@RequirePerm(PermCode.APP_OVERVIEW)`
- k8s_attribute CRUD：要求 `@RequirePerm(PermCode.APP_OVERVIEW_OTHER_SETTING)`（与 rainbond 一致）

## 8. 测试

- `GovernanceModeOperationsImplTest`（5 method × 1 happy + 1 错误）= 10 用例
- `K8sAttributeOperationsImplTest`（4 method × 1 happy + 1 错误，含 GET with body 验证）= 8 用例
- `GovernancePolicyIntegrationTest`（11 endpoint × happy/error）= ~16 用例

## 9. 实施期决策（占位段）

待 apply 阶段补：
- governance CR 的 jsonschema 校验是否完全跳过（保留 stub 或简化）
- `k8s_resources` 表与 helm-release / helm-app-yaml 复用是否冲突
- GET with body 的 Spring 6 真实可用性（如不可用，改 query string 兜底）
