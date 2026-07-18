> 全量接口清单见 `ui-endpoint-inventory.md`:UI 调用去重 **561** 个 = 已实现可对齐 **55** + 未实现(404,后续 feature)**506**。本 change 范围 = 下列 55 个已实现端点逐个对齐;506 个未实现端点列入 backlog,不在本次。

> **进展快照(2026-06-14)**:**55/55 全部对齐,165 单测全绿**。详见 `findings.md`。
> 修了 7 处真实差异:config/info、perms、user-teams 角色+404、regions pods、access-token JSON、logout GET、**13 个 team 端点 region_name 裸 400 校验**(系统性)。
> pemtransfer 额外加固目标成员校验;组件读 16 个 provision 验证全 ✅。

## 0. 准备
- [x] 0.1 登录两侧取 token(interop);7777 前端可导航
- [x] 0.2 deep-diff 工具 `/tmp/pdiff.sh`(剥离 token/时间戳等易变字段后排序比对)

## 1. Bootstrap / 鉴权域(首屏主链路)
- [x] 1.1 `GET /console/config/info` deep-diff ✅ 补 data.initialize_info:null
- [x] 1.2 `GET /console/perms` deep-diff ✅ 放行公开(树本就一致)
- [~] 1.3 `GET /console/healthz` 范围外(UI 不调用、7070 无此端点)
- [x] 1.4a `POST /console/users/login` ✅ 一致(token 易变、payload 同)
- [x] 1.4b `GET /console/users/logout` ✅ 修方法 POST→GET + msg_show「登出成功」(GET 体逐字节一致、POST 返 405)
- [x] 1.5 `GET /console/users/details` ✅ 内容一致(permissions 集合相同,顺序系 rainbond Python set 哈希序不追)、`/query` ✅
- [x] 1.6a `GET/PUT /console/users/custom_configs` ✅(GET 一致)
- [x] 1.6b `POST /console/users/changepwd` ✅ 契约一致(错误旧密码两侧"旧密码错误")
- [x] 1.7 access-token：GET ✅、POST ✅(修 JSON body 绑定)、PUT ✅、DELETE(7070 自身 NameError bug,8000 正确不对齐)
- [x] 1.8 鉴权域单测全绿(164 全量通过)

## 2. 企业域 ✅ 全部一致
- [x] 2.1 `GET /console/enterprises` ✅
- [x] 2.2 `GET /console/enterprise/{id}/info`、`/overview`、`/overview/team` ✅
- [x] 2.3 `GET /console/enterprise/{id}/admin/roles`、`/users` ✅
- [x] 2.4 企业域无 diff(全 ✅),既有单测绿

## 3. 团队域(团队 + 成员/角色)
- [x] 3.1 `GET /console/enterprise/{id}/teams` ✅、`/user/{user_id}/teams` ✅(修角色名+404)
- [x] 3.2 `GET /console/teams/{team}/overview`、`/groups`(带 region_name) ✅
- [x] 3.3a `DELETE /teams/{team}/delete` ✅契约(404)、`/modifyname` ✅、`add-teams`(7070 traceback bug,8000 更正确)
- [x] 3.3b `/pemtransfer` ✅(region_name 守卫+目标成员校验,非成员 404)、`/exit` ✅(region_name 守卫)
- [x] 3.4a 成员读 `GET .../users`、`/users/roles`、`/users/{uid}/roles`、`/users/{uid}/perms`、`/notjoinusers` ✅
- [x] 3.4b `/users/{uid}/roles` PUT ✅(非成员 404);`/users/batch/delete` ✅(region_name 守卫,两路一致)
- [x] 3.5 角色 `GET /roles`、`/roles/{id}`、`/roles/{id}/perms` ✅;create→delete ✅;RUD/perms 加 region_name 守卫
- [x] 3.6 团队/成员/角色域单测全绿(新增 transfer_to_non_member_404)

## 3b. 系统性 region_name 校验(13 端点）✅
- [x] 基建:ApiResult NON_NULL data + GeneralMessage.bare + ServiceHandleException.paramIncomplete + GlobalExceptionHandler bare + RegionScope
- [x] 13 端点加守卫,缺 region_name 返裸「请求参数不全」、带则正常,两侧逐字节一致(详见 findings.md §三)

## 4. 集群域 ✅ 全部一致
- [x] 4.1 `GET /console/enterprise/{id}/regions`(修 pods null→{})、`/regions/{region_id}` ✅
- [x] 4.2 `GET .../namespace`、`/resource` ✅、`/convert-resource` ✅(仅 renewTime/resourceVersion 易变)
- [x] 4.3 `GET .../cnb/frameworks` ✅
- [x] 4.4 集群域无 diff(全 ✅),既有单测绿

## 5. 应用/组件读域
- [x] 5.1 brief/detail/status ✅(provision gre88d16 后运行态 parity)
- [x] 5.2 ports/envs/volumes/probe/labels ✅
- [x] 5.3 pods/graphs/xparules/k8s-attributes ✅
- [x] 5.4 dependency/dependency-list/dependency-reverse/un_dependency ✅
- [x] 5.5 已 provision 可丢弃组件 gre88d16(grp22),16 端点全 ✅,待清理
- [x] 5.6 组件读 16 个无 diff(provision 验证 ✅),既有单测绿

## 6. 收口
- [x] 6.1 差异/修正结论汇总 → `findings.md`(7 修正 + region_name 系统性收口 + 不追判定)
- [x] 6.2 全量构建 + 165 单测全绿(BUILD SUCCESS)
- [x] 6.3 docs 已更新(inventory 55/55 + findings + tasks);openspec validate 通过;可归档
