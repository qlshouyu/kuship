# kuship-console-app

## ADDED Requirements

### Requirement: 企业级 maven 仓库配置 CRUD（migrate-console-maven-setting）

kuship-console 后端 SHALL 落地企业级 maven 仓库配置（mavensetting）的 CRUD 透传，覆盖 rainbond `views/region.py:MavenSettingView + MavenSettingRUDView` + `regionapi.py:2123-2168` 中 5 个 region method（`list_maven_settings` / `add_maven_setting` / `get_maven_setting` / `update_maven_setting` / `delete_maven_setting`），由 console 100% 透传到 region 端 builder service，本地不缓存。

本 Requirement 是母路线图 [`migrate-region-coverage-roadmap`](../../../migrate-region-coverage-roadmap/) 表中 **P2 #3** 行的细化契约（路线图估计 8 method，实际 5；偏差原因：路线图含 lang version 协调，但 lang version 已被 P1 #4 持有）。

#### Scenario: 列出 region 端 maven 配置

- **WHEN** 客户端调 GET `/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings?onlyname=true`
- **THEN** 后端 SHALL 调 region GET `/v2/cluster/builder/mavensetting`
- **AND** 当 `onlyname=true` 时 SHALL 把 region 返回的完整 list 投影为 `[{name, is_default}]`（避免传输大量 xml content）
- **AND** 当 `onlyname=false` 或缺省时 SHALL 返回完整 list（含 content xml）
- **AND** 响应 200 + `{"code": 200, "list": [...]}`

#### Scenario: 添加 maven 配置

- **WHEN** 客户端调 POST `/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings` body `{"name": "...", "content": "<settings>...</settings>", "is_default": false}`
- **THEN** 后端 SHALL 调 region POST `/v2/cluster/builder/mavensetting` body 透传
- **AND** region 返回 400（name 已存在）SHALL 透传 400 + msg `"配置名称已存在"`
- **AND** 响应 200 + `bean = region 返回的 maven setting`

#### Scenario: 查询单条 maven 配置详情

- **WHEN** 客户端调 GET `/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}`
- **THEN** 后端 SHALL 调 region GET `/v2/cluster/builder/mavensetting/{name}`
- **AND** region 返回 404 SHALL 透传 404 + msg `"配置不存在"`
- **AND** 响应 200 + `bean = {name, content, is_default, ...}`

#### Scenario: 更新 maven 配置

- **WHEN** 客户端调 PUT `/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}` body
- **THEN** 后端 SHALL 调 region PUT `/v2/cluster/builder/mavensetting/{name}` body 透传
- **AND** region 返回 404 SHALL 透传 404 + msg `"配置不存在"`

#### Scenario: 删除 maven 配置

- **WHEN** 客户端调 DELETE `/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings/{name}`
- **THEN** 后端 SHALL 调 region DELETE `/v2/cluster/builder/mavensetting/{name}`
- **AND** region 返回 404 SHALL 透传 404 + msg `"配置不存在"`

#### Scenario: 全部 endpoint 要求企业管理员权限

- **WHEN** 任何 5 个 endpoint 被非 enterprise admin 用户访问
- **THEN** 后端 SHALL 返回 403 + msg `"需要企业管理员权限"`
- **AND** 由 `@RequireEnterpriseAdmin` 注解驱动（与 P1 #4 LangVersionController 一致）

#### Scenario: 路线图位置可追溯

- **WHEN** 团队成员看到本 Requirement
- **THEN** SHALL 在 `kuship-console/CLAUDE.md` "Region API 覆盖度路线" 表 P2 #3 行 + 本 spec 文件头部找到完整路线图引用
- **AND** SHALL 知道本 change 与 P1 #4 build-versions 的协调边界：lang version 由 build-versions 持有，本 change 仅 maven setting CRUD
- **AND** SHALL 不与其它 P0/P1/P2 子 change 的 region URL 前缀重叠（本 change 唯一前缀：`/v2/cluster/builder/mavensetting*`）
