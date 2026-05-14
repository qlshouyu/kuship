## ADDED Requirements

### Requirement: Region API 覆盖度路线（migrate-region-coverage-roadmap）

kuship-console 后端 SHALL 按本 Requirement 定义的 18 个聚焦子 change 完成对 rainbond `www/apiclient/regionapi.py` 中剩余 ~153 个 region method 的迁移；每个子 change MUST 独立 propose / design / tasks / specs / 归档，命名、优先级、依赖关系按下表锁定，不得绕开本路线随意拼合或拆细。

子 change 命名与优先级表：

| 优先级 | 子 change 名                              | 估计 method |
|--------|-------------------------------------------|-------------|
| P0     | migrate-console-cluster-extras            | 5           |
| P0     | migrate-console-gateway-domain            | 29          |
| P0     | migrate-console-gateway-certificate       | 5           |
| P0     | migrate-console-cluster-nodes             | 12          |
| P0     | migrate-console-resource-center           | 10          |
| P0     | migrate-console-volume-extras             | 6           |
| P0     | migrate-console-dependency-extras         | 3           |
| P0     | migrate-console-third-party-runtime       | 6           |
| P1     | migrate-console-kubeblocks                | 13          |
| P1     | migrate-console-app-share                 | 7           |
| P1     | migrate-console-monitor-extras            | 6           |
| P1     | migrate-console-build-versions            | 15          |
| P1     | migrate-console-grayrelease-finalize      | 3           |
| P2     | migrate-console-app-import-export         | 22          |
| P2     | migrate-console-governance-policy         | 12          |
| P2     | migrate-console-maven-setting             | 8           |
| P2     | migrate-console-service-labels            | 4           |
| P2     | migrate-console-backup-extras             | 5           |

约束：

- **聚焦原则**：每个子 change SHALL 覆盖一个 region API URL 前缀的子集，方法数 ≤ 30，可在 1-2 周内闭合
- **接口位置**：14 接口骨架内的扩展放 `infrastructure/region/api/<X>Operations.java`；新业务域接口放 `modules/<domain>/api/<X>Operations.java`
- **路径回归**：controller 路径与 rainbond `console/urls/__init__.py` 严格一致，trailing slash 兼容
- **错误兜底**：region 异常透传 `msg_show`，缺失才走 `RegionErrorMsgEnricher`
- **不打包**：跨 capability 的重构（region client / 全局响应包装 / mTLS 优化）不放入任何子 change，单独立 hardening
- **Service Env**：rainbond 历史选择本地为主 + 重启同步，本路线 SHALL NOT 迁移 `add_service_env` / `update_service_env` / `delete_service_env` 3 个 region method

#### Scenario: 路线图存在并被引用

- **WHEN** 团队成员需要决定下一个 region 补齐 PR 做哪部分
- **THEN** 在 `kuship-console/CLAUDE.md` 或本 Requirement 表中能找到 18 个候选子 change 的命名 + 优先级
- **AND** 不会出现"两个 PR 撞同一组 region method"的并发冲突，因为每个子 change 已绑定唯一 URL 前缀

#### Scenario: 子 change 落地时遵循路线规约

- **WHEN** 任一 `migrate-console-<area>` 子 change 进入 propose 阶段
- **THEN** 该子 change 的 design.md SHALL 在头部引用本 Requirement，并标注自己在表中的位置（P0/P1/P2 + 估计 method 数）
- **AND** SHALL 在 design.md 中给出完整的 region URL 前缀表（与本路线决策 4 的 URL 前缀分配表一致）
- **AND** SHALL 在 design.md 中给出 controller 路径与 rainbond `console/urls/__init__.py` 的行号锚点

#### Scenario: 路线图随子 change 迭代更新

- **WHEN** 任一子 change 完成并归档
- **THEN** 子 change 的归档 commit SHALL 反向更新本 Requirement 的表格，把对应行标注为已完成（在 capability spec 的归档版本中体现）
- **AND** 若实际 method 数与估计偏差 > 30%，子 change 的 design.md SHALL 解释偏差原因
