# 对齐报告：集群节点/组件三端点（nodes / rbd-components / nodes/{n}/action）

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/enterprise.py` `GetNodes.get` / `RainbondComponents.get` / `NodeAction.post`（均 EnterpriseAdminView，该版本仅要求登录）
- **kuship 实现**：`modules/region/controller/RegionClusterController` + `RegionClusterService`

## 1. GET /console/enterprise/{eid}/regions/{region_name}/nodes

- 契约：`bean` = 角色计数（如 `{"control-plane":1}`），`list` = 节点数组 `{name,status,role[],unschedulable,arch,req_cpu,cap_cpu,req_memory,cap_memory}`，msg_show=获取成功
- 结果：**MATCH ✅** 逐叶子+键序全一致（空闲集群下活体 req_* 两侧相同）

## 2. GET /console/enterprise/{eid}/regions/{region_name}/rbd-components

- 契约：`list` = rbd 平台组件 `{name,run_pods,all_pods,status,pods[{pod_name,create_time,status,pod_ip,all_container,run_container,restart_count}]}`
- **顺序不属于契约**：来自 region Go map 迭代，**7070 自身连续两次请求顺序都不同**。按名称排序后集合级对比：**MATCH ✅**（信封、11 个组件项内容、两级键序全一致）
- 校准方法备忘：此类端点用排序后集合 diff，不用位置序 diff

## 3. POST /console/enterprise/{eid}/regions/{region_name}/nodes/{node_name}/action

- 契约：body `{"action": "..."}`，合法集合 `[unschedulable, reschedulable, down, up, evict]`（两侧代码一致）；非法/缺失 → HTTP 400 `{code:400,msg:"failed",msg_show:"暂不支持当前操作"}`；合法 → 转发 region `operate_node_action`，bean=region 返回
- 结果（按"写端点只核错误契约"纪律，未实执行节点操作）：
  - 非法 action：**MATCH ✅** 逐字节一致（HTTP 400）
  - 空 body：**MATCH ✅** 逐字节一致

## 本轮修复

无需修复，三端点实现即一致。

## 未覆盖分支

- node action 成功路径（会真实操作集群节点，单节点集群不可试；后续多节点环境用可丢弃节点验证）
- `GET nodes/{node_name}`（单节点详情）、labels/taints/container 同族端点未实现，404 backlog。
