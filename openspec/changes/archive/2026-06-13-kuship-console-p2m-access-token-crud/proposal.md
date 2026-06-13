## Why
P2-j 已做令牌列表读，补齐其写：创建/查看/重生成/删除（`users/access-token` POST、`users/access-token/{id}` GET/PUT/DELETE），纯 console 库写，对 7070 校准。

## What Changes
- 创建令牌（对齐 `UserAccessTokenCLView.post`）：`POST /console/users/access-token`（note 必填否则 400「注释不能为空」，age 可选秒数）→ access_key=sha1(随机)40hex，expire_time=age?(now+age):null，存库，返回 to_dict bean `{ID,note,user_id,access_key,expire_time}`。
- 查看（`UserAccessTokenRUDView.get`）：`GET /console/users/access-token/{id}` → 当前用户该令牌 `{note,expire_time,user_id,ID}`；无→404「未找到该凭证」。
- 重生成（put）：`PUT /console/users/access-token/{id}` → 换新 access_key，bean to_dict；无→404。
- 删除（delete）：`DELETE /console/users/access-token/{id}` → 删，200 success。
- **不包含**：令牌鉴权用途（access_key 认证）等。

## Capabilities
### New Capabilities
- `user-access-token-crud`: 用户访问令牌创建/查看/重生成/删除（纯 console 库写）。

## Impact
- 代码：复用 P2-j 的 UserAccessKey 实体；UserAccessKeyRepository 补 findByUserIdAndId/save/delete；UserAccessTokenService 补 create/getById/regenerate/delete；controller 补 POST + RUD(/{id})。
- 数据：写 user_access_key；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录），操作当前用户自己的令牌。
- 联调：用 interop 建临时令牌→查/重生成/删→清理。
