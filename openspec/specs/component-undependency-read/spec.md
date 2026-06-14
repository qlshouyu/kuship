# component-undependency-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3u-not-dependency. Update Purpose after archive.
## Requirements
### Requirement: 可依赖但未依赖的组件列表读
系统 SHALL 提供 `GET /console/teams/{tenantName}/apps/{serviceAlias}/un_dependency`（对齐 `AppNotDependencyView.get`）：返回 team region 中**有对内端口**、未被本组件依赖、非自身的组件 dep_info(6 字段)，支持 search_key+condition 过滤、本应用在前、分页，`total`、`msg_show=查询成功`；condition 非法且存在候选时返回 400。

#### Scenario: 仅含开放对内端口的组件
- **WHEN** 团队有多个组件，仅部分开放对内端口
- **THEN** list 仅含开放对内端口者

#### Scenario: 单组件
- **WHEN** 团队仅该组件
- **THEN** list=[]、total=0

