## ADDED Requirements

### Requirement: 共享 console 库的 JPA 底座

系统 SHALL 以 Spring Data JPA 连接既有 MySQL `console` 库，`hibernate.ddl-auto` MUST 为 `validate`，任何环境 MUST NOT 输出业务 DDL（schema 属权归 Django migrations）。P0 必需实体（如 `UserInfo`）MUST 反向映射既有列，命名/类型/可空性精确对齐。

#### Scenario: validate 模式启动

- **WHEN** 应用以已映射实体连接既有 `console` 库启动
- **THEN** Hibernate 仅校验 schema，不生成或修改任何表

#### Scenario: 实体与既有列不符则启动失败

- **WHEN** 某实体字段与 `console` 库既有列不匹配
- **THEN** 启动校验失败（fail-fast），暴露不匹配的字段

### Requirement: 无数据库外键的关系约定

由于 `console` 库 Django `db_constraint=False` 无 DB 外键，系统的 JPA 关系 MUST NOT 依赖 DB 外键或级联：关系用 `@ForeignKey(NO_CONSTRAINT)` 或直接存外键 ID + 应用层手动关联。entity MUST NOT 引入 Django 不认识的列（如 `@Version`）。

#### Scenario: 关系不产生外键约束

- **WHEN** 定义实体间关联
- **THEN** 映射不声明 DB 外键约束、不开启级联

### Requirement: 工程骨架与健康检查

系统 SHALL 以 `cn.kuship.console` 包结构（config/common/infrastructure/modules/healthz）组织，并提供 `GET /console/healthz`。URL MUST NOT 使用 `server.servlet.context-path`，controller 显式声明完整 `/console/...` 前缀。

#### Scenario: 健康检查可用

- **WHEN** 访问 `GET /console/healthz`
- **THEN** 返回成功信封，表示服务存活

#### Scenario: 不使用全局 context-path

- **WHEN** 检查应用配置
- **THEN** 未设置 `server.servlet.context-path`，路径前缀由各 controller 显式声明
