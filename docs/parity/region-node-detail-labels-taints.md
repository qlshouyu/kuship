# 对齐报告：节点详情 / labels / taints（附 region 错误契约横切修复）

- **日期**：2026-07-18
- **rainbond 实现**：`console/views/enterprise.py` `GetNode` / `NodeLabelsOperate` / `NodeTaintOperate`（EnterpriseAdminView，该版本仅要求登录）；region 路径 `/v2/cluster/nodes/{n}/detail|labels|taints`
- **kuship 实现**：`RegionClusterController` + `RegionClusterService`（本轮新增，原 404 backlog）

## 1. GET .../nodes/{node_name}

- 契约：bean=19 字段定序（name/ip(external 优先)/container_runtime/architecture/roles/os_version/unschedulable/create_time/kernel/os_type/req_cpu/cap_cpu/req_memory÷1000/cap_memory÷1000/req_root_partition÷1024³/cap_root_partition/cap_docker_partition/req_docker_partition/status(Ready 判定+SchedulingDisabled 后缀)），msg_show=获取成功
- 结果：**稳定字段 parity 通过**——17 字段+键序逐字节一致；`req_root_partition/req_docker_partition` 为磁盘占用活体值（实测 7070 自身 4 秒内也在小数第 5 位漂移），属时刻性差异

## 2. GET/PUT .../nodes/{node_name}/labels、GET/PUT .../taints

- 契约：GET labels bean=region body.bean（map）；GET taints **bean=region body.list**（rainbond 原样怪癖）；PUT body 分别取 `labels`(缺省{})/`taints`(缺省[]) 作为 region PUT body 本体，msg_show=操作成功，PUT taints 返回 list=
- 结果：GET 两端点 MATCH ✅ 逐叶子+键序全一致；PUT 按纪律只核错误契约（不存在节点）——**逐字节一致**（见下）；PUT 成功路径未实测（会真改节点标签/污点）

## 3. 横切修复：region API 错误契约（影响全部 region 依赖端点）

rainbond `base.py` 对 `CallApiError` 的渲染（裸信封无 data 键）：
- region 返回 404 → HTTP 404 `{code:404, msg:"region no found this resource", msg_show:"数据中心资源不存在"}`
- 其余 → HTTP 400 `{code:400, msg:{apitype:"Not specified", url, method, httpcode, body}, msg_show:"数据中心操作故障 <body.msg>"}`（**msg 是结构化 dict**）

kuship 原为自制 `500 集群请求失败`。修复：
- 新增 `RegionCallException`（继承 ServiceHandleException，既有服务层 catch/透传行为不变）
- `RegionClient` 非 2xx 时抛出，携带 url（**用 region_info 配置值而非 REGION_URL_OVERRIDE 实际值**，保证报错体与 7070 逐字节一致）/method/httpcode/解析后的 body
- `GlobalExceptionHandler` 新增渲染分支（`@SkipResponseWrapper` 防止被信封包装）

校准：不存在节点的 GET labels 与 PUT taints，7070 vs 8000 **响应体逐字节一致**（HTTP 400）。

## 未覆盖

- PUT labels/taints 成功路径（真实写，待多节点/可丢弃环境）；region 404 分支（本 region API 对 nodes 未知资源返 500 非 404，404 分支按源码移植）；InvalidLicenseError/CallApiFrequentError 等其余 region 异常分支。
