## ADDED Requirements

### Requirement: 修改登录密码
系统 SHALL 提供 `POST /console/users/changepwd`（对齐 `ChangeLoginPassword.post`），改当前登录用户密码。校验顺序：旧密码错误→400「旧密码错误」；`new_password`≠`new_password2`→400「两个密码不一致」；旧==新→400「新旧密码一致」；新密码<8 位→400「密码不能小于8位」；通过则更新 `user_info.password`（encrypt(email+new)），返回 200「密码修改成功」。旧密码校验对齐 `check_user_password`：库密码为默认（encrypt(email+"goodrain")）时恒通过。

#### Scenario: 改密成功
- **WHEN** 旧密码正确、两新密码一致且≠旧、长度≥8
- **THEN** 更新密码，返回 `code=200`、`msg_show=密码修改成功`，可用新密码登录

#### Scenario: 旧密码错误
- **WHEN** 旧密码不正确（且库密码非默认）
- **THEN** 返回 400「旧密码错误」，不改

#### Scenario: 两密码不一致 / 新旧一致
- **WHEN** new_password≠new_password2，或 password==new_password
- **THEN** 分别返回 400「两个密码不一致」/「新旧密码一致」，不改
