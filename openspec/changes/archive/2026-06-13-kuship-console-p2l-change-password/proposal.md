## Why
修改登录密码 `users/changepwd` 是纯 console 库写、可对 7070 校准（viewer 测后还原），补齐账号域第一个写接口。

## What Changes
- 修改密码（对齐 `ChangeLoginPassword.post`）：`POST /console/users/changepwd`（form: password/new_password/new_password2）→
  - 旧密码校验（`check_user_password`：库密码=encrypt(email+"goodrain") 时恒过——默认密码 quirk；否则比对 encrypt(email+password)）失败→400「旧密码错误」；
  - new_password != new_password2 → 400「两个密码不一致」；password == new_password → 400「新旧密码一致」；new_password 长度 <8 → 400「密码不能小于8位」；
  - 通过 → 更新 `user_info.password = encrypt(email + new_password)` 并保存，200「密码修改成功」。
- **不包含**：企业中心 oauth 同步（非 oauth 环境跳过）。

## Capabilities
### New Capabilities
- `user-change-password`: 修改登录密码（纯 console 库写）。

## Impact
- 代码：`modules/account` 新增 UserPasswordService + controller；复用 PasswordEncryptor、UserInfoRepository.save。
- 数据：写当前用户 `user_info.password`；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录），改当前登录用户自己的密码。
- 联调：用 viewer 改密→验登录→还原；绝不动 interop 密码。
