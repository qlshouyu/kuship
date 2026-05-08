## ADDED Requirements

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
