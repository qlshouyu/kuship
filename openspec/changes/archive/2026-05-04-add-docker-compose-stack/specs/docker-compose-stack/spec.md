## ADDED Requirements

### Requirement: Compose 文件位置与服务编排

系统 SHALL 在仓库 `docker/docker-compose.yaml` 路径下提供 Docker Compose 编排文件，文件中 SHALL 至少声明 `redis`、`mysql`、`rainbond` 三个服务，且 SHALL 使用 Compose 默认网络让三个服务可通过服务名互相寻址。

#### Scenario: 开发者拉起完整栈

- **WHEN** 开发者在仓库根目录执行 `docker compose -f docker/docker-compose.yaml up -d`
- **THEN** Compose 同时创建并启动 `redis`、`mysql`、`rainbond` 三个容器，且它们位于同一默认网络中

#### Scenario: 服务通过名称互联

- **WHEN** `rainbond` 服务在容器内对主机名 `mysql` 发起 TCP 连接
- **THEN** 该连接 SHALL 路由到 `mysql` 服务监听的 3306 端口

### Requirement: Redis 服务镜像与配置

`redis` 服务 SHALL 使用 `redis:8.0-alpine` 镜像启动，并 SHALL 加入 Compose 默认网络对其他服务可见。

#### Scenario: Redis 使用指定镜像

- **WHEN** `docker compose -f docker/docker-compose.yaml config` 解析 `redis` 服务
- **THEN** 输出的 `image` 字段 SHALL 等于 `redis:8.0-alpine`

#### Scenario: Redis 启动后可用

- **WHEN** `redis` 容器进入 running 状态
- **THEN** 通过 Compose 网络发起的 `redis-cli -h redis ping` SHALL 返回 `PONG`

### Requirement: MySQL 服务镜像与凭据

`mysql` 服务 SHALL 使用 `mysql:8.0` 镜像，并 SHALL 通过环境变量 `MYSQL_ROOT_PASSWORD=123456` 设置 root 用户密码。MySQL 数据 SHALL 持久化到命名卷以便容器重建时不丢失。

#### Scenario: MySQL 使用指定镜像

- **WHEN** `docker compose -f docker/docker-compose.yaml config` 解析 `mysql` 服务
- **THEN** 输出的 `image` 字段 SHALL 等于 `mysql:8.0`

#### Scenario: root 密码符合约束

- **WHEN** `mysql` 容器启动完成
- **THEN** 使用账号 `root`、密码 `123456` 通过 3306 端口进行登录 SHALL 成功

#### Scenario: 数据持久化

- **WHEN** 开发者依次执行 `docker compose down` 与 `docker compose up -d`（不带 `-v`）
- **THEN** 之前在 MySQL 中创建的库表数据 SHALL 仍然存在

### Requirement: Rainbond 服务镜像来源

`rainbond` 服务 SHALL 使用 `rainbond-dev:v6.7.1-release` 镜像运行；Compose 文件 SHALL 同时声明 `build` 段，使该镜像可由 `standalone/` 目录中的 `Dockerfile` 在仓库根目录上下文中构建产出，且镜像标签 SHALL 与 `image` 字段保持一致。

#### Scenario: 引用既有镜像

- **WHEN** 宿主机 Docker 中已存在 `rainbond-dev:v6.7.1-release` 镜像，且开发者执行 `docker compose up -d`
- **THEN** Compose SHALL 直接使用该镜像启动 `rainbond` 容器，不重复构建

#### Scenario: 通过 standalone Dockerfile 构建

- **WHEN** 开发者执行 `docker compose -f docker/docker-compose.yaml build rainbond`
- **THEN** Compose SHALL 使用仓库根目录作为构建上下文、`standalone/Dockerfile` 作为 Dockerfile 完成镜像构建，并 SHALL 产出标签为 `rainbond-dev:v6.7.1-release` 的镜像

### Requirement: Rainbond 连接 MySQL 与启动顺序

`rainbond` 服务 SHALL 通过环境变量将 region 数据库与 ui 数据库均指向 Compose 中的 `mysql` 服务（host=`mysql`、port=`3306`、user=`root`、password=`123456`）；`rainbond` 服务 SHALL 通过 `depends_on` 等待 `mysql` 服务的健康检查通过后再启动。

#### Scenario: rainbond 在 MySQL 就绪后启动

- **WHEN** 开发者执行 `docker compose up -d`
- **THEN** `rainbond` 容器 SHALL 仅在 `mysql` 服务的健康检查变为 healthy 之后才进入启动流程

#### Scenario: rainbond 使用 mysql:8.0 作为数据库

- **WHEN** `rainbond` 容器内的初始化逻辑读取 region/ui 数据库连接配置
- **THEN** 配置中的 host SHALL 等于 `mysql`，port SHALL 等于 `3306`，user SHALL 等于 `root`，password SHALL 等于 `123456`，且实际建立的连接 SHALL 落到 Compose 中以 `mysql:8.0` 镜像运行的容器
