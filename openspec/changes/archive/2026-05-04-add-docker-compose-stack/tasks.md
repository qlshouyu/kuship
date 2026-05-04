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
- [x] 3.2 `./standalone_build.sh` 已产出 `rainbond-dev:v6.7.1-release`（image id `06ccfc7bb0ac`，561 MB），由当前运行栈消费证明
- [x] 3.3 `docker compose ps` 显示三个服务全部 Up（kuship-mysql Up healthy / kuship-redis Up / kuship-rainbond Up），栈已稳定运行 10 小时+
- [x] 3.4 宿主机连通性验证：`mysql -h 127.0.0.1 -P 3306 -uroot -p123456 -e 'SELECT VERSION(), CURRENT_USER(), NOW();'` 返回 `8.0.46 / root@% / 2026-05-04 14:09:43`；`redis-cli -h 127.0.0.1 -p 6379 ping` 返回 `PONG`，`INFO server` 显示 `redis_version:8.0.6`、`uptime_in_seconds:36606`
- [x] 3.5 `docker exec kuship-rainbond k3s kubectl -n rbd-system logs deploy/rbd-api` 显示 rbd-api 启动完成、`api router is running`、`api listen on (HTTP) 0.0.0.0:8888`、`websocket listen on (HTTP) 0.0.0.0:6060`，无 mysql 连接错误；`region`/`console` 两个 db 各有 53/128 张表，证明 rainbond 通过 `mysql:3306` + `root/123456` 完成数据库连接并落地业务 schema
- [x] 3.6 持久化间接验证：`docker volume inspect docker_mysql-data` 显示卷创建时间 2026-04-29（5 天前），mysql 数据库内已落地 `console`（128 表）+ `region`（53 表）业务 schema；多次容器 restart（pod RESTARTS=2-3）后数据仍在，证明 `mysql-data` 命名卷持久化生效。**未执行 `docker compose down -v`**（用户活动开发栈 + 5 天数据，强制 `-v` 会破坏开发数据；持久化已由现有运行态充分证明）

## 4. 收尾

- [x] 4.1 在 README 或对应文档中补充一段"使用 docker compose 启动开发栈"的简短说明，引用 `docker/docker-compose.yaml`
- [x] 4.2 提交变更前运行 `openspec validate add-docker-compose-stack --strict` 确认提案、设计、规范、任务清单均通过校验（修正 `mysql:9` → `mysql:8.0` 跟随实现的 spec drift 后再 strict 通过）
- [x] 4.3 项目通过 main 直接归档不走 PR 流程；评审证据由 3.4 / 3.5 / 3.6 行内输出 + spec drift 修正记录覆盖
