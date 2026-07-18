## Why

7777 前端(kuship-ui)在真实导航中逐个调用的接口都打到 kuship-console(8000)。这些接口虽已逐个实现并各自声称对齐 7070，但缺少一次以**前端真实调用面**为驱动的端到端校准:同一前端、同一鉴权、同一参数下，把 8000 的返回与 rainbond(7070)逐字段 deep-diff，把残留差异(字段缺失/类型/顺序/默认值/msg/分页)一次性收口。

## What Changes

- 以 7777 前端导航旅程为顺序，对 kuship-console **已实现的全部端点**(37 个 Controller)逐个执行 `调用 8000 → 调用 7070 → deep-diff → 修正`，差异处改 kuship-console 实现使其与 7070 对齐。
- 校准遵循既有"7070 校准打法":鉴权态/参数对齐后只比稳定字段，易变字段(时间戳/实时状态/运行态)做形状校验而非值校验。
- 产出每个端点的差异记录与修正;**纯 bug 修正**(实现未达既有 spec)不改 spec;若 deep-diff 揭示既有 spec 描述有误，单独标注为对应 spec 的 delta。
- **不包含**:实现 7777 调用但 kuship-console 尚未实现(返回 404)的端点——那是后续 feature 工作，不属于"对齐"。

## Capabilities

### New Capabilities
- `frontend-api-parity`: 以 7777 前端真实调用面为驱动的接口返回对齐契约——已实现端点在同鉴权/同参数下与 7070 逐字段 deep-diff 一致(稳定字段值对齐、易变字段形状对齐),并固化按导航旅程的逐个校准清单与方法。

### Modified Capabilities
<!-- 仅当 deep-diff 揭示既有端点 spec 的 REQUIREMENT 描述有误时，才在 apply 阶段追加对应能力的 delta。提案阶段不预判，留空。 -->

## Impact

- 代码:对存在差异的 Controller/Service 做局部修正(响应字段/默认值/排序/msg/分页),不新增端点。
- 数据:只读校准，无 schema 变更。
- 鉴权:复用既有 JWT/登录态;校准时 8000 与 7070 使用等价账号与 cookie/token。
- 前置:7070、8000、7777 三者均需运行(已确认在线)。
- 验证:每个端点 deep-diff 通过 + 受影响模块单测全绿 + 全量构建。
