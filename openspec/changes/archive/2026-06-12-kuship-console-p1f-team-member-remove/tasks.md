## 1. 联调与参照准备

- [x] 1.1 确认环境在线 + 真实 SECRET_KEY 回写
- [x] 1.2 抓存 7070 基线到 `docs/p1f-7070-reference.md`：`notjoinusers`（含 query/分页）、`users/batch/delete`（成功 + 空/自身/创建者 各 400）；用 viewer 作可丢弃成员演练移除

## 2. 仓储补写/查询

- [x] 2.1 `UserInfoRepository.findByEnterpriseId(String)`（企业用户）
- [x] 2.2 `PermRelTenantRepository.deleteByUserIdInAndTenantId(List<Integer>, Integer)`
- [x] 2.3 `UserRoleRepository.deleteByUserIdInAndRoleIdIn(List<String>, List<String>)`

## 3. 服务

- [x] 3.1 `listNotJoinUsers(team, query, page, pageSize)`：企业用户 − 团队成员，query 模糊 nick_name，分页切片，返回 {list,page,page_size,total}
- [x] 3.2 `batchRemoveMembers(team, requesterUserId, userIds)`（@Transactional）：空/自身/创建者校验（400 文案对齐）+ 删 tenant_perms + 删团队 user_role
- [x] 3.3 单测：未加入过滤/分页、移除事务删两表、空/自身/创建者三种 400

## 4. 接口与鉴权

- [x] 4.1 `TeamMemberController` 加 GET notjoinusers（610001）、DELETE users/batch/delete（610004）
- [x] 4.2 响应包裹对齐（list + 顶层 page/page_size/total；删除成功 msg_show；400 文案）

## 5. 实跑校准与收尾

- [x] 5.1 读校准：notjoinusers 对 7070 deep-diff（含 query/分页顶层字段）
- [x] 5.2 写校准：用 viewer 临时入 default，DELETE batch 移除 → 回读 notjoinusers 含 viewer、tenant_perms/user_role 已删；8000 与 7070 一致；三种 400 一致
- [x] 5.3 鉴权校准：viewer（无 610xxx）访问两接口 → 403
- [x] 5.4 全量构建 + 单测；docs 记结论；清理临时数据
- [x] 5.5 openspec 校验，准备归档
