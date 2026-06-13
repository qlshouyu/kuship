## Context
令牌 CRUD（user_access_key）。create：note 必填、access_key=sha1(os.urandom(24)).hexdigest()(40hex)、expire_time=age?(time()+age):null、to_dict 全列{ID,note,user_id,access_key,expire_time}。get/{id}：按 user+id .values(note,expire_time,user_id,ID)，404 未找到该凭证。put：regenerate access_key+save。delete：删，200。纯 DB 写。

## Goals / Non-Goals
**Goals:** 令牌 create/get/put/delete + 对 7070 校准（确定性分支 diff，随机 access_key 验结构+持久化）。
**Non-Goals:** access_key 认证用途、age 过期逻辑细节。

## Decisions
### 决策 1：复用 UserAccessKey 实体；repo 补 findByUserIdAndId(Integer,Integer)、save、delete
### 决策 2：generateKey=SHA1(SecureRandom 24B)→40hex；expire_time=age?(System.currentTimeMillis()/1000+age):null(Integer)
### 决策 3：create 返回 to_dict {ID,note,user_id,access_key,expire_time}（access_key/ID 随机，校准验结构+DB+回读）；get/{id} {note,expire_time,user_id,ID} 同 P2-j 顺序，404 未找到该凭证；put to_dict；delete message(200 success)
### 决策 4：唯一约束 (note,user_id)——重复 note 由 DB 约束(本轮不显式校验重复，rainbond catch IntegrityError→令牌用途不能重复；defer 或简单透传)。JWTAuthApiView 仅登录，操作当前用户

## Risks / Trade-offs
- **[随机 access_key/ID]** create 响应不可 byte-diff→验 keys/note/user_id/expire_time + access_key 40hex + DB 持久化；确定性分支(note 空 400/get 404/delete 200)对 7070 diff。
- **[note 唯一]** 用唯一 note 校准，测后删。
- **[IntegrityError 重复 note]** rainbond→「令牌用途不能重复」；本轮 defer 重复校验（用唯一 note 不触发）。

## Migration Plan
无 schema 变更。校准：interop 建临时令牌→get/put/delete→清理。

## Open Questions
- create to_dict 字段顺序——以 7070 实测为准（diff keys 集合即可，access_key/ID 随机除外）。
