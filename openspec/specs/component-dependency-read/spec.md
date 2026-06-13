# component-dependency-read Specification

## Purpose
TBD - created by archiving change kuship-console-p3n-component-dependency. Update Purpose after archive.
## Requirements
### Requirement: 组件依赖读
系统 SHALL 提供 `GET .../apps/{serviceAlias}/dependency`(正向) 与 `GET .../apps/{serviceAlias}/dependency-list`(反向)（对齐 `AppDependencyView`/`AppDependencyViewList`）：返回 data={bean,list,total}，bean 含本组件 port_list+total（反向另含 service_id），list 为依赖组件信息(分页、本应用在前)，`msg_show=查询成功`。

#### Scenario: 无依赖
- **WHEN** 组件无依赖关系
- **THEN** list=[]、total=0、bean.port_list 为本组件端口，与 7070 一致

#### Scenario: 反向 bean 含 service_id
- **WHEN** 请求 dependency-list
- **THEN** bean 含 service_id 字段（正向不含）

