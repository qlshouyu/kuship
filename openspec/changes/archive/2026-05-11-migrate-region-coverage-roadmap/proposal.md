## Why

kuship-console 的 14 个 Region Operations 接口骨架自 `init-kuship-console` 落地以来，已通过 `migrate-console-*` 系列 change 分批补齐 `Tenant`、`Service`、`Helm`、`ServicePort`、`ServiceVolume`（基础）、`ServiceProbe`、`ServiceDependency`（基础）、`ServiceLifecycle`、`ServiceStatus`、`ServiceLog`、`Event`、`Cluster`（基础）、`Plugin`、`Backup`（基础）、`Monitor`（基础）、`Autoscaler` 共约 70 个 method。但对照 rainbond-console `www/apiclient/regionapi.py` 的 ~353 个 method 全集，仍有 **约 153 个 method、跨 18 个业务域** 未实现，覆盖率约 **20%**。

未实现部分集中在 UI 高频路径上：HTTP/TCP 域名管理、证书、集群节点、资源中心 Pod 详情、KubeBlocks、应用分享、应用导入导出、构建版本管理 等。结果是 kuship-ui 的"网关/路由"、"集群资源"、"数据库"、"应用市场分享"等页面打开后大面积空白或返回 stub 数据，迁移到 kuship-ui 的体验断点很多。

继续按"每个 PR 顺手补一两个 method"的方式推进会持续产生熵：接口职责漂移、命名风格不统一、controller 与 region API 边界不清、test 覆盖率参差。本路线图 change 不直接落代码，目标是 **把剩余 153 个 method 切成 18 个聚焦子 change，定义每个子 change 的范围、依赖、命名、共享规约，并明确 P0/P1/P2 推进顺序**，作为后续 region 补齐的母提案。

## What Changes

仅文档 / 规划，不写实现代码。

- 在 `kuship-console-app` capability 下新增一条 **Region API 覆盖度路线** Requirement，把 18 个子 change 的存在与命名作为正式承诺写入 spec，让"region 补齐"成为可追踪的工程脉络
- 产出三份指引文档（路线本体在 `design.md`，子 change 提案待办在 `tasks.md`）
  - 全景表：rainbond 业务域 ↔ kuship 当前覆盖率 ↔ 缺口 ↔ 优先级（P0/P1/P2）
  - 18 个子 change 的范围卡片（名称、目标 method 数、region API URL 前缀、涉及的 Operations 接口与本地 entity、controller、依赖、与 rainbond Python 引用文件锚点）
  - 共享规约：14 接口扩展原则、新增 Operations 接口的命名空间、controller 路径前缀回归测试、错误消息汉化兜底
- 不在本 change 内：任何 controller / service / entity / region client 的代码新增；任何 region API 调用实现；任何 schema 变更（实际由 rainbond-console Django migrations 拥有，kuship 一直 `validate`）

每个子 change 落地时遵循既定模式：自己起一个独立 OpenSpec change（`migrate-console-*`），不在本 epic 下塞代码。

## Capabilities

### Modified Capabilities

- `kuship-console-app`：新增 1 条 Requirement —— "Region API 覆盖度路线"。把 18 个 region 子 change 的存在、命名、优先级、依赖关系作为正式 spec 内容；后续每个子 change 落地后，需要更新此 Requirement 标注完成状态（在归档时 / 子 change 内反向更新）。

## Impact

- **代码**：无新增 / 无修改。
- **数据库**：不变。
- **OpenSpec 工程脉络**：新增 1 个 active change（本 change），其归档后衍生 18 个 `migrate-console-*` 子 change 进入 backlog。每个子 change 的命名、范围、依赖在 design.md 中钉死，避免后续命名漂移、范围塌陷。
- **风险**：路线图本身不会让现网行为变化；但若 P0 子 change 不被实际推进，UI 的对应页面会持续残缺。`tasks.md` 把"开 P0 子 change 提案"放在最前列，强迫顺序推进。
- **依赖**：本 change 完成后，第一个落地子 change 推荐 `migrate-console-gateway-domain`（P0 #1，UI 影响面最大、与已有 `ServiceDomain` / `ServiceTcpDomain` entity 衔接顺）。
