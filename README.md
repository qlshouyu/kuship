# kuship
kubernetes管理系统
## standalone 镜像制作
```bash
./standalone_build.sh                  # 默认：仓库根已有 k3s-images-<arch>.tar.zst 时复用，否则重打
./standalone_build.sh enable_proxy=1   # 为本进程的 curl/docker 命令导出 http://127.0.0.1:7897 代理
./standalone_build.sh force_rebuild=1  # 强制重新执行 standalone/images-package.sh，覆盖已有离线包
./standalone_build.sh -h               # 查看用法
```

> 升级 k3s 时只需修改 `standalone/k3s-version.env` 中的 `K3S_VERSION`，再跑 `./standalone_build.sh force_rebuild=1`，离线镜像包与 Dockerfile 内嵌的 k3s 二进制会自动保持同一版本。
>
> `enable_proxy=1` 仅在本脚本进程内导出 `HTTP(S)_PROXY` 等变量，影响 `curl` 取 `k3s-images.txt` 与本机 `docker pull`。docker daemon 拉取基础镜像（`alpine/helm:3` / `ubuntu:24.04`）的代理需要在 Docker Desktop 的 Settings → Resources → Proxies 中单独配置，本脚本不会修改它。

## 使用 docker compose 启动开发栈
`docker/docker-compose.yaml` 一键拉起本地开发依赖栈：`redis:8.0-alpine`、`mysql:9`（root 密码 `123456`）以及 `rainbond-dev:v6.7.1-release`。其中 rainbond 镜像由 `standalone/Dockerfile` 构建产出，首次启动前请先运行 `./standalone_build.sh` 以构建镜像并准备好 `k3s-images-<arch>.tar.zst` 离线包。

```bash
./standalone_build.sh
docker compose -f docker/docker-compose.yaml up -d
```

## 其他