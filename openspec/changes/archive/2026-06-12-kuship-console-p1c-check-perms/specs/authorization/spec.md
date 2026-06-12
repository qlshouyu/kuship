## ADDED Requirements

### Requirement: 整型权限码展开

系统 SHALL 提供"角色名列表 → 整型权限码集合"的展开（对齐 rainbond `perms.py` 的 `list_enterprise_perm_codes_by_roles`/`list_enterprise_perm_codes_by_role`/`get_enterprise_adminer_codes`）：企业 `admin` 角色 SHALL 展开为团队 + 企业全部权限码；其它已知角色展开为该角色权限码并叠加 `common_perms` 码；任何角色集合的展开结果 SHALL 恒定叠加 `common_perms` 码。该集合是整型 `code`，区别于 P1-b 的 `group.name` 字符串集合。

#### Scenario: admin 展开为全部权限码

- **WHEN** 对含 `admin` 的企业角色集合展开整型权限码
- **THEN** 结果包含全部团队与企业权限码（`get_enterprise_adminer_codes`），且与 7070 同口径一致

#### Scenario: 非 admin 角色叠加 common_perms

- **WHEN** 对不含 admin 的角色集合展开
- **THEN** 结果含各角色自身权限码并叠加 `common_perms` 的码

### Requirement: 用户权限码计算

系统 SHALL 计算"当前用户在当前请求范围下拥有的整型权限码集合"，分两类（对齐 `base.py`）：

- **企业级**（对齐 `JWTAuthApiView.get_perms`）：取用户企业角色（`enterprise_user_perm.identity`）展开为整型企业权限码集合。
- **团队级**（对齐 `TenantHeaderView.get_perms`）：企业角色码 ∪ 团队权限。其中**仅团队所有者**（user==team creater）SHALL 短路为全部团队权限码并追加 `100001`；普通（非所有者）用户 SHALL 取其团队角色 `role_perms` 中全局（`app_id=-1`）权限码的并集。企业管理员之所以能通过团队接口鉴权，是因其企业角色 `admin` 经整型码展开（`list_enterprise_perm_codes_by_roles`）已含全部团队码，而**非**被当作 owner 单独短路——故本计算不引入独立的"企业管理员短路"，以免对 identity≠admin 的企业成员多放行。

#### Scenario: 团队 owner 拥有全部团队权限码

- **WHEN** 计算团队创建者在该团队的权限码
- **THEN** 集合包含全部团队权限码与 `100001`

#### Scenario: 企业 admin 经企业码展开获得团队码

- **WHEN** 计算企业角色含 `admin`、但非团队创建者的用户在某团队的权限码
- **THEN** 因企业码展开已含全部团队码，集合覆盖团队接口所需码（无需 owner 短路）

#### Scenario: 普通成员按角色码

- **WHEN** 计算普通成员（非 owner、企业角色非 admin）在某团队的权限码
- **THEN** 集合为其团队角色 `role_perms` 全局码并集（叠加企业角色码），不含未授予的码

### Requirement: 路由所需权限码绑定

系统 SHALL 提供"受保护接口 → 按 HTTP 方法（get/post/put/delete）声明的所需权限码列表"的绑定机制（对齐 rainbond `perms_route_config` 与 URL 第三参 `__message`）。某方法所需码为空 SHALL 表示该方法对已认证用户放行（不做码校验）。

#### Scenario: 按方法取所需码

- **WHEN** 命中某受保护接口的某 HTTP 方法
- **THEN** 系统能取到该方法声明的所需权限码列表（可能为空）

### Requirement: check_perms 强制鉴权

系统 SHALL 在已认证（P0）与范围上下文解析（团队上下文）之后、进入业务处理之前，对受保护接口执行 `check_perms`：取当前请求方法所需权限码列表，与用户权限码集合求交集；当所需码非空且未被用户权限码完全覆盖时 SHALL 拒绝（抛无权异常）；所需码为空时 SHALL 放行。

#### Scenario: 权限充分放行

- **WHEN** 用户权限码集合包含某接口某方法的全部所需码
- **THEN** 请求进入业务处理，返回正常结果

#### Scenario: 权限不足拒绝

- **WHEN** 用户权限码集合缺少所需码中任一项
- **THEN** 请求被拒绝，返回无权响应（不进入业务处理）

### Requirement: 无权响应契约

系统 SHALL 将无权拒绝渲染为 HTTP 403，信封 `code=10402`、`msg=no permissions `、`msg_show=没有操作权限`（对齐 rainbond `NoPermissionsError`）。异常体系 SHALL 支持独立于 HTTP status 的业务 error_code（无权场景 status=403、code=10402）。

#### Scenario: 无权返回 403/10402

- **WHEN** `check_perms` 判定无权
- **THEN** 响应 HTTP 403，信封 `code=10402`、`msg_show=没有操作权限`，与 7070 一致
