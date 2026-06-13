# P2-l（修改密码 users/changepwd POST）7070 校准基线
- `POST /console/users/changepwd`（form: password/new_password/new_password2，改当前登录用户）。
- 校验顺序：旧密码错误→400「旧密码错误」(old password error)；new!=new2→400「两个密码不一致」(two password disagree)；old==new→400「新旧密码一致」(old and new password agree)；new<8→400「密码不能小于8位」；通过→更新 user_info.password=encrypt(email+new)，200「密码修改成功」(change password success)。
- check_user_password quirk：库密码=encrypt(email+"goodrain")时旧密码校验恒过。
- 校准（viewer）：3 种 400 两端逐字节一致；8000 改密 Viewer@123→NewViewer@1 → 新密码登录 200 → 还原 Viewer@123 → 登录 200。viewer 密码已还原。单测 110/110。
- 写共享库 user_info.password；联调用 viewer 测后还原，勿动 interop。oauth 同步(企业中心)非 oauth 环境跳过。
