## Why
P2-k 已做自定义配置读，补齐其写 `PUT /console/users/custom_configs`（bulk upsert），纯 console 库写，对 7070 校准。

## What Changes
- 批量创建/更新自定义配置（对齐 `CustomConfigsUserCLView.put` + `bulk_create_or_update`）：`PUT /console/users/custom_configs`（body 为 list，否则 400「请求参数必须为列表」）→ 对每项 `{key,value}`（无 key 跳过）：当前用户已存在该 key 且其值为真→视为更新（其 key 全局删后重建），否则新建；全部 bulk insert（user_nick_name=当前用户，update_time null）；响应回显入参 list、`msg_show=操作成功`。
- **不包含**：删除单个配置（rainbond 无该接口）；GET 已在 P2-k。

## Capabilities
### New Capabilities
- `user-custom-configs-write`: 用户自定义配置批量创建/更新（纯 console 库写）。

## Impact
- 代码：复用 P2-k 的 ConsoleConfig 实体；ConsoleConfigRepository 补 deleteByKeyIn；UserCustomConfigsService 补 bulkCreateOrUpdate；controller 补 PUT。
- 数据：写 console_config（按 key 全局删 + insert，对齐 rainbond）；无 schema 变更。
- 鉴权：JWTAuthApiView（仅登录），写当前用户配置。
- 联调：interop PUT 临时配置→GET 验→清理。
