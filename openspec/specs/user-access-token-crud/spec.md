# user-access-token-crud Specification

## Purpose
TBD - created by archiving change kuship-console-p2m-access-token-crud. Update Purpose after archive.
## Requirements
### Requirement: 创建访问令牌
系统 SHALL 提供 `POST /console/users/access-token`（对齐 `UserAccessTokenCLView.post`）：`note` 为空→400「注释不能为空」；否则生成 `access_key`（sha1(随机) 40 位 hex），`expire_time`=`age` 非空时为 now+age 秒（整型）否则 null，存库，返回 `{ID,note,user_id,access_key,expire_time}`。

#### Scenario: 创建成功
- **WHEN** `POST` 携带非空 `note`
- **THEN** 创建令牌，返回 bean（含随机 access_key、user_id、note），并持久化

#### Scenario: note 为空
- **WHEN** `note` 为空
- **THEN** 返回 400「注释不能为空」

### Requirement: 查看/重生成/删除访问令牌
系统 SHALL 提供 `GET/PUT/DELETE /console/users/access-token/{id}`（对齐 `UserAccessTokenRUDView`）：`GET` 返回当前用户该令牌 `{note,expire_time,user_id,ID}`，无→404「未找到该凭证」；`PUT` 重生成 access_key 并返回 to_dict；`DELETE` 删除该令牌，200。

#### Scenario: 查看不存在
- **WHEN** `GET .../{id}` 该用户无此令牌
- **THEN** 返回 404「未找到该凭证」

#### Scenario: 重生成与删除
- **WHEN** `PUT`/`DELETE .../{id}` 操作当前用户令牌
- **THEN** 分别换新 access_key（返回 bean）/删除（200 success）

