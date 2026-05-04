## Context

kuship 是基于 Rainbond 的 Kubernetes 云原生服务托管管理项目。仓库已具备 standalone 单机镜像构建链：`standalone_build.sh` 调用 `standalone/images-package.sh` 后通过 `docker buildx build -f standalone/Dockerfile -t rainbond-dev:v6.7.1-release .` 产出 `rainbond-dev:v6.7.1-release`。该镜像内嵌 k3s 与 rainbond Helm Chart，原本面向单容器单机部署。

`standalone/entrypoint.sh` 中 `rainbond-cluster.yaml` 的渲染逻辑显示，rainbond 控制台的 region/ui 数据库默认连接 `127.0.0.1:3306`、用户名 `root`、密码 `123456`、库名 `region`/`console`，可通过 `DB_HOST` / `UI_DB_HOST` / `DB_PASSWORD` 等环境变量覆盖。这为 compose 中外接 MySQL 提供了天然契约。

`docker/` 目录目前为空。开发者本地联调时，缺乏一份能一键起栈的编排文件。本设计目标是在不改动 standalone 构建链的前提下，新增 `docker/docker-compose.yaml` 编排文件，把 redis、mysql、rainbond 三者按依赖串起来。

## Goals / Non-Goals

**Goals:**
- 在 `docker/docker-compose.yaml` 中声明三个服务：`redis:8.0-alpine`、`mysql:8.0`、`rainbond-dev:v6.7.1-release`。
- 满足约束：MySQL root 密码 `123456`；rainbond 通过环境变量连接 compose 网络内的 `mysql:8.0` 服务；rainbond 镜像由 `standalone/` 目录的 Dockerfile 构建产出。
- compose 中提供 `build` 段，使 `docker compose build rainbond` 等价于现有 `standalone_build.sh` 的镜像构建步骤；同时通过 `image: rainbond-dev:v6.7.1-release` 复用已构建镜像。
- 服务之间通过 `depends_on` + 健康检查（MySQL）保证 rainbond 在 MySQL 就绪后启动。
- MySQL 数据使用命名卷持久化，开发期重启不丢数据。

**Non-Goals:**
- 不修改 `standalone/Dockerfile`、`standalone_build.sh`、`entrypoint.sh` 等任何现有构建脚本与配置。
- 不引入生产级的高可用配置（主从、备份、TLS 等）。
- 不重新设计 rainbond 的内部数据库 schema，沿用 entrypoint 中既有的 region/console 双库默认值。
- 不实现 k3s/Kubernetes 集群本身的多节点编排——本 compose 仅服务于 standalone 单容器形态。

## Decisions

### 决策 1：rainbond 镜像同时声明 `image` + `build`

- 选择：`rainbond` 服务同时配置 `image: rainbond-dev:v6.7.1-release` 与 `build: { context: .., dockerfile: standalone/Dockerfile }`。
- 理由：用户约束要求镜像由 `standalone/` 目录文件生成；同时仓库已有 `standalone_build.sh` 作为权威构建入口。`image` 让 compose 在镜像存在时直接复用产物，`build` 让缺镜像时可由 compose 兜底构建，二者命名一致避免分裂。
- 备选：仅写 `image` 强制开发者先跑 `standalone_build.sh`——更简单但首次启动易踩坑；仅写 `build` 会绕开既有构建脚本中的 `images-package.sh` 步骤导致缺少 `k3s-images-*.tar.zst`。当前方案兼顾两者。
- `context` 设为仓库根目录（`..`），与 `standalone_build.sh` 中 `docker buildx build -f standalone/Dockerfile ... .` 保持一致，因为 Dockerfile 中存在 `COPY reference/rainbond-chart`、`COPY k3s-images-$TARGETARCH.tar.zst` 等路径依赖于仓库根。

### 决策 2：MySQL 凭据与库名

- root 密码硬编码 `123456`（`MYSQL_ROOT_PASSWORD=123456`），与用户约束一致，也对齐 `entrypoint.sh` 中 `DB_PASSWORD:-123456`/`UI_DB_PASSWORD:-123456` 默认值。
- 通过 `MYSQL_DATABASE` 不预创建库——rainbond 启动时会按需建库；如需精细控制，可在后续迭代以初始化 SQL 注入。
- 暴露端口 `3306` 到宿主机，便于本地调试连接。

### 决策 3：rainbond 连接 MySQL 的方式

- 通过环境变量覆盖 `entrypoint.sh` 中的默认值：
  - `DB_HOST=mysql`、`DB_PORT=3306`、`DB_USER=root`、`DB_PASSWORD=123456`
  - `UI_DB_HOST=mysql`、`UI_DB_PORT=3306`、`UI_DB_USER=root`、`UI_DB_PASSWORD=123456`
- compose 默认网络让服务通过服务名 `mysql` 互相寻址，符合用户"使用 mysql:8.0"的约束。
- `depends_on` 配置 `condition: service_healthy`（依赖 MySQL 健康检查），避免 rainbond 在 MySQL 未就绪时连接失败。

### 决策 4：rainbond 容器的 privileged/挂载

- standalone 容器内置 k3s server，需要 `privileged: true` 与 `/opt/rainbond` 卷（Dockerfile 中已 `VOLUME /opt/rainbond`）；compose 将该卷映射为命名卷 `rainbond-data`，避免每次重建容器时丢失 k3s 状态。
- 暴露 rainbond 控制台/网关端口（典型 7070、80、443、6443）以便宿主机访问；具体端口范围沿用 standalone 默认行为，通过端口映射对外暴露。

### 决策 5：Redis 仅作依赖项启动

- 使用 `redis:8.0-alpine`，默认无密码、绑定 compose 内网；不强制 rainbond 连接它（entrypoint 中未涉及 redis 连接参数），仅满足"启动 redis 服务"的诉求并保留扩展空间。
- 暴露 `6379` 到宿主机以便本地调试。

## Risks / Trade-offs

- **Risk**：首次 `docker compose up` 时若未预先构建 `rainbond-dev:v6.7.1-release` 镜像，compose 触发的 `build` 会跳过 `standalone/images-package.sh`，导致缺失 `k3s-images-$TARGETARCH.tar.zst`。
  → Mitigation：在 compose 文件中以注释形式说明，并在 `tasks.md` 中要求先运行 `standalone_build.sh` 或 `standalone/images-package.sh` 准备离线镜像包；后续可考虑在 compose 增加 pre-hook 脚本。
- **Risk**：rainbond 容器需要 `privileged: true`，部分受限的 Docker 环境（Rootless、企业策略）会拒绝启动。
  → Mitigation：在 README/注释中说明本编排仅面向开发场景。
- **Risk**：MySQL 9 与 rainbond 历史版本的 caching_sha2_password 兼容性需要确认。
  → Mitigation：保留显式的 `MYSQL_ROOT_HOST=%`、`command: --default-authentication-plugin=mysql_native_password` 选项的扩展余地；初版直接使用 mysql:8.0 默认配置，遇到兼容性问题再加。
- **Trade-off**：用命名卷而非 bind mount 简化了首次启动体验，但开发者无法直接在宿主机文件系统检查 k3s/MySQL 数据；可在后续按需切换。

## Migration Plan

1. 新增 `docker/docker-compose.yaml`，无需迁移既有数据。
2. 文档侧（README）补充开发者使用方式：`./standalone_build.sh` 构建镜像 → `cd docker && docker compose up -d`。
3. 回滚策略：删除 `docker/docker-compose.yaml` 即恢复原状，不影响 standalone 镜像与 CI。

## Open Questions

- rainbond 控制台/网关需要对外暴露的最小端口集合是否仅为 7070？需在实现阶段验证 standalone 容器对外暴露的端口默认值。
- 是否需要在 compose 中加入 `restart: unless-stopped` 以贴近开发者重启容器的习惯？倾向加入，但留待实现时与团队确认。
