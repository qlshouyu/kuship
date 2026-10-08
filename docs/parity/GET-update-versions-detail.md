# 对齐报告：GET /console/update/versions/{version} 与 /{version}/images

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/upgrade.py` `UpgradeVersionRView.get` / `UpgradeVersionImagesView.get`（JWTAuthApiView）→ `fetch_json_data()`（VERSION_INFO_URL，2s 超时）→ 按 version 命中取 `detail` / `images` 字段，`or {}` 兜底
- **kuship 实现**：`UpgradeVersionController` + `UpgradeVersionService.versionDetail/versionImages`（复用 fetchData）

## 契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/update/versions/{version}`、`GET .../{version}/images` |
| 鉴权 | 需要（GRJWT） |
| 响应 | **裸 JSON 对象**（无信封）。detail：`{update_time, html_url, tag_name, body}`；images：`{rbd-api: <image>, ...}`。清单拉取失败/版本未命中/字段假值 → `{}` |

## 校准结果

| 用例 | 结果 |
|---|---|
| v6.9.4-release detail | MATCH ✅ 逐叶子+键序全一致 |
| v6.9.4-release images | MATCH ✅ |
| 不存在版本 → {} | MATCH ✅ |

本轮实现（原为 404 backlog），Python `or {}` 假值语义（null/空串/空容器→{}）已复刻。
