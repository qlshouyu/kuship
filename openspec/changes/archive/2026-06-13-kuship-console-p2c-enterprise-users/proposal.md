## Why

企业域读起步：企业用户列表（`enterprise/{eid}/users`）是纯 console 库、有真实数据（interop/viewer）、可完整对 7070 校准的读接口。先落它作为企业域读底座。

## What Changes

- 企业用户列表（对齐 `EnterPriseUsersCLView.get` + `get_user_by_eid`）：`GET /console/enterprise/{enterprise_id}/users?page=&page_size=&query=` →
  - `user_info` 按 `enterprise_id` 过滤、按 `user_id` 升序；`query` 非空时模糊匹配 `nick_name`/`real_name`/`phone`/`email`；分页（默认 page=1/page_size=10），`data` 顶层带 `page`/`page_size`/`total`；
  - 每项 `{email, nick_name, real_name(为空取 nick_name), user_id, phone, create_time, default_favorite_name, default_favorite_url}`；`create_time` 为 ISO（DRF 默认序列化）；`default_favorite_*` 当前恒 null（无收藏域，已实测两用户均 null）。
- **不包含**：用户创建/编辑/删除（`POST/UD`）、收藏域（default_favorite 真实值）、企业 admin 角色管理、region 列表。本轮仅企业用户列表读。

## Capabilities

### New Capabilities
- `enterprise-user-read`: 企业用户列表读，纯 console 库。

## Impact

- 代码：新增 `modules/enterprise` 下企业用户列表服务与 controller；复用 `UserInfoRepository.findByEnterpriseId`。
- 数据：只读 `user_info`；无 schema 变更。
- 接口：新增 `GET /console/enterprise/{enterprise_id}/users`；对 7070 deep-diff 校准（interop/viewer 真实数据，含分页/query/create_time 精度）。
- 鉴权：JWTAuthApiView（仅登录，无团队权限码门槛）。
- 简化：`default_favorite_*` 恒 null（无收藏域；两测试用户实测均 null，校准通过）。
