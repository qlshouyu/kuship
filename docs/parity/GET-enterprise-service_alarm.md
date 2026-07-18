# 对齐报告：GET /console/enterprise/{eid}/service_alarm

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/enterprise.py` `ServiceAlarm.get`（EnterpriseAdminView——**该版本 initial 并不强制企业管理员，任意登录用户可访问**）。逻辑：企业无团队→空 list；有团队→遍历可用集群调 region `get_user_service_abnormal_status` 收集异常组件 id→组装 {service_cname, group_id, group_name, service_alias, service_id, tenant_id, region_name, tenant_name, tenant_alias}
- **kuship 实现**：`modules/enterprise/controller/ServiceAlarmController` + `ServiceAlarmService`

## 请求契约

| 项 | 值 |
|---|---|
| Method/Path | `GET /console/enterprise/{enterprise_id}/service_alarm` |
| 鉴权 | 登录即可（EnterpriseAdminView 该版本无 admin 强制） |
| 参数 | path: enterprise_id |

## 响应契约

`{code:200, msg:"team query success", msg_show:"查询成功", data:{bean:{}, list:[异常组件...]}}`，无异常组件时 `list:[]`。

## 校准结果（7070 vs 8000）

| 用例 | 结果 |
|---|---|
| interop（企业 admin） | MATCH ✅ 逐叶子+键序全一致（HTTP 200） |
| viewer（低权，无 enterprise_user_perm 行） | MATCH ✅ 两侧均 200 空 list，逐字节一致 |

## 本轮修复

无需修复，实现即一致。

## 未覆盖分支（本环境不可测）

- 存在异常组件的 populated 分支（需 region 上有 abnormal 状态组件；本空闲集群无）。项字段组装逻辑以源码 1:1 移植为准，后续有异常组件环境时补校。
