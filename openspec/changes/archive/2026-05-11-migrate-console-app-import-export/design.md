# Design — migrate-console-app-import-export

> **路线图位置**：母路线图 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.1（**P2 #1**，22 method，最大子 change）
>
> rainbond 参照：
> - region API：`www/apiclient/regionapi.py:1538-1670`（19 import/export/upload）+ `:2039-2065`（3 yaml）+ `:704`（1 helm chart resource）
> - urls：`console/urls/__init__.py:348-365 + 1027-1060 + 363`
> - views：`console/views/center_pool/app_import.py` + `app_export.py` + `console/views/yaml_resource.py`
> - service：`console/services/app_import_and_export_service.py`（700+ 行业务编排）

## 1. 范围分段

本 change 含 22 method，分 3 大段 6 小段：

### 1.1 App Export 段（2 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 1 | `export_app` | `exportApp(rn, eid, body)` | POST `/v2/app/export` |
| 2 | `get_app_export_status` | `getExportStatus(rn, eid, eventId)` | GET `/v2/app/export/{event_id}` |

### 1.2 App Import 段（10 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 3 | `import_app_2_enterprise` | `importApp2Enterprise(rn, eid, body)` | POST `/v2/app/import` |
| 4 | `import_app` | `importApp(rn, tn, body)` | POST `/v2/app/import` |
| 5 | `get_app_import_status` | `getImportStatus(rn, tn, eventId)` | GET `/v2/app/import/{event_id}` |
| 6 | `get_enterprise_app_import_status` | `getEnterpriseImportStatus(rn, eid, eventId)` | GET `/v2/app/import/ids/{event_id}` |
| 7 | `get_enterprise_import_file_dir` | `getEnterpriseImportFileDir(rn, eid, eventId)` | GET `/v2/app/import/ids/{event_id}` (with body) |
| 8 | `get_import_file_dir` | `getImportFileDir(rn, tn, eventId)` | GET `/v2/app/import/{event_id}` (with body) |
| 9 | `delete_enterprise_import` | `deleteEnterpriseImport(rn, eid, eventId)` | DELETE `/v2/app/import/ids/{event_id}` |
| 10 | `delete_import` | `deleteImport(rn, tn, eventId)` | DELETE `/v2/app/import/{event_id}` |
| 11 | `create_import_file_dir` | `createImportFileDir(rn, tn, eventId)` | POST `/v2/app/import/{event_id}` |
| 12 | `delete_enterprise_import_file_dir` | `deleteEnterpriseImportFileDir(rn, eid, eventId)` | DELETE `/v2/app/import/ids/{event_id}` |
| 13 | `delete_import_file_dir` | `deleteImportFileDir(rn, tn, eventId)` | DELETE `/v2/app/import/{event_id}` |

**注**：method #7-#13 中部分 URL 重叠（rainbond 用 GET/POST/DELETE 区分操作）。本 change 严格按 region 端 HTTP method 区分。

### 1.3 App Upload 段（4 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 14 | `create_upload_file_dir` | `createUploadDir(rn, tn, eventId)` | POST `/v2/app/upload/events/{event_id}` |
| 15 | `get_upload_file_dir` | `getUploadDir(rn, tn, eventId)` | GET `/v2/app/upload/events/{event_id}` |
| 16 | `delete_upload_file_dir` | `deleteUploadDir(rn, tn, eventId)` | DELETE `/v2/app/upload/events/{event_id}` |
| 17 | `update_upload_file_dir` | `updateUploadDir(rn, tn, eventId, componentId)` | PUT `/v2/app/upload/events/{event_id}/component_id/{component_id}` |

### 1.4 Load Tar Image 段（1 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 18 | `load_tar_image` | `loadTarImage(rn, tn, body)` | POST `/v2/app/load_tar_image` |

### 1.5 Helm Chart Resource 段（1 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 19 | `import_upload_chart_resource` | `importUploadChartResource(rn, body)` | POST `/v2/helm/import_upload_chart_resource` |

### 1.6 Yaml Resource 段（3 method）

| # | rainbond region method | kuship method | URL |
|---|------------------------|----------------|-----|
| 20 | `yaml_resource_name` | `yamlResourceName(eid, rn, body)` | GET `/v2/cluster/yaml_resource_name?eid={eid}` (with body) |
| 21 | `yaml_resource_detailed` | `yamlResourceDetailed(eid, rn, body)` | GET `/v2/cluster/yaml_resource_detailed?eid={eid}` (with body) |
| 22 | `yaml_resource_import` | `yamlResourceImport(eid, rn, body)` | POST `/v2/cluster/yaml_resource_import?eid={eid}` |

## 2. 接口拆分（6 个独立接口避免 god interface）

```java
// 1. modules/appmarket/share/export/api/AppExportOperations.java（2 method）
public interface AppExportOperations {
    Map<String, Object> exportApp(String regionName, String enterpriseId, Map<String, Object> body);
    Map<String, Object> getExportStatus(String regionName, String enterpriseId, String eventId);
}

// 2. modules/appmarket/share/import/api/AppImportOperations.java（10 method, P2 #1 主体）
public interface AppImportOperations {
    Map<String, Object> importApp2Enterprise(...);
    Map<String, Object> importApp(...);
    Map<String, Object> getImportStatus(...);
    Map<String, Object> getEnterpriseImportStatus(...);
    Map<String, Object> getEnterpriseImportFileDir(...);
    Map<String, Object> getImportFileDir(...);
    Map<String, Object> deleteEnterpriseImport(...);
    Map<String, Object> deleteImport(...);
    Map<String, Object> createImportFileDir(...);
    Map<String, Object> deleteEnterpriseImportFileDir(...);
    Map<String, Object> deleteImportFileDir(...);
}

// 3. modules/appmarket/share/upload/api/AppUploadOperations.java（4 method）
public interface AppUploadOperations { /* 4 method */ }

// 4. modules/appmarket/share/upload/api/LoadTarImageOperations.java（1 method）
public interface LoadTarImageOperations {
    Map<String, Object> loadTarImage(String regionName, String tenantName, Map<String, Object> body);
}

// 5. modules/appmarket/helm/api/HelmChartImportOperations.java（1 method, 扩展 helm 段）
public interface HelmChartImportOperations {
    Map<String, Object> importUploadChartResource(String regionName, Map<String, Object> body);
}

// 6. modules/region/yaml/api/YamlResourceOperations.java（3 method）
public interface YamlResourceOperations {
    Map<String, Object> yamlResourceName(String enterpriseId, String regionName, Map<String, Object> body);
    Map<String, Object> yamlResourceDetailed(String enterpriseId, String regionName, Map<String, Object> body);
    Map<String, Object> yamlResourceImport(String enterpriseId, String regionName, Map<String, Object> body);
}
```

## 3. 数据模型

### 3.1 `app_import_record`（apply 期 docker exec mysql DESC 验真实 schema）

预期字段集（rainbond 经验）：
- ID PK
- event_id VARCHAR(32)（事件标识）
- status VARCHAR(20)（init/uploading/importing/success/failed）
- scope VARCHAR(20)（enterprise / team）
- enterprise_id / team_name
- source_dir / file_temp_dir
- create_time / update_time

JPA Entity：`AppImportRecord`；Repository：`AppImportRecordRepository`，含 `findByEventId(String)`。

### 3.2 `app_export_record`

预期字段：ID / event_id / app_key / app_version / format / status / file_path / create_time。

**决策 1**：本 change 仅落地 entity + 基础 CRUD repository。完整 export 业务编排（如 app_template 序列化、tar.gz 打包、上传到 oss）**不在本 change 范围**，留 hardening。本 change 的 controller 在 export 时仅写 record + 透传到 region。

## 4. Controller 路径表

### 4.1 `CenterAppExportController`

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/enterprise/{eid}/app-models/export` | 触发导出 |
| GET | `/console/enterprise/{eid}/app-models/export/{event_id}/status` | 查导出状态 |

### 4.2 `EnterpriseAppImportInitController`

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/enterprise/{eid}/app-models/import` | 初始化导入事件 |

### 4.3 `CenterAppImportController`

| HTTP | URL | 说明 |
|------|-----|------|
| GET | `/console/enterprise/{eid}/app-models/import/{event_id}` | 查导入状态 |
| DELETE | `/console/enterprise/{eid}/app-models/import/{event_id}` | 取消导入 |
| GET | `/console/enterprise/{eid}/app-models/import/{event_id}/dir` | 查上传文件目录 |
| DELETE | `/console/enterprise/{eid}/app-models/import/{event_id}/dir` | 删除上传文件目录 |

### 4.4 `AppUploadController`

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/teams/{tn}/app-upload/events/{event_id}` | 创建上传目录 |
| GET / DELETE / PUT | 同 URL | 完成 / 删除 / 切换组件 |

### 4.5 `YamlResourceController`

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/teams/{tn}/resource-name` | 解析 yaml 资源名 |
| POST | `/console/teams/{tn}/resource-detailed` | 解析 yaml 资源详情 |
| POST | `/console/enterprise/{eid}/regions/{rn}/yaml-resource-import` | 导入 yaml 资源 |

### 4.6 `UploadHelmChartValueResourceController`

| HTTP | URL | 说明 |
|------|-----|------|
| POST | `/console/teams/{tn}/import_upload_chart_resource` | 导入 helm chart 资源 |

## 5. 业务流程要点（不在本 change 落地的部分）

### 5.1 完整 import 工作流（4-step）

1. **init**：POST 触发 region 创建 import event，返回 event_id
2. **upload**：UI 用 multipart 上传文件到 region storage（**不经过 console**）
3. **commit**：POST 触发 region 解析并落地组件
4. **poll**：GET 状态轮询直到 success / failed

本 change SHALL 落地 step 1 + step 4 的 console 端透传；step 2/3 直接由 UI 与 region 通信（rainbond 历史也是这么做）。

### 5.2 完整 export 工作流（3-step）

1. **trigger**：POST 触发 region export 事件
2. **poll**：GET 状态轮询
3. **download**：UI 直接到 region storage 下载（**不经过 console**）

本 change SHALL 落地 step 1 + step 2 的透传。

## 6. 错误处理

- region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- import event 不存在 → 透传 404 + msg `"导入事件不存在"`
- import 状态 `failed` → 200 + bean 含 `error_msg` 字段（不抛异常，UI 展示给用户）

## 7. 权限

- 全部 enterprise scope 端点：`@RequireEnterpriseAdmin`
- team scope 端点：`@RequirePerm(PermCode.APP_OVERVIEW_CREATE)`（与 rainbond 一致）

## 8. 测试

- `AppExportOperationsImplTest`：2 × 2 = 4 用例
- `AppImportOperationsImplTest`：10 × 1 = 10 用例（基础透传，不验复杂工作流）
- `AppUploadOperationsImplTest`：4 × 1 = 4 用例
- `LoadTarImageOperationsImplTest`：1 × 2 = 2 用例
- `HelmChartImportOperationsImplTest`：1 × 2 = 2 用例
- `YamlResourceOperationsImplTest`：3 × 2 = 6 用例
- `AppImportExportIntegrationTest @SpringBootTest`：~16 用例（覆盖 6 个 controller）

合计 ~44 单测 + ~16 集成测试 = ~60 用例

## 9. 实施期决策（占位段）

待 apply 阶段补：
- `app_import_record` / `app_export_record` 真实 schema（docker exec mysql DESC）
- region URL 重叠的 HTTP method 路由是否真实生效（GET/DELETE 同 URL）
- multipart 上传是否需要 console 端的代理（rainbond 历史不代理，本 change 沿用）
- yaml_resource_name / yaml_resource_detailed 是 GET with body 还是 POST（path 上写 GET 但 region 端 timeout 20s 暗示是上传内容）
- 与已落地 helm 段（`migrate-console-helm-release`）的 helm chart resource 接口冲突检查
