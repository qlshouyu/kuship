# 对齐报告：GET /console/custom_configs

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/custom_configs.py` `CustomConfigsCLView.get`（**BaseApiView，公开无鉴权**）→ `custom_configs_service.list()` → `console_config` 表 `user_nick_name=""` 的平台级配置
- **kuship 实现**：`modules/platform/controller/CustomConfigsController` + `CustomConfigsService`

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/custom_configs` |
| 鉴权 | **无需**（rainbond BaseApiView 公开；带 token 也可） |
| 参数 | 无 |

## 响应契约

信封 `{code:200, msg:"success", msg_show:"操作成功", data:{bean:{}, list:[...]}}`。
list 项字段（键序固定，`.values()` 全列）：

```json
{"ID": 3, "key": "applicationInfo", "value": "True", "description": "", "update_time": null, "user_nick_name": ""}
```

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 带 GRJWT token | MATCH ✅ 逐叶子+键序全一致 |
| 无鉴权头 | MATCH ✅（修复后）逐字节一致，HTTP 200 |

## 本轮修复

1. **公开路径缺失**：8000 原对无鉴权请求返 401/10405，7070 为 BaseApiView 公开返 200 数据。修复：`SecurityConfig.PUBLIC_PATHS` 加入 `/console/custom_configs`。

## Defer

- `PUT /console/custom_configs`（平台级批量写，rainbond 同样公开）kuship 未实现，返 404（未实现端点统一约定）。
