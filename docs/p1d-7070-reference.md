# P1-d（团队角色管理写）7070 校准基线

> 环境同前；用户 interop(700002, default owner+企业 admin)。team default tenant_id=a274b41c…，已有角色 ID 1/2/3=管理员/开发者/观察者。
> **关键：`roles/{id}`、`roles/perms`、`roles/{id}/perms` 继承 RegionTenantHeaderView，强制 `?region_name=rainbond`，缺则 HTTP 400「请求参数不全」。**`roles`(LC) 与 `POST` 不需要 region。`GET /console/perms` 免鉴权。

## 1. 读接口 bean 形态

- `GET /console/perms?tenant_id=<tid>`：`data.bean` = 权限元数据树（`get_perms_structure`），形如 `{team:{sub_models:[...], perms:[{name,desc,code},...]}, enterprise:{...}}`。叶子含 name/desc/code。
- `GET teams/{t}/roles`：`data.list` = `[{name, ID}]`（`{"管理员",1},{"开发者",2},{"观察者",3}`）。`bean={}`。
- `GET teams/{t}/roles/{id}?region_name=`：`data.bean` = `{ID, name}`（**去 kind/kind_id**）。
- `GET teams/{t}/roles/perms?region_name=`：`data.list` = `[{role_id(int), permissions:树}]`，3 项。**team_app_manage 含模型默认 7 个 app 子模型**（app_overview/app_release/app_gateway_manage/app_upgrade/app_resources/app_backup/app_config_group），perms 为 list。role_id 为 **int**。
- `GET teams/{t}/roles/{id}/perms?region_name=`：`data.bean` = `{role_id(string "1"), permissions:树}`。**team_app_manage 为空 `{sub_models:[],perms:{}}`**（app 替换，无应用域）。role_id 为 **string**。

> permissions 树与 P1-b tenant_actions 同构（team 根 6 个 sub_models、叶子 `{name:bool}`）。**列表端不做 app 替换（保留 7 子模型），单个端做 app 替换（空）**——两端 team_app_manage 形态不同、role_id 类型不同（int vs string）。

## 2. 写接口 bean 形态（临时角色 p1d_tmp，ID 自增=4）

- `POST teams/{t}/roles` body `{"name":"p1d_tmp"}`：`bean={ID, name, kind_id, kind}`（**全 to_dict，不去 kind/kind_id**），`msg_show=创建角色成功`。名空→`角色名称不能为空`；重名（含默认）→`角色名称已存在`。
- `PUT teams/{t}/roles/{id}?region_name=` body `{"name":"p1d_tmp2"}`：`bean={ID, name}`（**去 kind/kind_id**），`msg_show=更新角色成功`。
- `PUT teams/{t}/roles/{id}/perms?region_name=` body `{"permissions":树}`：HTTP 200。提交树中 true 叶子降维落 `role_perms`：本例只开 team_overview.describe → `role_perms` 仅 `(role_id=4, perm_code=200001, app_id=-1)`（整表按角色重建）。
- `DELETE teams/{t}/roles/{id}?region_name=`：`bean={}`，`msg_show=删除角色成功`。连带删该角色 role_perms 与 user_role。

## 3. 校准要点

- create 响应保留 kind/kind_id，RUD(get/put) 去除——两套 bean 形态。
- unpack 名↔码：`team_overview_describe`→200001 等，硬编码 `get_perms_name_code_kv()`，全局码 app_id=-1。
- 写联调用可丢弃角色（如 p1d_tmp），勿动 interop 的 1/2/3。
- 鉴权：角色接口码 TEAM_ROLE_PERMS get=630001/post=630002/put=630003/delete=630004；`/console/perms` 免鉴权。

## 4. 实跑校准结果（8000 vs 7070）

- **读接口 deep-diff 全 0**：`/console/perms`、`roles`、`roles/{id}`、`roles/perms`、`roles/{id}/perms`（带 region_name）逐叶子一致（含 role_id int/string 差异、team_app_manage 列表端 7 子模型 vs 单个端空）。✓
- **写流程**（8000 临时角色 p1d_k8000，ID 自增=5）与 7070 一致：create bean{ID,name,kind_id,kind}/创建角色成功；重名→400 角色名称已存在；rename bean{ID,name}/更新角色成功；PUT perms→`role_perms` 仅 (5,200001,-1)；delete bean{}/删除角色成功。✓
- **连带清理**：delete 后 role_info 与 role_perms 该角色记录均为 0。✓
- **鉴权**：viewer（无 630xxx）GET/POST/DELETE 角色接口 → 403（同 7070）；`/console/perms` → 200（免 @RequiresPerms）。✓
- **region_name**：kuship 对 RUD/perms 接口接收即忽略（角色功能不依赖 region）；7070 缺 region_name 会 400，kuship 不强制——为本轮明确放宽（读响应在带 region_name 时逐叶子一致）。

结论：团队角色管理写底座与 7070 行为一致，无遗留 defer（应用级权限子树因无应用域，列表端保留模型结构、单个端为空，均与 7070 天然一致）。
