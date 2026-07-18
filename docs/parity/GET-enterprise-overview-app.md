# 对齐报告：GET /console/enterprise/{eid}/overview/app

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/enterprise.py` `EnterpriseAppOverView.get`（JWTAuthApiView）：无可用集群→`general_message(404,"no found regions","查询成功")` HTTP 200；有集群→`enterprise_services.get_enterprise_runing_service`（统计 service_group 应用与组件的 total/running/closed，running 数来自 region 运行态）
- **kuship 实现**：`modules/enterprise/controller/EnterpriseAppOverviewController` + `EnterpriseAppOverviewService`

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/enterprise/{enterprise_id}/overview/app` |
| 鉴权 | 需要（GRJWT） |
| 参数 | path: enterprise_id |

## 响应契约

- 有集群：`{code:200, msg:"success", msg_show:"查询成功", data:{bean:{service_groups:{total,running,closed}, components:{total,running,closed}}, list:[]}}`
- 无可用集群：`{code:404, msg:"no found regions", msg_show:"查询成功", data:{bean:{},list:[]}}`，**HTTP 200**（msg_show 仍是「查询成功」，rainbond 原样如此）

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| 真实 eid（1 应用 1 组件，均 closed） | MATCH ✅ 逐叶子+键序全一致 |
| 不存在 eid（无集群分支） | MATCH ✅ 逐字节一致，HTTP 均 200 |

## 本轮修复

无需修复，实现即一致。

## 备注

- running 计数依赖 region 运行态；当前均 closed。有 running 组件时按稳定字段 parity 复核（可用探针组件配方）。
