# user-custom-configs-write Specification

## Purpose
TBD - created by archiving change kuship-console-p2n-custom-configs-write. Update Purpose after archive.
## Requirements
### Requirement: 批量创建/更新自定义配置
系统 SHALL 提供 `PUT /console/users/custom_configs`（对齐 `CustomConfigsUserCLView.put`）：请求体非列表→400「请求参数必须为列表」；否则对每项 `{key,value}`（无 `key` 跳过）按 `bulk_create_or_update` 处理（当前用户已存在该 key 且值为真→更新：该 key 全局删后重建；否则新建），bulk 写入（`user_nick_name`=当前用户），响应回显入参 list、`msg_show=操作成功`。

#### Scenario: 批量写入成功
- **WHEN** `PUT` 携带配置项列表
- **THEN** 这些配置被写入当前用户，响应回显入参 list、`msg_show=操作成功`，可经 GET 读回

#### Scenario: 非列表入参
- **WHEN** 请求体不是列表
- **THEN** 返回 400「请求参数必须为列表」

