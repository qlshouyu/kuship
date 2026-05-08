# kuship
kubernetes管理系统

## 项目结构
- `kuship-console/` —— kuship 后端控制台（Java Spring Boot 重写 rainbond-console）。
- `kuship-ui/` —— kuship 控制台前端（基于 rainbond-ui 拷贝起步，独立演进）。开发态默认对接 `add-docker-compose-stack` 启动的 rainbond-console 测试实例（`http://localhost:7070`）；切换到 kuship-console 通过 `CONSOLE_PROXY_TARGET` 环境变量覆盖。
- `reference/` —— Rainbond 上游代码（git submodule，只读，作为对照参考）。
- `standalone/`、`docker/` —— standalone 镜像与 docker-compose 开发栈相关资产。
- `rke2/` —— 多节点 RKE2 离线部署脚本与配置（详见 `rke2/README.md`）。
- `openspec/` —— OpenSpec 规范与变更提案。详见各子目录下的 `proposal.md` / `tasks.md`。

## standalone 镜像制作
```bash
./standalone_build.sh                          # 默认：仓库根已有 k3s-images-<arch>.tar.zst 时复用，否则重打
./standalone_build.sh enable_proxy=1           # 为本进程的 curl/docker 命令导出 http://127.0.0.1:7897 代理
./standalone_build.sh force_rebuild=1          # 强制重新执行 standalone/images-package.sh，覆盖已有离线包
./standalone_build.sh build_business_images=1  # 同时生成 rainbond-images-<arch>.tar.zst（rainbond 业务镜像离线包，加速 docker-compose 首次启动）
./standalone_build.sh -h                       # 查看用法
```

> 升级 k3s 时只需修改 `standalone/k3s-version.env` 中的 `K3S_VERSION`，再跑 `./standalone_build.sh force_rebuild=1`，离线镜像包与 Dockerfile 内嵌的 k3s 二进制会自动保持同一版本。
>
> 升级 rainbond 业务版本时改 `standalone/rainbond-images.env` 中的 `RAINBOND_VERSION`，再跑 `./standalone_build.sh build_business_images=1 force_rebuild=1` 重打业务镜像离线包；`reference/rainbond-chart` 改动后需同步 `RAINBOND_IMAGES` 列表，否则 `business-images-package.sh` 的 chart 校对会立即报错。
>
> `enable_proxy=1` 仅在本脚本进程内导出 `HTTP(S)_PROXY` 等变量，影响 `curl` 取 `k3s-images.txt` 与本机 `docker pull`。docker daemon 拉取基础镜像（`alpine/helm:3` / `ubuntu:24.04`）的代理需要在 Docker Desktop 的 Settings → Resources → Proxies 中单独配置，本脚本不会修改它。

## 使用 docker compose 启动开发栈
`docker/docker-compose.yaml` 一键拉起本地开发依赖栈：`redis:8.0-alpine`、`mysql:9`（root 密码 `123456`）以及 `rainbond-dev:v6.7.1-release`。其中 rainbond 镜像由 `standalone/Dockerfile` 构建产出，首次启动前请先运行 `./standalone_build.sh` 以构建镜像并准备好 `k3s-images-<arch>.tar.zst` 离线包。

```bash
./standalone_build.sh                          # 必需：构建 rainbond-dev 镜像 + k3s 系统镜像离线包
./standalone_build.sh build_business_images=1  # 可选：再生成 rainbond-images-<arch>.tar.zst，加速首次启动
docker compose -f docker/docker-compose.yaml up -d
```

> 业务镜像离线包缺失时 docker compose 仍可启动（退化为在线拉取），不会阻塞；详见 [`docker/README.md`](docker/README.md)。

## RKE2 多节点部署
生产环境 1×server + N×agent 拓扑，使用与 standalone 同构的离线交付路径，详见 [`rke2/README.md`](rke2/README.md)。

```bash
./rke2_build.sh                 # 默认：复用已存在的 rke2-bundle-<arch>.tar.zst
./rke2_build.sh enable_proxy=1  # 国内开发者：本机已开 127.0.0.1:7897 代理
./rke2_build.sh force_rebuild=1 # 升级 RKE2 版本后强制重打
```

产物 `rke2-bundle-<arch>.tar.zst`（约 1.5 GB）。把它分发到 server / agent 节点解包后：

```bash
sudo ./server-install.sh                                     # server 节点
sudo RKE2_URL=... RKE2_TOKEN=... ./agent-install.sh          # agent 节点
```

需放行的端口（详细见 `rke2/README.md`）：

| 端口 | 协议 | 用途 |
|------|------|------|
| 6443  | TCP | Kubernetes API |
| 9345  | TCP | RKE2 supervisor（agent 注册） |
| 10250 | TCP | kubelet |
| 8472  | UDP | Flannel/Canal VXLAN（pod 跨节点网络） |

> 安装完成后，在 server 节点 `cat /etc/rancher/rke2/rke2.yaml` 取 kubeconfig，把其中 `127.0.0.1` 替换为 server 节点对外 IP，即可在 kuship-console 通过「集群接入 → 通过 kubeconfig 接入」完成 region 注册。本期不修改 console / UI 代码，仅交付离线脚本。

## 启动
```bash
# 在/etc/hosts设置 rbd-api-api映射到本地地址，方便kuship-console调用docker启动的rbd-api
! sudo sh -c 'grep -q "rbd-api-api" /etc/hosts || echo "127.0.0.1 rbd-api-api" >> /etc/hosts'
```

## 其他