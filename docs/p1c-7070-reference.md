# P1-c（check_perms 强制鉴权）7070 校准基线

> 环境同前：rainbond-console v6.9.0(7070)，共享 console 库。
> 测试用户：interop(700002, 企业 admin + default 团队 owner，全权) / **viewer(700003, 低权)**。
> viewer 由本轮 DB 直插：user_info(nick_name=viewer, email=viewer@kuship.cn, 密码 Viewer@123,
> enterprise_id=b16bf28d…)，**无 enterprise_user_perm 行、无 default 团队 user_role**。

## 1. 无权 403 基线（viewer 请求 default 团队 overview）

```
GET /console/teams/default/overview   Authorization: GRJWT <viewer token>
HTTP 403
{"code":10402,"msg":"no permissions ","msg_show":"没有操作权限","data":{"bean":{},"list":[]}}
```
- 对齐 rainbond `NoPermissionsError`(status=403, error_code=10402)；信封 `code` = error_code（非 HTTP status），`data` 为标准空壳 `{bean:{},list:[]}`。
- 触发链：viewer 无 enterprise_user_perm 行 → `list_roles` 走 DoesNotExist 返回 `[]` → 企业码仅 `common_perms`；default 团队无 `user_role` → 团队码空；`teams/{team}/overview` 需 `200001`(TEAM_OVERVIEW_DESCRIBE) → 交集缺失 → 403。

## 2. 有权基线（interop）

interop 是 owner+企业管理员 → check_perms 通过（不 403）。其 overview 业务层因缺 region 参数返回 `HTTP 400 {"code":400,"msg":"","msg_show":"请求参数不全"}`——这是 P1-a 接口的下游行为，**非 P1-c 范围**。P1-c 校准只断言鉴权层：interop 不被 403、viewer 被 403。

## 3. 坑：empty identity 触发 rainbond 崩溃

若给 enterprise_user_perm.identity 置空串 `''`，`list_roles` 返回 `['']`，rainbond `list_enterprise_perm_codes_by_role('')` 取 `ENTERPRISE.get('',[])["perms"]` → `TypeError`（HTTP 500）。故合法低权用户必须**无 enterprise_user_perm 行**（非空串）。kuship 侧已在 P1-b 对未知角色做防御（不崩溃，按空处理），与此不冲突。

## 4. 整型权限码口径（鉴权用，区别于 P1-b 字符串展开）

- `list_enterprise_perm_codes_by_roles`：admin→`get_enterprise_adminer_codes()`（团队+企业全码）；非 admin→角色码 + common_perms 码；恒叠加 common_perms 码。
- 团队 user_perms（TenantHeaderView.get_perms）：企业码 ∪（owner→全团队码 + `100001`；企业管理员等同 owner；成员→`role_perms` 全局(app_id=-1)码并集）。
- `teams/{team}/overview` 所需码：`200001`（TEAM_OVERVIEW_DESCRIBE，get/post/put/delete 同）。

## 5. 校准断言

- viewer → 403 + code=10402 + msg_show=没有操作权限（auth 层）。
- interop → 非 403（过鉴权；下游 200/400 属 P1-a，不在本轮断言）。

## 6. 实跑校准结果（8000 vs 7070）

- **viewer @ default overview**：8000 与 7070 **逐字节一致** `HTTP 403 {"code":10402,"msg":"no permissions ","msg_show":"没有操作权限","data":{"bean":{},"list":[]}}`。✓
- **interop @ default overview?region_name=rainbond**：8000 → `HTTP 200`（有权过鉴权，业务成功）。✓
- **次序验证**：interop 无 region_name → 8000 `HTTP 400`（先过 check_perms、后业务校验参数），非 403；与 rainbond initial() 先解析/鉴权、后 get() 校验一致。✓
- **放行验证**：viewer 请求无 `@RequiresPerms` 的 `users/details` → `HTTP 200`（未标注即放行）。✓
- **认证前置**：未认证请求 overview → `HTTP 401`（P0 鉴权先行）。✓

结论：check_perms 鉴权底座与 7070 行为一致，无遗留 defer。企业级接口是否需补 `@RequiresPerms` 待后续按 7070 实测逐个判定（本轮仅落地团队 overview）。
