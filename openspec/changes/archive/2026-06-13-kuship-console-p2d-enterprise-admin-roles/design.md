## Context
`AdminRolesView.get`：遍历 perms.py `ENTERPRISE` 的键收集角色名，返回 list。纯计算无 DB。kuship `PermsCatalog.ENTERPRISE` 是 LinkedHashMap（admin、app_store 顺序固定）。

## Goals / Non-Goals
**Goals:** GET admin/roles 返回 ENTERPRISE 键列表，对 7070 校准。
**Non-Goals:** 企业 info、regions、admin 用户写。

## Decisions
### 决策 1：复用 PermsCatalog.ENTERPRISE.keySet()（保序），controller 直接返回
### 决策 2：JWTAuthApiView——仅登录，无 @RequiresPerms

## Risks / Trade-offs
- 极小、纯计算、确定；ENTERPRISE 顺序 LinkedHashMap 保证 admin,app_store。

## Migration Plan
无 schema 变更，无 DB。校准：直接 deep-diff list。
