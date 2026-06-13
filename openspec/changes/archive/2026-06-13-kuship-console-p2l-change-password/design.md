## Context
`ChangeLoginPassword.post`：check_user_password(旧)→new!=new2→old==new→update_password(new<8 抛 PasswordTooShortError)。check_user_password：check_password("goodrain")恒过 else check_password(password)。update_password：set_password=encrypt(email+new)+save。成功 200「密码修改成功」。oauth 同步在非 oauth 环境跳过。纯 DB 写。

## Goals / Non-Goals
**Goals:** changepwd 写 + 对 7070 校准（成功/旧错/不一致/新旧同；viewer 测后还原）。
**Non-Goals:** 企业中心 oauth 同步、重置密码邮件。

## Decisions
### 决策 1：UserPasswordService.changePassword(user, old, new, new2)：① matches(email,"goodrain",stored)?true:matches(email,old,stored) 否则 400 旧密码错误；② new!=new2→400；③ old==new→400；④ new<8→400 密码不能小于8位；⑤ setPassword(encrypt(email+new))+save，返回成功
### 决策 2：UserPasswordController POST /console/users/changepwd（form @RequestParam password/new_password/new_password2，对齐登录 form 编码）；RequestContext.currentUser
### 决策 3：JWTAuthApiView 仅登录，改自己密码；非 oauth 环境不同步

## Risks / Trade-offs
- **[写密码]** 用 viewer 校准：改密→新密码登录验证→还原 Viewer@123；绝不动 interop。
- 校验顺序与文案严格对齐 rainbond。

## Migration Plan
无 schema 变更。回滚移除 service/controller。校准：viewer 全分支（成功+3 种 400）+还原。

## Open Questions
- changepwd 入参 form vs JSON——rainbond request.data 兼容；本环境用 form 校准（同登录）。
