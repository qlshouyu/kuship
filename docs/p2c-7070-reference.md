# P2-c（企业用户列表读）7070 校准基线
> 企业 b16bf28d…；真实用户 interop(700002)/viewer(700003)。

## GET /console/enterprise/{eid}/users?page=&page_size=&query=
- `data`: {bean:{}, list:[...], page_size, page, total}（顶层带 page_size/page/total）。
- 项：{email, nick_name, real_name(null→nick_name), user_id, phone, create_time, default_favorite_name, default_favorite_url}。
- **create_time = ISO 带微秒**（DRF 默认），如 `2026-06-12T21:09:00.908434`；**Jackson 默认 LocalDateTime 序列化与之逐字符一致**（无需显式 formatter）。注意：这与 P1-g tenant to_dict 的空格格式 `yyyy-MM-dd HH:mm:ss` 不同——两端序列化口径不同（user.create_time 走 DRF ISO，tenant.to_dict 走自定义空格）。
- default_favorite_* 恒 null（无收藏域；interop/viewer 实测均 null）。
- 排序 user_id 升序；query 模糊 nick/real/phone/email；real_name null→nick_name。

## 实跑校准
- 全量 / query=interop / page=1&page_size=1 三案 deep-diff 8000 vs 7070 全 0（含 create_time 微秒）。✓
- 只读，无数据改动。
