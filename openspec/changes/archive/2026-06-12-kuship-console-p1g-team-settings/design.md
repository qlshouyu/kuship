## Context

团队设置写三件：移交（`UserPemTraView`/TeamOwnerView，纯改 `tenant_info.creater`）、改名（`TeamNameModView`，改 `tenant_alias`/`logo`/`update_time`）、退出（`TeamExitView`→`exit_current_team`，删自身 `tenant_perms`+`user_role`）。均纯 console 库写，不触 region。复用 P1-a 的 `Tenants`/`PermRelTenant` 实体、P1-e/f 的 `UserRole`/`PermRelTenant` 删方法。

## Goals / Non-Goals

**Goals:** 3 接口纯 DB 写 + 对 7070 校准；pemtransfer owner-only、modifyname/exit 无码门槛。
**Non-Goals:** 团队建/删（region provision/销毁）、成员加入（企业级且 provision region）。

## Decisions

### 决策 1：新建 `TeamSettingsService` + `TeamSettingsController`（modules/team）
- 团队自身设置属 team 域；与 P1-a 的 `TeamController`/`TeamContextResolver` 同域。

### 决策 2：pemtransfer owner-only 用显式 owner 校验，非 @RequiresPerms
- `TeamOwnerView` 的门槛是"必须团队创建者"，非权限码。故在服务/控制器显式：`team.creater != currentUser.userId` → 抛 `NoPermissionsException`（403/10402，复用 P1-c）。不挂 `@RequiresPerms`。

### 决策 3：modifyname 更新 update_time，bean 为完整 to_dict
- `update_tenant_info`：set tenant_alias、（new_logo 非空时）logo、update_time=now，save。bean 含 12 字段（对齐 7070 实测）。`update_time` 为当前时间，校准时排除该字段逐字节比对（仅验格式/其余字段）。
- LocalDateTime 序列化沿用 P0/P1-a 全局 Jackson 配置（"yyyy-MM-dd HH:mm:ss"，P1-a 已验证）。

### 决策 4：exit 事务删两表，创建者 409
- 当前用户==creater → `ServiceHandleException(409,"not allow exit.","您是当前团队创建者，不能退出此团队")`。否则 `@Transactional`：`permRelTenantRepository.deleteByUserIdInAndTenantId([userId], teamPK)` + `userRoleRepository.deleteByUserIdAndRoleIdIn(userIdStr, 团队角色ID)`。返回 200「退出团队成功」。

### 决策 5：响应对 7070 实测校准（已抓基线）
- pemtransfer：`{code:200,msg:"success",msg_show:"移交成功",data:{bean:{},list:[]}}`。
- modifyname：`{code:200,msg:"update success",msg_show:"团队信息修改成功",bean:完整to_dict}`。
- exit 成功：`{code:200,msg:"success",msg_show:"退出团队成功"}`；creater：HTTP 409 `{code:409,msg:"not allow exit.",msg_show:"您是当前团队创建者，不能退出此团队"}`。

## Risks / Trade-offs

- **[改 default 归属/成员]** pemtransfer/exit 改 creater/成员关系 → 联调用 viewer 可丢弃数据；pemtransfer 测后因 owner 已变需 **DB 直接还原** creater（移交后原 owner 失去 owner 权不能移回）；exit 用 viewer 自退（天然只删 viewer）。
- **[update_time 易变]** 校准排除该字段。
- **[modifyname 无码门槛]** 对齐 rainbond（路由无 perms 标签）；不挂 @RequiresPerms。

## Migration Plan

无 schema 变更。回滚移除 3 路由与服务。联调：已抓 7070 基线（docs/p1g-7070-reference.md）；写用 viewer，pemtransfer 测后 DB 还原 default.creater=700002。

## Open Questions

- modifyname 是否校验 new_team_alias 非空？rainbond `if new_team_alias:` 才更新，空则可能返回原样/无操作——以实测补充；本轮空别名按"不更新"或报错按 7070 行为定（默认非空才更新）。
