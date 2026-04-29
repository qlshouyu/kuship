## 1. 准备工作

- [x] 1.1 确认仓库根目录已存在 `standalone/Dockerfile`、`standalone_build.sh`、`standalone/images-package.sh`，以及构建所需的 `reference/rainbond-chart` 子模块与 `k3s-images-$TARGETARCH.tar.zst` 文件
- [x] 1.2 在 `docker/` 目录下确认无既有 `docker-compose.yaml`，避免覆盖

## 2. 编写 docker-compose.yaml

- [x] 2.1 在 `docker/docker-compose.yaml` 中声明 `services.redis`，使用 `image: redis:8.0-alpine`，加入默认网络，按需暴露宿主机 6379 端口
- [x] 2.2 在 `docker/docker-compose.yaml` 中声明 `services.mysql`，使用 `image: mysql:9`，配置 `MYSQL_ROOT_PASSWORD=123456`，挂载命名卷 `mysql-data:/var/lib/mysql`，并配置 `healthcheck`（如 `mysqladmin ping -h localhost -uroot -p123456`）
- [x] 2.3 在 `docker/docker-compose.yaml` 中声明 `services.rainbond`，同时设置 `image: rainbond-dev:v6.7.1-release` 与 `build: { context: .., dockerfile: standalone/Dockerfile }`，将仓库根作为构建上下文
- [x] 2.4 在 `services.rainbond` 中通过 `environment` 注入 `DB_HOST=mysql`、`DB_PORT=3306`、`DB_USER=root`、`DB_PASSWORD=123456`、`UI_DB_HOST=mysql`、`UI_DB_PORT=3306`、`UI_DB_USER=root`、`UI_DB_PASSWORD=123456`，使其连接 compose 中的 mysql:9
- [x] 2.5 在 `services.rainbond` 中配置 `depends_on`：`mysql` 使用 `condition: service_healthy`，`redis` 使用 `condition: service_started`
- [x] 2.6 在 `services.rainbond` 中添加 `privileged: true`、`restart: unless-stopped`，并挂载命名卷 `rainbond-data:/opt/rainbond`，按需将控制台/网关相关端口（例如 7070、80、443、6443）映射到宿主机
- [x] 2.7 在文件顶部以注释说明：rainbond 镜像由 `standalone_build.sh` 通过 `standalone/Dockerfile` 构建产出；首次启动前需先运行 `./standalone_build.sh` 以确保 `k3s-images-*.tar.zst` 离线包已就位
- [x] 2.8 在 `volumes:` 顶层声明 `mysql-data`、`rainbond-data` 命名卷

## 3. 本地验证

- [x] 3.1 运行 `docker compose -f docker/docker-compose.yaml config` 校验 YAML 语法与字段引用正确，无告警/错误
- [ ] 3.2 运行 `./standalone_build.sh` 产出 `rainbond-dev:v6.7.1-release` 镜像（或在 compose 缺失镜像时执行 `docker compose -f docker/docker-compose.yaml build rainbond` 回退构建）
- [ ] 3.3 运行 `docker compose -f docker/docker-compose.yaml up -d`，验证三个服务全部进入 running 状态，且 `mysql` 健康检查通过
- [ ] 3.4 在宿主机使用 `mysql -h 127.0.0.1 -P 3306 -uroot -p123456 -e 'SELECT 1;'` 验证密码生效；用 `redis-cli -h 127.0.0.1 -p 6379 ping` 验证 redis 可达
- [ ] 3.5 进入 `rainbond` 容器或观察日志，确认其使用环境变量中的 `mysql:3306` 与 root/123456 完成 region/ui 数据库连接，无连接失败错误
- [ ] 3.6 执行 `docker compose down` 后再 `up -d`，确认 mysql 中的数据通过 `mysql-data` 命名卷得到保留

## 4. 收尾

- [x] 4.1 在 README 或对应文档中补充一段"使用 docker compose 启动开发栈"的简短说明，引用 `docker/docker-compose.yaml`
- [x] 4.2 提交变更前运行 `openspec validate add-docker-compose-stack` 确认提案、设计、规范、任务清单均通过校验
- [ ] 4.3 在 PR 描述中链接 `openspec/changes/add-docker-compose-stack/` 下的 proposal/design/specs/tasks 文件，便于评审
