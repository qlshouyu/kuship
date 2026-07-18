# 对齐报告：GET /console/monitor/query

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/app_monitor.py` `MonitorQueryView.get`（**AlowAnyApiView 公开**）→ `region_api.get_query_data(region_name,"","?query="+q)` → region Prometheus 代理 `/api/v1/query`，body 原样返回（裸 Prometheus 格式，无信封）
- **kuship 实现**：`modules/region/controller/MonitorQueryController` + `MonitorQueryService`（RegionClient mTLS 透传）

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/monitor/query` |
| 鉴权 | **无需**（AllowAny 公开） |
| Query 参数 | `query`（promQL，默认 ""）、`region_name`（默认 ""） |

## 响应契约

裸 Prometheus 即时查询格式：`{"status":"success","data":{"resultType":"vector","result":[{"metric":{...},"value":[<ts float>, "<val>"]}]}}`；无数据时 `result:[]`。region 不可达时降级同款空向量。

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 匿名 + 不存在指标（空向量） | MATCH ✅ 逐字节一致（修复后） |
| `count(up)`（活体） | 结构/键序/精度格式一致；value 的时间戳与数值属查询时刻活体差异，按稳定字段 parity 判过 |
| Content-Type | 两侧均 `application/json` |

## 本轮修复

1. **公开路径缺失**：7070 为 AllowAny，8000 原要求 JWT 返 401。修复：`SecurityConfig.PUBLIC_PATHS` 加 `/console/monitor/query`。
2. **浮点重序列化差异**：8000 原把 body 重解析再序列化，Jackson 将时间戳 `1784367660.417` 输出为科学计数法 `1.784E9`。修复：`MonitorQueryService` 改为**原文 String 透传**（仅 readTree 校验合法性），controller `produces="application/json"`。

## Defer

- `GET /console/open/monitor/query`（MonitorQueryOverConsoleView）与组件级 `monitor/query(_range)`（依赖原生 promql-parser 二进制，见记忆「monitor 拐点」）未实现。
