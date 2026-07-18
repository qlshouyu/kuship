# 对齐报告：GET /console/update/versions（附全局 401 信封修复）

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/upgrade.py` `UpgradeVersionLView.get`（JWTAuthApiView）→ `fetch_json_data()` 拉取 `VERSION_INFO_URL`（默认 `https://get.rainbond.com/upgrade-versions.json`，超时 2s；云市场禁用或拉取失败→返回 `[]`）→ 版本号倒序排序
- **kuship 实现**：`modules/platform/controller/UpgradeVersionController` + `UpgradeVersionService`（`@SkipResponseWrapper` 裸数组）

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/update/versions` |
| 鉴权 | 需要（GRJWT） |
| 参数 | 无 |

## 响应契约

**裸 JSON 数组**（非 general_message 信封）：`["v6.9.4-release", "v6.9.3-release", ...]`，倒序；外网不可达/云市场禁用时 `[]`。

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 带 token | MATCH ✅ 35 个版本逐元素一致（仅 JSON 空格风格差异：Django `json.dumps` 带空格 vs Jackson 紧凑，非语义，放过） |
| 无 token | MATCH ✅ 401 裸信封逐字节一致（修复后，见下） |
| 伪造 token | MATCH ✅ 401 裸信封逐字节一致（修复后，见下） |

## 本轮修复（全局性，影响所有受保护端点）

**未认证/token 无效的 401 信封差异**：
- 7070：`base.py` 里所有 token 校验失败（未提供/过期/解码失败/不合法/用户不存在）统一抛 `AuthenticationInfoHasExpiredError`，exception handler **忽略具体 msg**，恒返固定裸三字段体 `{"code":10405,"msg":"Signature has expired.","msg_show":"身份认证信息失败，请登录"}`（**无 data 键**）。
- 8000 原先：① 信封多 `data:{bean:{},list:[]}`；② 不同失败分支返不同 msg（如 `invalid token: malformed token`）。
- 修复：`RestAuthenticationEntryPoint` 与 `JwtAuthenticationFilter.write401` 均改为 `GeneralMessage.bare(10405, "Signature has expired.", "身份认证信息失败，请登录")` 固定文案。

## 接受的偏离

- `Authorization: GRJWT `（有前缀无 token）等畸形头：7070 返 403 且 body 携带 `error_type/error_trace`（Python traceback 噪声），kuship 按缺 token 走统一 401 裸信封。UI 不会发出此类请求，且对齐 traceback 属对齐 rainbond 内部噪声，按"不对齐 rainbond 自身缺陷"原则放过。

## Defer

- `GET /console/update/versions/{version}`、`GET /console/update/versions/{version}/images`（UI 升级详情页用）未实现，404 backlog。
