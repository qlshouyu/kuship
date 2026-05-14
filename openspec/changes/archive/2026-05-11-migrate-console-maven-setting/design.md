# Design — migrate-console-maven-setting

> **路线图位置**：母路线图 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.3（**P2 #3**，路线图估计 8 / 实际 5 region method，偏差 -37.5%；偏差原因详见 proposal.md）
>
> rainbond 参照：
> - region API：`www/apiclient/regionapi.py:2123-2168`（5 method）
> - urls：`console/urls/__init__.py:1001-1003`（2 个 URL）
> - views：`console/views/region.py:366-460`（`MavenSettingView` + `MavenSettingRUDView`）

## 1. 范围（5 region method + 2 controller URL）

| # | rainbond region method | kuship `MavenSettingOperations` method | rainbond URL | kuship controller endpoint |
|---|------------------------|-----------------------------------------|--------------|----------------------------|
| 1 | `list_maven_settings` | `listMavenSettings(enterpriseId, rn, onlyName)` | GET `/v2/cluster/builder/mavensetting` | GET `/console/enterprise/{eid}/regions/{rn}/mavensettings?onlyname=true` |
| 2 | `add_maven_setting` | `addMavenSetting(enterpriseId, rn, body)` | POST `/v2/cluster/builder/mavensetting` | POST `/console/enterprise/{eid}/regions/{rn}/mavensettings` |
| 3 | `get_maven_setting` | `getMavenSetting(enterpriseId, rn, name)` | GET `/v2/cluster/builder/mavensetting/{name}` | GET `/console/enterprise/{eid}/regions/{rn}/mavensettings/{name}` |
| 4 | `update_maven_setting` | `updateMavenSetting(enterpriseId, rn, name, body)` | PUT `/v2/cluster/builder/mavensetting/{name}` | PUT `/console/enterprise/{eid}/regions/{rn}/mavensettings/{name}` |
| 5 | `delete_maven_setting` | `deleteMavenSetting(enterpriseId, rn, name)` | DELETE `/v2/cluster/builder/mavensetting/{name}` | DELETE `/console/enterprise/{eid}/regions/{rn}/mavensettings/{name}` |

**注**：本 change 的 region URL 路径不含 enterprise_id 段（rainbond 历史 URL 设计），enterprise_id 仅在 console 路径中作为权限 / 多租户隔离标识，不传给 region。

## 2. 接口

### 2.1 `MavenSettingOperations`（业务自治）

```java
package cn.kuship.console.modules.region.maven.api;

public interface MavenSettingOperations {

    /** @param onlyName true 时返回 [{name, is_default}]；false 返回完整内容含 xml content */
    List<Map<String, Object>> listMavenSettings(String enterpriseId, String regionName, boolean onlyName);

    Map<String, Object> addMavenSetting(String enterpriseId, String regionName, Map<String, Object> body);

    Map<String, Object> getMavenSetting(String enterpriseId, String regionName, String name);

    Map<String, Object> updateMavenSetting(String enterpriseId, String regionName, String name,
                                           Map<String, Object> body);

    Map<String, Object> deleteMavenSetting(String enterpriseId, String regionName, String name);
}
```

### 2.2 Impl 要点（MavenSettingOperationsImpl @Primary）

- 模式：`clientFactory.getClient(rn, "")` + `RegionApiSupport.exchange(...)` + `processor.checkStatus / extractBean`
- `listMavenSettings` 在 `onlyName=true` 时由 console 层把 region 返回的完整 list 投影成 `[{name, is_default}]`（与 rainbond 行为一致，避免传输大量 xml content）
- region URL 不含 enterprise_id（rainbond 真相）；enterprise_id 仅在 console 层做权限校验

## 3. Controller（MavenSettingController）

```java
@RestController
public class MavenSettingController {

    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings")
    public ApiResult listMavenSettings(...) { /* 调 listMavenSettings */ }

    @PostMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings")
    public ApiResult addMavenSetting(...) { /* 调 addMavenSetting；400 重名兼容 */ }

    @GetMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}")
    public ApiResult getMavenSetting(...) { /* 调 getMavenSetting；404 兼容 */ }

    @PutMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}")
    public ApiResult updateMavenSetting(...) { /* 调 updateMavenSetting */ }

    @DeleteMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}")
    public ApiResult deleteMavenSetting(...) { /* 调 deleteMavenSetting */ }
}
```

## 4. 权限

5 个 endpoint 全部要求 enterprise admin 权限（`@RequireEnterpriseAdmin`），与 rainbond `RegionTenantHeaderView` 行为一致；与已落地的 `LangVersionController`（P1 #4）权限模型一致。

## 5. 错误处理（rainbond 行为兼容）

- POST 400（name 已存在）→ 透传 400 + msg `"配置名称已存在"`
- GET/PUT/DELETE 404（name 不存在）→ 透传 404 + msg `"配置不存在"`
- 其它 5xx → 500 + msg `"配置 add/get/update/delete 失败"`
- region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`

## 6. 数据模型

**决策**：本 change SHALL NOT 落地任何本地缓存表；maven setting 由 region 端持久化（goodrain.me builder service 内部存储），console 100% 透传。

## 7. 与 P1 #4 build-versions 的边界

- **lang version 持有方**：`migrate-console-build-versions`（P1 #4，已落地）
- **本 change 持有方**：仅 maven setting CRUD（5 method）
- **协调点**：UI 在创建组件时若选择 java 语言，会**先后**调 lang version（P1 #4）和 maven settings list（本 change）；但本 change SHALL NOT 在内部硬编码任何 lang version 关联逻辑

## 8. 测试

- `MavenSettingOperationsImplTest`（单测，MockRestServiceServer）：5 method × 1 happy + 1 错误（400 重名 / 404 不存在）= 10 用例
- `MavenSettingIntegrationTest`（@SpringBootTest）：5 endpoint × happy/error = ~10 用例

## 9. 实施期决策（占位段）

待 apply 阶段补：
- region 返回的 list shape 真相（`bean.list` vs `data.list`）
- onlyName 投影是 console 端做还是 region 端做
- 与现有 `LangVersionController` 权限模型的实际匹配度
