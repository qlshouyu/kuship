# docker-compose-stack

## Purpose

定义 kuship 本地开发栈的 docker-compose 编排契约：在仓库 `docker/` 目录提供一份开箱即用的 `docker-compose.yaml`，一键拉起 Rainbond 控制台所需的最小依赖栈（redis + mysql + rainbond）。覆盖：服务镜像与版本、root 凭据与密码注入、命名卷持久化、服务间依赖顺序与 healthcheck、rainbond 容器与 mysql/redis 的网络连通契约，以及 rainbond 镜像由 `standalone_build.sh` 离线产出的来源约定。本能力专注于本地开发联调，不影响生产部署、CI 流程与 standalone 单机镜像的发布形态。

## Requirements

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

### Requirement: Rainbond 服务可选挂载业务镜像离线包

`docker/docker-compose.yaml` 的 `rainbond` 服务 SHALL 在 `volumes:` 中声明一条仓库根目录下 `rainbond-images-${ARCH:-arm64}.tar.zst` 到容器内 `/tmp/rainbond-images.tar.zst` 的只读单文件挂载，其中 `${ARCH:-arm64}` 在用户未导出 `ARCH` 环境变量时退化为 `arm64`。该挂载 SHALL 是可选的：当宿主机文件不存在时，docker compose 启动 SHALL 不阻塞，rainbond 容器 SHALL 仍可启动并退化为在线拉取（由 entrypoint 检测后决定是否拷贝到 k3s agent/images 目录）。

#### Scenario: 离线包存在时被挂载

- **WHEN** 仓库根存在 `rainbond-images-arm64.tar.zst`，开发者执行 `docker compose -f docker/docker-compose.yaml up -d`
- **THEN** rainbond 容器内 `/tmp/rainbond-images.tar.zst` SHALL 是与宿主机大小一致的常规文件

#### Scenario: 通过 ARCH 切换架构

- **WHEN** 开发者在 amd64 宿主机执行 `ARCH=amd64 docker compose -f docker/docker-compose.yaml up -d`
- **THEN** Compose 解析后挂载源 SHALL 为 `rainbond-images-amd64.tar.zst`

#### Scenario: 离线包缺失不阻塞启动

- **WHEN** 仓库根不存在 `rainbond-images-${ARCH}.tar.zst`，开发者执行 `docker compose up -d`
- **THEN** docker compose SHALL 不报 `no such file or directory` 错误，rainbond 容器 SHALL 进入 running 状态

### Requirement: 默认网络保留 docker-compose 既有约束

引入业务镜像离线包挂载 SHALL 不改变现有的服务名互联、服务依赖顺序、healthcheck、命名卷持久化与端口暴露契约；仅在 `rainbond` 服务下增加一条 `volumes:` 行。

#### Scenario: 现有连通性不受影响

- **WHEN** 在引入挂载后执行 `docker compose up -d`
- **THEN** `rainbond` 容器 SHALL 仍可通过主机名 `mysql` 连通 mysql:3306、通过主机名 `redis` 连通 redis:6379，行为与本次变更前完全一致
