# 对齐报告：GET /console/enterprise/{eid}/monitor

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/enterprise.py` `EnterpriseMonitor.get`（JWTAuthApiView）：取企业可用集群；无集群→`general_message(404,"no found",None)`（**HTTP 仍 200**）；有集群→逐集群调 region `get_region_resources` 累加 cap_mem/req_mem/cap_cpu/req_cpu
- **kuship 实现**：`modules/enterprise/controller/EnterpriseMonitorController` + `EnterpriseMonitorService`

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/enterprise/{enterprise_id}/monitor` |
| 鉴权 | 需要（GRJWT） |
| 参数 | path: enterprise_id |

## 响应契约

- 有集群：`{code:200, msg:"success", msg_show:null, data:{bean:{total_regions, memory:{used,total}, cpu:{used,total}}, list:[]}}`（**msg_show 为 null**，cpu 可为小数如 1.1）
- 无可用集群（含企业不存在）：`{code:404, msg:"no found", msg_show:null, data:{bean:{},list:[]}}`，**HTTP 200**

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 真实 eid（1 集群） | MATCH ✅ 逐叶子+键序全一致（含活体 used 值，空闲集群下两侧相同） |
| 不存在 eid（无集群分支） | MATCH ✅ 逐字节一致，HTTP 均 200 |

## 本轮修复

无需修复，实现即一致。

## 备注

- memory.used/cpu.used 为活体指标，繁忙集群下允许两侧时刻性差异（按稳定字段 parity 原则判定）。
