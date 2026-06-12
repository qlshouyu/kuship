## 1. 联调与参照准备

- [x] 1.1 确认 7070/8000/mysql/redis 在线；从运行版 console 进程取真实 SECRET_KEY 回写 `/tmp/kuship_secret.txt`
- [x] 1.2 抓存 7070 读基线到 `docs/p1d-7070-reference.md`：`GET /console/perms`、`teams/default/roles`、`roles/{id}`、`roles/perms`、`roles/{id}/perms`（用 interop）
- [x] 1.3 抓存 7070 写基线：用临时角色 `p1d_tmp` 走 POST 创建 / PUT 改名 / PUT perms / DELETE，记录各响应 bean 与 msg_show；确认是否需要 `region_name` 参数（Open Question）

## 2. PermsCatalog 写支撑扩展

- [x] 2.1 `getPermsNameCodeKv()`：`分组名_权限名 → 整型码` 映射（对齐 `get_perms_name_code_kv`，团队+企业，硬编码）
- [x] 2.2 `getStructure(template, kindName)` + `getPermsStructure(tenantId)`：name/desc/code 形态的权限元数据树（应用子模型无应用域则空）
- [x] 2.3 `unpackRolePermsTree(permsTree)`：遍历提交树，true 叶子按 kv 映射为 `(code, appId)` 列表（app_<id> 解析 appId，否则 -1；未知键跳过并 log）
- [x] 2.4 单测：kv 覆盖团队权限名、structure 结构对齐 7070、unpack(pack(codes)) 往返一致（owner 全 true → 全码）

## 3. 仓储写方法

- [x] 3.1 `RoleInfoRepository`：保存/改名/删除；`findByKindAndKindIdInAndId` / `findByKindAndKindIdIn`（with_default = {tenantId,"default"}）；`findByKindAndKindIdAndName` 等查重
- [x] 3.2 `RolePermsRepository`：`deleteByRoleId`、`saveAll`（批量写）
- [x] 3.3 `UserRoleRepository`：`deleteByRoleId`（删角色时清成员关联）

## 4. 角色写服务

- [x] 4.1 `getRoles(tenantId, withDefault)`：团队角色 ∪ 默认角色（kind_id="default"）
- [x] 4.2 `createRole(tenantId, name)`：名非空 + 唯一（含默认）校验；新角色 kind=team/kind_id=tenantId
- [x] 4.3 `getRoleById(tenantId, roleId, withDefault)` / `updateRole`（改名，唯一校验，自身幂等，默认角色不可改）
- [x] 4.4 `deleteRole`（@Transactional：删 role_perms + user_role + role_info；默认/不存在拒绝）
- [x] 4.5 `getRolesPerms(tenantId)` / `getRolePerms(tenantId, roleId)`：复用 packRolePermsTree 出权限树
- [x] 4.6 `updateRolePerms(roleId, permsTree)`（@Transactional：deleteByRoleId + unpack saveAll）
- [x] 4.7 单测：创建/重名/删除连带清理/默认角色保护/权限树读写（mock 仓储）

## 5. 接口与鉴权

- [x] 5.1 `PermsInfoController` GET /console/perms（免鉴权，返回 getPermsStructure）
- [x] 5.2 `TeamRoleController`：GET/POST /console/teams/{team_name}/roles；GET/PUT/DELETE /roles/{role_id}
- [x] 5.3 同上：GET /roles/perms；GET/PUT /roles/{role_id}/perms
- [x] 5.4 标 `@RequiresPerms(TEAM,...)`：get=630001 / post=630002 / put=630003 / delete=630004；/console/perms 不标
- [x] 5.5 响应包裹对齐（general_message bean/list、msg_show 文案、RUD 去 kind/kind_id）

## 6. 实跑校准与收尾

- [x] 6.1 读接口对 7070 deep-diff（perms/roles/roles 列表/单角色/角色权限树）逐叶子一致
- [x] 6.2 写接口校准：用 `p1d_tmp` 走 create→改名→改 perms→读回→delete，8000 与 7070 响应一致；写后回读 role_perms 落库正确
- [x] 6.3 鉴权校准：viewer（无 630xxx）访问角色写接口 → 403/10402；/console/perms 免鉴权 200
- [x] 6.4 全量构建 + 现有单测通过；`docs/p1d-7070-reference.md` 记已知差异/defer；清理 p1d_tmp 角色
- [x] 6.5 走 openspec 校验（`openspec validate`），准备归档
