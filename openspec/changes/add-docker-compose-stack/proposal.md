## Why

当前项目仅提供 standalone 单机镜像（k3s + Helm Chart）作为部署形态，缺少面向开发与本地联调的轻量编排方案。开发者需要手动拉取并启动 Redis、MySQL，再单独跑 rainbond-dev 容器，且各容器间的网络/凭据/依赖顺序均需自行维护，门槛高且易错。通过在 `docker/` 目录下提供一份开箱即用的 `docker-compose.yaml`，可以一键拉起 Rainbond 控制台所需的最小依赖栈，加速本地开发与功能验证。

## What Changes

- 在 `docker/` 目录新增 `docker-compose.yaml`，编排以下三个服务：
  - `redis`：使用 `redis:8.0-alpine` 镜像，作为缓存/会话依赖。
  - `mysql`：使用 `mysql:9` 镜像，root 密码固定为 `123456`，为 rainbond 提供数据存储。
  - `rainbond`：使用 `rainbond-dev:v6.7.1-release` 镜像，并通过环境变量连接到上述 `mysql:9` 服务。
- `rainbond-dev:v6.7.1-release` 镜像约定由 `standalone/` 目录中的 `Dockerfile` 构建产出（沿用现有 `standalone_build.sh` 的构建产物名），`docker-compose.yaml` 中通过 `image` 引用该镜像，并提供 `build` 段以便在镜像缺失时本地构建。
- 编排约束：`rainbond` 服务依赖 `mysql`、`redis` 启动（`depends_on`），共享同一 compose 网络以便通过服务名互联；MySQL 数据通过命名卷持久化，避免开发期数据丢失。

## Capabilities

### New Capabilities
- `docker-compose-stack`: 在 `docker/` 目录下提供一份 docker-compose 编排，声明 redis/mysql/rainbond 三个服务的镜像、端口、密码、依赖与镜像构建来源等本地开发栈契约。

### Modified Capabilities
<!-- 无既有 spec 受到影响 -->

## Impact

- 新增文件：`docker/docker-compose.yaml`。
- 不修改 `standalone/Dockerfile`、`standalone_build.sh` 等现有构建脚本；rainbond 镜像仍由 `standalone_build.sh` 产出，compose 仅消费该镜像。
- 影响开发者本地工作流：可使用 `docker compose up` 一键启动栈；需要本地已构建 `rainbond-dev:v6.7.1-release` 镜像或允许 compose 触发构建。
- 不影响生产部署、CI 流程与 standalone 单机镜像的发布形态。
