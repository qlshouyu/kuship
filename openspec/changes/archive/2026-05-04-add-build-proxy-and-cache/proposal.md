## Why

`standalone_build.sh` 现在每次执行都会无条件调用 `./standalone/images-package.sh` 重新拉取并打包 k3s 离线镜像，开发体验有两个痛点：

1. **网络受限场景缺少代理逃生通道**：`images-package.sh` 需要访问 `github.com`（取 `k3s-images.txt`）和 `docker.io`（拉镜像），在国内开发机直连常常失败/超时。开发者本机往往已有本地代理（如 Clash/Surge 默认监听 `127.0.0.1:7897`），但当前脚本没有通道把代理传给 `curl` 与 `docker pull`/`docker buildx build`。
2. **重复拉镜像浪费时间与带宽**：仓库根目录已存在 `k3s-images-arm64.tar.zst`（114MB+）时，`standalone_build.sh` 仍会再跑 `images-package.sh` 重新生成同样的内容；arm64 mac 开发者每次构建都白等几分钟。

希望让构建脚本支持两件事：开关式代理 + 复用已存在的离线包，使本地反复构建更轻量。

## What Changes

- `standalone_build.sh` 接受形如 `key=value` 的参数（沿用 `enable_proxy=1` 的既定写法），并按以下规则工作：
  - **脚本进程内代理**：`enable_proxy=1` 时为后续命令导出 `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`ALL_PROXY` 均为 `http://127.0.0.1:7897`，并把 `localhost,127.0.0.1` 加入 `NO_PROXY`；让 `images-package.sh` 中的 `curl`（取 `k3s-images.txt`）与本机 `docker pull` 走代理。
  - **buildx 容器内代理穿透**：`enable_proxy=1` 时同步给 `docker buildx build` 注入 `--build-arg HTTP_PROXY=http://host.docker.internal:7897` 等，让 build 容器内（base 阶段 `wget` 拉 k3s 二进制等场景）也能走宿主机代理；`Dockerfile` 在 `base` 与 `stage-1` 阶段声明 `ARG HTTP_PROXY` 等让 build-arg 进入 RUN 步骤的环境。
  - **apt 走国内 mirror 直连**：`Dockerfile` 在 `apt-get update && install ca-certificates` 这步显式 `unset HTTP(S)_PROXY` 并把 `/etc/apt/sources.list.d/ubuntu.sources` 替换为 `http://mirrors.aliyun.com/ubuntu-ports`，同时只保留 `Components: main`（ca-certificates 来自 main，关掉 universe/multiverse/restricted 减少弱网下因大索引拉取失败的概率）。这样 apt 直连国内 mirror、不绕代理；其他 RUN 步骤的 wget/curl 仍走代理。
  - **离线包缓存**：调用 `./standalone/images-package.sh` 之前按当前架构推断目标文件名（与 `images-package.sh` 中 `ARCH_TAG` 规则一致），仓库根已存在 `k3s-images-<arch-tag>.tar.zst` 时跳过 `images-package.sh` 并打印说明；否则照常执行。
  - 提供 `force_rebuild=1` 参数显式跳过缓存（覆盖已有离线包），方便升级 k3s 后强制重打。
- `standalone/images-package.sh` 自身不变（继续作为单点真相生成离线包）。
- `standalone/Dockerfile` 在 `base`（alpine/helm:3）与 `stage-1`（ubuntu:24.04）声明 `ARG HTTP_PROXY` / `ARG HTTPS_PROXY` / `ARG http_proxy` / `ARG https_proxy` / `ARG NO_PROXY` / `ARG no_proxy`；`stage-1` 新增 `ARG APT_MIRROR=http://mirrors.aliyun.com/ubuntu-ports` 用于 apt 替换。
- README 中 standalone 章节追加示例：
  - `./standalone_build.sh enable_proxy=1`
  - `./standalone_build.sh force_rebuild=1`

## Capabilities

### New Capabilities
- `standalone-build-orchestration`: 描述 `standalone_build.sh` 作为构建编排入口的契约——参数解析、代理开关、离线包缓存复用、与 `images-package.sh` / `docker buildx build` 的协作顺序。

### Modified Capabilities
<!-- 既有 standalone-image-bundle 不受影响，本变更只在外层编排脚本添加开关 -->

## Impact

- 修改文件：`standalone_build.sh`（新增参数解析、代理导出、缓存判断、buildx 代理 build-arg 注入）、`standalone/Dockerfile`（base/stage-1 加 `ARG HTTP_PROXY` 等、apt 替换 mirror 并 unset 代理、`Components: main` 精简）、`README.md`（用法示例）。
- 不修改 `standalone/images-package.sh`、`standalone/k3s-version.env`、`docker/docker-compose.yaml`。
- 行为兼容：未传任何参数时与现状一致（仍会调用 `images-package.sh`，buildx 不附加 proxy build-arg）；apt 始终用阿里云 mirror（不依赖 enable_proxy）；新增开关均为可选，默认关闭。
- 影响开发者：国内用户可加 `enable_proxy=1` 直接让 curl/wget/docker pull 走 `127.0.0.1:7897`，buildx 容器内自动用 `host.docker.internal:7897`；本机已存在离线包时构建明显加速；需要刷新离线包时显式 `force_rebuild=1`。
