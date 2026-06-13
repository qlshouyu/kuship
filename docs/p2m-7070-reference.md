# P2-m（访问令牌 CRUD）7070 校准基线
- `POST /console/users/access-token`(form note 必填/age?)：note 空→400「注释不能为空」(note can't be null)；成功→200 msg/msg_show=null，bean to_dict {ID,note,user_id,access_key(sha1(随机)40hex),expire_time(age?now秒+age:null)}。
- `GET /console/users/access-token/{id}`：{note,expire_time,user_id,ID}；无→404「未找到该凭证」(no found access key)。
- `PUT /{id}`：重生成 access_key，返回 to_dict。
- `DELETE /{id}`：删，200 msg=success msg_show=null。
- 校准(interop)：note空400/get/{id}/404/delete 两端逐字节一致；create 因 ID/access_key 随机不 byte-diff——验 bean keys+note/user_id/expire_time+access_key 40hex+DB 持久化；put 验 access_key 变更。测毕清理(interop 令牌 0)。单测 115/115。
- generate_key=SHA1(SecureRandom 24B)40hex；expire_time int。唯一约束(note,user_id)——重复 note 的 IntegrityError(令牌用途不能重复)本轮 defer(用唯一 note)。
