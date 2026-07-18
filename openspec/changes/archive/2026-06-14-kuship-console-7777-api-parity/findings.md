# 对齐结论汇总(7777 UI → kuship-console 8000 vs rainbond 7070)

UI 调用去重 **561** 个 = 已实现 **55** + 未实现(404,backlog)**506**。本轮逐个 deep-diff 已实现的 55 个,**55/55 全部对齐**(165 单测全绿)。

## 一、修正的真实差异(7 处,均已构建+重启+验证)

| # | 端点 | 差异 | 修正 |
|---|------|------|------|
| 1 | `GET /console/config/info` | 缺 `data.initialize_info:null` | `ConfigInfoController.putExtra("initialize_info", null)` |
| 2 | `GET /console/perms` | 8000 要鉴权,7070 公开(AllowAny) | `SecurityConfig.PUBLIC_PATHS` 加 `/console/perms` |
| 3 | `GET /console/enterprise/{id}/user/{uid}/teams` | `roles` 缺角色名("管理员");用户不存在误返 200 | `TeamReadService` 补 `teamMemberRoleService.userTeamRoleNames` + `notFound("user not found","用户不存在")` |
| 4 | `GET /console/enterprise/{id}/regions` | `pods` 空态 `null` vs `{}` | 缺省回落 `{}` |
| 5 | `POST /console/users/access-token` | UI 发 JSON,8000 用 `@RequestParam` 读不到 note → 400 | 改 `@RequestBody Map` 读 note/age |
| 6 | `GET /console/users/logout` | UI 用 GET,8000 只有 POST;msg_show 不同 | `@PostMapping`→`@GetMapping`;`已退出登录`→`登出成功` |
| 7 | **13 个 team 端点**缺 `region_name` 校验 | 7070 `RegionTenantHeaderView` 缺 region_name 返裸 `400 请求参数不全`,8000 不强制 | 见下「三、系统性 region_name 收口」 |

## 二、判定为一致/不追(有据)

- `GET /console/users/details`:`permissions` 仅顺序不同(7070 是 Python set 哈希序),集合完全相同 → 内容一致。
- `DELETE /console/users/access-token/{id}`:**7070 自身 bug**(`NameError: access_key is not defined`,返 Python traceback),8000 返 200 正确 → 不对齐 7070 的 bug。
- `POST /console/teams/add-teams`(非法参数):**7070 自身 bug**(`TypeError` traceback),8000 返干净 400 → 不对齐 bug。
- `convert-resource`:仅 ConfigMap leader 注解的 `renewTime/resourceVersion` 易变 → 一致。
- 组件运行态(status/pods 等):`start_time` 等易变字段按形状校验。

## 三、系统性 region_name 收口(已完成）

**根因**:7070 这些 team 端点继承 `RegionTenantHeaderView`,`initial()` 在 handler 前校验 `region_name`(query/cookie),缺失则返**裸信封** `{code:400, msg:"", msg_show:"请求参数不全"}`(**无 data**)。8000 原先不强制。

**实现**:① `ApiResult.data` 加 `@JsonInclude(NON_NULL)`(null 时省略)；② `GeneralMessage.bare()` + `ServiceHandleException.paramIncomplete()`(裸 400)；③ `GlobalExceptionHandler` bare 渲染；④ 新增 `common/util/RegionScope.require(regionName)`；⑤ 给 **13 个端点**加 `region_name` 守卫。

**13 个端点**(实测 7070 逐个确认需要）：`teams/{}/overview`、`/groups`、`/users`、`/users/roles`、`/users/{uid}/perms`、`/notjoinusers`、`/roles/{id}`(GET/PUT/DELETE)、`/roles/perms`、`/roles/{id}/perms`(GET/PUT)、`/modifyname`、`/exit`、`/pemtransfer`、`/users/batch/delete`。
**不需要(实测确认,未加)**:`/roles`(list)、`createRole`、`/users/{uid}/roles`(GET/PUT/DELETE)、`/delete`(团队删,JWTAuthApiView)、`add-teams`。

验证:13 个端点缺 region_name → 两侧裸 `请求参数不全` 逐字节一致;带 region_name → 正常路径/数据完全一致。

## 四、破坏性端点(3,已解决)

- `POST /console/teams/{}/pemtransfer`:加 `region_name` 守卫 + **目标须为团队成员**校验(`TeamSettingsService.transferOwnership`,非成员→404「用户不存在」,不再误移交)。验证:带 region_name + 非成员 99999999 → 404,creater 不变。新增单测 `transfer_to_non_member_404`。
- `GET /console/teams/{}/exit`:加 `region_name` 守卫。带 region_name 时本就一致(creater 409「不能退出」)。
- `DELETE /console/teams/{}/users/batch/delete`:加 `region_name` 守卫。空入参「删除成员不能为空」本与 rainbond 视图一致;之前的「文案差异」实为 7070 的 region 前置校验(`请求参数不全`)被误判。两路现完全一致。

## 五、联调环境(供复跑)

- 账号 `interop`/`Interop@123`、`viewer`(低权);8000 起法 `MYSQL_PASSWORD=123456 java -jar target/kuship-console.jar`。
- 上下文:enterprise_id=`b16bf28d35cfde02d5a72e5038a59d79`、team=`default`、region=`rainbond`、region_id=`beb16025f33644c88b79e62eb05f50f6`。
- 组件读校准用 provision 配方铺设可丢弃组件 `gre88d16`(已清理,tenant_service 归零)。
- diff 工具:`/tmp/pdiff.sh`(GET)、`/tmp/mdiff.sh`(任意方法,比信封)。
