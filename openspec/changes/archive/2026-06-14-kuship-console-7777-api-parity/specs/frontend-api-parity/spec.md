## ADDED Requirements

### Requirement: 前端调用面逐字段对齐 7070

kuship-console(8000)对 7777 前端真实调用的**每个已实现端点**，在与 7070 等价的鉴权态与请求参数下，其 JSON 返回 SHALL 与 rainbond(7070)对应端点逐字段一致:稳定字段(`code`/`msg`/`msg_show`/业务字段名、类型、默认值、列表排序、分页 `total`/`page`/`page_size`、`bean` 默认值)值对齐;易变字段(时间戳、实时运行状态、动态资源用量)形状(字段存在性与类型)对齐、值不做断言。

#### Scenario: 已实现端点稳定字段对齐
- **WHEN** 以同一账号/cookie/token 与同一参数分别调用 8000 与 7070 的同一已实现端点
- **THEN** 两侧响应在稳定字段上 deep-diff 无差异(字段名、类型、默认值、顺序、`msg`/`msg_show`、分页结构一致)

#### Scenario: 易变字段按形状校验
- **WHEN** 端点返回含时间戳/实时状态/运行态等易变字段
- **THEN** 仅校验字段存在与类型一致，不断言具体值，避免因环境差异误判

#### Scenario: 未认证/错误契约对齐
- **WHEN** 在缺失或非法鉴权、或参数非法等错误路径下调用端点
- **THEN** 8000 的错误响应(HTTP 状态、body `code`、`msg`)与 7070 一致

### Requirement: 以导航旅程为序的逐个校准清单

本能力 SHALL 固化一份按 7777 前端导航旅程排序的端点校准清单(bootstrap → 企业 → 团队 → 集群 → 应用 → 组件)，逐个端点执行 `调用 8000 → 调用 7070 → deep-diff → 修正`，并记录每个端点的差异与修正结论。

#### Scenario: 逐个推进并记录
- **WHEN** 校准清单中的某端点完成 deep-diff
- **THEN** 记录其差异项与修正(或"已一致")结论，再推进下一个端点

#### Scenario: 修正仅收口实现差异
- **WHEN** deep-diff 发现 8000 与 7070 不一致
- **THEN** 修改 kuship-console 实现使其对齐 7070(不修改既有端点 spec)，除非差异揭示既有 spec 描述本身有误

### Requirement: 范围限定为已实现端点

本能力 SHALL 只覆盖 kuship-console 已实现并能被 7777 前端正常调用(非 404)的端点;前端调用但 console 尚未实现的端点不在本次对齐范围内。

#### Scenario: 未实现端点跳过
- **WHEN** 某前端调用的端点在 kuship-console 返回 404(尚未实现)
- **THEN** 标记为后续 feature 工作并跳过，不在本次对齐中实现
