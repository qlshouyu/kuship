# Tasks — migrate-console-dependency-extras

## 1. 探测 region 端 3 个 URL

- [ ] 1.1 curl 验证 3 个 URL（注意 `dependencys` 拼写）：（需用户联动验证，实施环境无在线 region）
  - `POST /v2/tenants/<ns>/services/<alias>/dependencys` body=`{"dep_service_ids":[...]}` → 200
  - `POST /v2/tenants/<ns>/services/<alias>/volume-dependency` body=`{...}` → 200/201
  - `DELETE /v2/tenants/<ns>/services/<alias>/volume-dependency` body=`{...}` → 200
- [ ] 1.2 响应 shape 写到 design.md（需用户联动验证，待 curl 完成后补充）

## 2. 实现 ServiceDependencyOperations 3 个 default override

- [x] 2.1 注入 `TenantsRepository` 到 `ServiceDependencyOperationsImpl`（tenant_id 注入在 service 层，Impl 不需要注入 repo）
- [x] 2.2 实现 `addDependencies(rn, tn, alias, body)`：
  - URL = `/v2/tenants/{namespace}/services/{alias}/dependencys`（拼写保留）
  - 在 service 层（不在 Impl）注入 `tenant_id = tenants.namespace` 到 body
  - POST 透传
- [x] 2.3 实现 `addVolumeDependency(rn, tn, alias, body)`：URL = `/v2/tenants/{ns}/services/{alias}/volume-dependency`，POST
- [x] 2.4 实现 `deleteVolumeDependency(rn, tn, alias, body)`：DELETE with body
- [x] 2.5 单测 3 method × 2（共 6 个单测，全部通过）

## 3. 新建 AppDependencyBatchService

- [x] 3.1 新建 `modules/application/service/AppDependencyBatchService.java`
- [x] 3.2 `@Transactional public Map<String, Object> addBatch(teamName, serviceAlias, body)`：
  - 取 `TenantService` by alias
  - 解析 body `dep_service_ids` (List<String>)
  - 循环每个 dep：
    - 已存在（`serviceRelationRepository.findByServiceIdAndDepServiceId(...)`）→ 跳过
    - 循环检测（BFS）→ 抛 `ServiceHandleException(400, "circular dependency", "依赖关系不能形成循环")`
    - 标记为待 INSERT
  - 批量 INSERT 待加 dep
  - 调 `serviceDepOps.addDependencies(rn, tn, alias, body{dep_service_ids,tenant_id})`
  - 返 region 响应

## 4. 扩展 AppDependencyController（已存在）

- [x] 4.1 在 `modules/application/controller/AppDependencyController.java` 注入 `AppDependencyBatchService`
- [x] 4.2 追加 `@PostMapping({"/dependency-list", "/dependency-list/"})`
  - body 透传给 batch service
  - `@RequirePerm(PermCode.APP_CREATE_PERMS)`
- [x] 4.3 校验既有 GET `/dependency-list`（已实现）不受影响（GET 与 POST 路径名相同但 HTTP method 不同，Spring 正确区分）

## 5. 集成测试

- [x] 5.1 `AppDependencyBatchIntegrationTest`（位于 integration 包）：
  - 3 dep 全部新 → INSERT 3 行 + region 调用 1 次 ✓
  - 1 dep 已存在 → INSERT 2 行（去重） ✓
  - 1 dep 循环 → 抛异常，0 行 INSERT ✓
  - region 5xx → 事务回滚 0 行 ✓
- [x] 5.2 `AppDependencyControllerBatchTest`：
  - POST happy path（sys_admin=1，200） ✓
  - 无权限 403（普通成员，无 app_create_perms） ✓

## 6. 文档与归档

- [ ] 6.1 更新 `kuship-console/CLAUDE.md` 依赖管理段（由 leader 合并 CLAUDE_FRAGMENT.md 完成，本 change 不修改 CLAUDE.md）
- [x] 6.2 CLAUDE_FRAGMENT.md 已生成于 `openspec/changes/migrate-console-dependency-extras/CLAUDE_FRAGMENT.md`

## 7. 编译 / 重启 / 验证

- [x] 7.1 `mvn -DskipTests package` → BUILD SUCCESS
- [ ] 7.2 `curl -X POST ... /apps/<alias>/dependency-list -d '{"dep_service_ids":["id1","id2"]}'` → 200（需用户联动验证，实施环境无在线 region）
