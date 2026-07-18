# kuship-console 逐端点对齐报告索引

每个端点对照 rainbond-console(7070) 实跑 deep-diff 校准后，在本目录记录一份对齐报告。
早期批次基线见 `docs/p1a~p3u-7070-reference.md`（55+ 端点，均已对齐归档）。

校准环境：docker `kuship-rainbond`(7070) + `kuship-mysql`(共享 console 库) + kuship-console(8000, 同源 SECRET_KEY)。
校准用户：`interop / Interop@123`（user_id=2，企业 admin + default 团队管理员，DB 直插）。

| 端点 | 状态 | 报告 | 日期 |
|---|---|---|---|
| GET /console/custom_configs | ✅ 一致（修 1 处：公开路径） | [GET-custom_configs.md](GET-custom_configs.md) | 2026-07-18 |
| GET /console/update/versions | ✅ 一致（修全局 401 信封：裸三字段+固定文案） | [GET-update-versions.md](GET-update-versions.md) | 2026-07-18 |
| GET /console/enterprise/{eid}/licenses | ✅ 一致（无修改；有授权码分支本环境不可测） | [GET-enterprise-licenses.md](GET-enterprise-licenses.md) | 2026-07-18 |
| GET /console/enterprise/{eid}/service_alarm | ✅ 一致（无修改；异常组件分支不可测） | [GET-enterprise-service_alarm.md](GET-enterprise-service_alarm.md) | 2026-07-18 |
| GET /console/enterprise/{eid}/monitor | ✅ 一致（无修改；两分支均校） | [GET-enterprise-monitor.md](GET-enterprise-monitor.md) | 2026-07-18 |
| GET /console/enterprise/{eid}/overview/app | ✅ 一致（无修改；两分支均校） | [GET-enterprise-overview-app.md](GET-enterprise-overview-app.md) | 2026-07-18 |
| GET /console/monitor/query | ✅ 一致（修 2 处：公开路径+原文透传防浮点变形） | [GET-monitor-query.md](GET-monitor-query.md) | 2026-07-18 |
| GET regions/{r}/nodes | ✅ 一致（无修改） | [region-cluster-nodes.md](region-cluster-nodes.md) | 2026-07-18 |
| GET regions/{r}/rbd-components | ✅ 一致（集合级；7070 自身顺序不稳定） | [region-cluster-nodes.md](region-cluster-nodes.md) | 2026-07-18 |
| POST regions/{r}/nodes/{n}/action | ✅ 错误契约一致（成功路径不实测） | [region-cluster-nodes.md](region-cluster-nodes.md) | 2026-07-18 |
| GET regions/{r}/plugins | ✅ 一致（无修改） | [region-plugins.md](region-plugins.md) | 2026-07-18 |
| GET regions/{r}/officialplugins | ✅ 一致（无修改） | [region-plugins.md](region-plugins.md) | 2026-07-18 |
| GET regions/{r}/platform-plugins | ✅ 一致（**重写**：空 stub→1:1 移植市场拉取+合并逻辑，10 项全对齐） | [region-plugins.md](region-plugins.md) | 2026-07-18 |

| GET teams/{t}/overview（计数） | ✅ 一致（修存量：team_app_num/team_service_num 硬编码 0→真实计数） | [GET-team-overview-counts.md](GET-team-overview-counts.md) | 2026-07-18 |

**横切修复**（2026-07-18 回归发现）：
1. raw ISO 时间戳微秒截尾零差异（`.185550`→Jackson 输出 `.18555`）——新增 `PyIsoDateTime.iso()` 统一格式化，替换 enterprise/users、regions(list/detail)、组件 envs 4 处输出。
2. 未认证 401 信封（全局）：裸三字段+固定文案，见 [GET-update-versions.md](GET-update-versions.md)。
