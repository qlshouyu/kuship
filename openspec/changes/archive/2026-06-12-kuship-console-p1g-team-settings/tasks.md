## 1. 联调与参照准备

- [x] 1.1 确认环境在线 + 真实 SECRET_KEY；抓 7070 基线到 `docs/p1g-7070-reference.md`：pemtransfer(移交成功)、modifyname(完整 bean/团队信息修改成功)、exit(creater→409 / 成员→退出团队成功)

## 2. 仓储确认

- [x] 2.1 复用 TenantsRepository.save、PermRelTenantRepository.deleteByUserIdInAndTenantId、UserRoleRepository.deleteByUserIdAndRoleIdIn（已存，无需新增；如缺则补）

## 3. 服务（TeamSettingsService）

- [x] 3.1 `transferOwnership(team, requesterUserId, targetUserId)`：owner 校验（非 owner→NoPermissionsException）+ set creater + save
- [x] 3.2 `updateTenantInfo(team, newAlias, newLogo)`：set alias、(logo)、update_time=now、save；返回完整 bean(to_dict 12 字段)
- [x] 3.3 `exitTeam(team, userId)`：creater→409 异常；否则 @Transactional 删 tenant_perms + 团队 user_role；返回成功
- [x] 3.4 单测：owner 移交/非 owner 拒、改名 bean 字段、creater 不可退、成员退删两表

## 4. 接口

- [x] 4.1 `TeamSettingsController`：POST pemtransfer、POST modifyname、GET exit
- [x] 4.2 响应包裹对齐（pemtransfer 移交成功；modifyname msg=update success/团队信息修改成功 + bean；exit 200 退出团队成功 / 409 文案）

## 5. 实跑校准与收尾

- [x] 5.1 modifyname 校准：8000 改名→bean 除 update_time 外与 7070 一致；测后还原 alias
- [x] 5.2 pemtransfer 校准：owner 移交 viewer→200 移交成功；非 owner→403；测后 DB 还原 creater=700002
- [x] 5.3 exit 校准：interop(creater)→409；viewer(入 default 后)自退→200 退出团队成功 + 成员关系删除
- [x] 5.4 全量构建 + 单测；docs 记结论；清理临时数据（default 还原）
- [x] 5.5 openspec 校验，准备归档
