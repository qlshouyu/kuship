## 1. 实现
- [x] 1.1 UserPasswordService.changePassword（旧密码 quirk + 3 校验 + 长度 + 更新保存）
- [x] 1.2 UserPasswordController POST /console/users/changepwd（form 参数）
- [x] 1.3 单测：成功/旧错/不一致/新旧同/过短
## 2. 校准
- [x] 2.1 viewer 实跑：成功(改密→新密码登录 200→还原)、旧错 400、不一致 400、新旧同 400；对 7070 文案/状态一致
- [x] 2.2 全量构建+单测；docs；openspec 校验归档；确认 viewer 密码已还原
