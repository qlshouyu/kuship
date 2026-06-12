## 1. 数据层实体反向映射（persistence）

- [x] 1.1 `TenantEnterprise` 映射 `tenant_enterprise`（PK `ID`；enterprise_id uniq char32、enterprise_name、enterprise_alias、is_active、enable_team_resource_view、logo、create_time）
- [x] 1.2 `Tenants` 映射 `tenant_info`（PK `ID`；tenant_id uniq char33、tenant_name uniq、enterprise_id、is_active、creater、limit_memory、create_time、update_time）
- [x] 1.3 `TenantRegionInfo` 映射 `tenant_region`（PK `ID`；tenant_id、region_name、is_active、is_init、region_tenant_name/id、region_scope）
- [x] 1.4 `EnterpriseUserPerm` 映射 `enterprise_user_perm`（PK `ID`；user_id、enterprise_id、identity、token uniq、is_initial_enterprise_admin）
- [x] 1.5 `PermRelTenant` 映射 `tenant_perms`（PK `ID`；user_id、tenant_id(整型)、enterprise_id(整型)、identity、role_id）—— 用户↔团队成员关系（取代 TenantUserRole，后者是角色定义归 P1-b）
- [x] 1.6 各实体 Repository（含按 enterprise_id / tenant_name / user_id 的查询方法）
- [x] 1.7 启动 validate 对真实/共享 console 库通过（无 DDL 输出、列精确对齐、无 @Version）

## 2. 上下文解析升级（tenant-context）

- [x] 2.1 扩展 `RequestContext`：新增 `enterprise`(TenantEnterprise)、`team`(Tenants) 实体字段
- [x] 2.2 `EnterpriseContextResolver`：按当前用户 enterprise_id 解析企业；enterprise 路由 `{enterprise_id}` 与当前用户不一致时按 7070 行为拒绝
- [x] 2.3 `TeamContextResolver`：按 `{team_name}`+enterprise_id 查 `tenant_info`，注入 RequestContext.team；查不到抛 `ServiceHandleException(msg="team not found", msg_show="团队不存在")`（HTTP code 以 7070 实测校准）
- [x] 2.4 team-scoped controller 进入业务前调用 resolver（仅"团队属于用户企业"级校验，不做权限码强校验）

## 3. account：当前用户详情（account-profile）

- [ ] 3.1 `GET /console/users/details`：从 RequestContext.currentUser 组装 bean（user_id/nick_name/email/enterprise_id 等）
- [ ] 3.2 与 7070 对照补齐 bean 字段集与命名

## 4. enterprise 读路径（enterprise-read）

- [ ] 4.1 `GET /console/enterprises`：返回当前用户企业（形态/字段对照 7070）
- [ ] 4.2 `GET /console/enterprise/{enterprise_id}/overview`：企业概览基础字段（重聚合 TODO 标注）；越权企业按 7070 拒绝

## 5. team 读路径（team-read）

- [ ] 5.1 `GET /console/enterprise/{enterprise_id}/teams`：企业下团队列表（tenant_info where enterprise_id）
- [ ] 5.2 `GET /console/enterprise/{enterprise_id}/user/{user_id}/teams`：用户加入的团队（团队切换器）
- [ ] 5.3 `GET /console/teams/{team_name}/overview`：团队概览（经 TeamContextResolver）；团队不存在返回"团队不存在"

## 6. 单元测试

- [ ] 6.1 ContextResolver：企业解析、团队解析、团队不存在→ServiceHandleException、越权企业拒绝
- [ ] 6.2 Repository 查询（mock/切片）：按 enterprise_id/tenant_name/user_id 取数
- [ ] 6.3 Controller 切片：各端点信封结构（data.bean/data.list）、未认证 401

## 7. 端到端对照 7070（同库同密钥逐接口 diff）

- [ ] 7.1 kuship(8000) 连共享 console 库 + 同源 SECRET_KEY 启动；准备测试用户与其企业/团队数据
- [ ] 7.2 逐接口 diff 7070 vs 8000：users/details、enterprises、enterprise/{eid}/overview、enterprise/{eid}/teams、enterprise/{eid}/user/{uid}/teams、teams/{team}/overview —— 信封/字段/错误码一致
- [ ] 7.3 rainbond-ui proxyTarget 指 8000，登录后控制台外壳加载、企业/团队列表、团队切换、团队概览可用
- [ ] 7.4 记录与 7070 的字段差异并校准（沿用 P0 经验，源码推断不足处以实测为准）
