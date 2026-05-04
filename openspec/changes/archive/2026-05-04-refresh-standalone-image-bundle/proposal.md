## Why

`standalone/Dockerfile` 已升级到 `k3s v1.33.10+k3s1`，但仓库中的 `k3s-images-arm64.tar.zst` 仍是旧 k3s 版本打的包，预置镜像版本与运行时期望版本对不上：

| 组件 | 离线包内 | k3s v1.33.10 期望 |
|---|---|---|
| coredns | `1.10.1` | **`1.14.2`** |
| metrics-server | `v0.7.0` | **`v0.8.1`** |
| klipper-helm | `v0.8.4-build20240523` | **`v0.9.14-build20260309`** |

后果：本地按 `docker/docker-compose.yaml` 启动 standalone 容器后，coredns、metrics-server、`helm-install-rainbond-cluster` 全部 `ImagePullBackOff`，HelmChart 无法落地，rainbond 集群起不来；离线场景下完全不可用。

更深层问题：`standalone/images-package.sh` 把镜像清单与版本号硬编码，每次升级 k3s 后都要手工同步——这次的版本错位就是同步遗漏导致的。需要把"镜像清单跟随 k3s 版本"这条契约固化下来。

## What Changes

- 重构 `standalone/images-package.sh`：
  - 从 `standalone/Dockerfile` 解析当前 k3s 版本，或通过参数 / 环境变量显式传入。
  - 从 `https://github.com/k3s-io/k3s/releases/download/<version>/k3s-images.txt` 拉取该 k3s 版本的官方系统镜像清单作为权威来源。
  - 按当前架构 (`amd64` / `arm64`) `docker pull` 全部清单镜像，再 `docker save` + `zstd` 输出 `k3s-images-<arch>.tar.zst`。
- 让 k3s 版本只有"一处真相"：在 `standalone/Dockerfile` 与 `images-package.sh` 间共享同一版本号（通过解析 Dockerfile 或抽到 `standalone/k3s-version.env` 顶层文件）。
- 重新生成并提交 `k3s-images-arm64.tar.zst`（以及条件允许时的 `k3s-images-amd64.tar.zst`），覆盖仓库中的旧文件。
- `standalone_build.sh` 行为不变，仍依次调用 `images-package.sh` 与 `docker buildx build`，但调用后产出的镜像将与 k3s 版本对齐。
- **BREAKING**：旧 `k3s-images-*.tar.zst` 内容会被替换，下游若手工依赖旧版镜像 tag 会受影响——但这是修复回归，不是退化。

## Capabilities

### New Capabilities
- `standalone-image-bundle`: 描述 standalone 镜像离线包的生成契约——从权威 k3s 版本清单出发、按构建机架构生成 `k3s-images-<arch>.tar.zst`，保证容器启动时 containerd 加载的镜像版本与 k3s 二进制版本完全一致。

### Modified Capabilities
<!-- 无既有 spec；docker-compose-stack 不直接受影响，但其 ImagePullBackOff 问题由本变更修复 -->

## Impact

- 修改文件：`standalone/images-package.sh`（重构）；新增 `standalone/k3s-version.env`（可选，单一真相）；`standalone/Dockerfile` 中 k3s 版本由该文件提供（可选）。
- 替换文件：`k3s-images-arm64.tar.zst`（必）；`k3s-images-amd64.tar.zst`（条件允许时）。
- 无需改 `docker/docker-compose.yaml`、`standalone/entrypoint.sh`、`standalone_build.sh` 主流程。
- 执行约束：生成离线包必须在能访问 `github.com`（取 `k3s-images.txt`）与 `docker.io`（拉镜像）的网络环境中进行；CI/本地受限网络无法生成。
- 影响开发者：升级 k3s 版本时只需改一处版本号，再跑 `./standalone/images-package.sh` 即可获得匹配的离线包；不再出现版本错位的回归。
