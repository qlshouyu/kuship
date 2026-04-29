## Context

kuship 通过 `standalone/Dockerfile` 把 k3s 二进制 + Rainbond Helm Chart 打成单容器镜像 `rainbond-dev:v6.7.1-release`，运行时 k3s 启动时会自动从 `/var/lib/rancher/k3s/agent/images/`（本项目 data-dir 重映射到 `/opt/rainbond/k3s/agent/images/`）加载 `*.tar`/`*.tar.zst` 离线镜像归档。

构建链：
- `standalone_build.sh:2`：`./standalone/images-package.sh` 在仓库根生成 `k3s-images-<arch>.tar.zst`。
- `standalone_build.sh:3`：`docker buildx build -f standalone/Dockerfile -t rainbond-dev:v6.7.1-release .`，Dockerfile 中 `COPY k3s-images-$TARGETARCH.tar.zst /tmp/k3s-images.tar.zst`。
- `standalone/entrypoint.sh:19-21`：容器首次启动时把 `/tmp/k3s-images.tar.zst` 复制到 k3s data-dir 下的 agent images 目录。

现状：`standalone/images-package.sh` 把 5 个镜像版本硬编码：
```
rancher/klipper-helm:v0.8.4-build20240523
rancher/mirrored-coredns-coredns:1.10.1
rancher/mirrored-metrics-server:v0.7.0
rancher/mirrored-pause:3.6
busybox:latest
```
而 `standalone/Dockerfile:7-9` 已升级到 `k3s v1.33.10+k3s1`，该版本运行时的实际期望（已通过 `containerd images list` 与 ImagePullBackOff 事件验证）是 coredns `1.14.2`、metrics-server `v0.8.1`、klipper-helm `v0.9.14-build20260309`。两边偏差直接导致 ImagePullBackOff。

每个 k3s release 在 GitHub release assets 中都附带一份权威 `k3s-images.txt`（如 `https://github.com/k3s-io/k3s/releases/download/v1.33.10%2Bk3s1/k3s-images.txt`），列出该版本启动所需的全部容器镜像（含 mirrored-coredns、mirrored-metrics-server、klipper-helm、klipper-lb、local-path-provisioner、mirrored-library-busybox、mirrored-library-traefik、mirrored-pause 等）。把这份清单作为单一真相，可以一劳永逸地与 k3s 版本对齐。

## Goals / Non-Goals

**Goals:**
- 把"k3s 版本"做成一处真相：Dockerfile 与 images-package.sh 共用同一版本号。
- `images-package.sh` 自动从 `k3s-images.txt` 取该版本的镜像清单，按当前架构 `docker pull` + `docker save` + `zstd` 生成 `k3s-images-<arch>.tar.zst`。
- 重新生成并提交 `k3s-images-arm64.tar.zst`（开发者主流为 macOS arm64），从而修复 docker compose 启动后的 ImagePullBackOff。
- 生成失败时退出码非零、报错明确（网络不可达、版本号无效、Docker 不可用等），不要静默吞错。

**Non-Goals:**
- 不修改 `standalone/Dockerfile` 中的 k3s 版本本身（保持 `v1.33.10+k3s1`），本变更只让离线包追上现有版本。
- 不打包 Rainbond Helm Chart 的业务镜像（rbd-app-ui、rainbond-operator、rbd-gateway 等）——这是另一项独立的离线化工作，与 k3s 系统镜像清单的来源不同；纳入会让范围爆炸，留作后续变更。
- 不改 `standalone_build.sh`、`standalone/entrypoint.sh`、`standalone/Dockerfile` 中加载离线包的代码路径（容器侧自动加载机制无变化）。
- 不改 docker-compose.yaml；本变更只供 rainbond 镜像本身。

## Decisions

### 决策 1：k3s 版本"单一真相"放在 `standalone/k3s-version.env`

- 选择：新增 `standalone/k3s-version.env`，内容仅一行：`K3S_VERSION=v1.33.10+k3s1`。`images-package.sh` 与 `standalone/Dockerfile` 都从此读取。
- 理由：`Dockerfile` 中 k3s 版本目前以 URL 字面量硬编码（行 7、9），不易解析；引入一个浅显的 env 文件作为单一来源，让二者天然同步。开发者升级 k3s 时只改一处。
- 备选 A：让 `images-package.sh` 用 grep/sed 从 Dockerfile 解析版本——方案脆，URL 编码（`%2B` vs `+`）易出错。
- 备选 B：让 Dockerfile 用 `ARG K3S_VERSION` 由构建脚本注入——更优雅，但需要同时改 `standalone_build.sh` 来传 `--build-arg`，且改动不在本变更范围。本期采用 env 文件 + 脚本读取，Dockerfile 改成 `ARG K3S_VERSION` 也只是新增一行 `RUN` 时使用 `${K3S_VERSION}`，可在同变更里一并完成（见任务 2.4）。
- `K3S_VERSION` 取值采用 GitHub release tag 的"+ 号"形态（人类友好）；脚本内部需要的 URL 编码版本由脚本生成。

### 决策 2：用 k3s 官方 `k3s-images.txt` 作为镜像清单源

- URL 模板：`https://github.com/k3s-io/k3s/releases/download/<urlencoded_tag>/k3s-images.txt`，其中 `<urlencoded_tag>` 把 `+` 替换为 `%2B`。
- 理由：这是 k3s 团队提供的权威清单，每次发版都同步更新；任何 k3s 版本对应的运行期镜像不会遗漏。
- 备选：从 k3s 仓库 `scripts/airgap/image-list.txt` 抓——同源但需要 git clone 整个 k3s 源码，显著更重。
- 鲁棒性：`curl` 加 `--fail --location --retry 3 --retry-delay 5` 防瞬态错误；HTTP 非 200 时退出码非零并明确报错"无法获取 k3s-images.txt，请检查网络与版本号"。

### 决策 3：拉取与打包流程

- 解析得到的镜像清单按行迭代，每行一条 `<repo>:<tag>`；忽略空行与 `#` 开头的注释行。
- 通过 `docker pull --platform=linux/<arch>` 拉每个镜像；任一镜像失败立即退出（`set -e`），输出失败的镜像名。
- 全部镜像拉到本地后，`docker save <image1> <image2> ... | zstd -T0 -19 -o k3s-images-<arch>.tar.zst`。`-T0` 用满核；`-19` 与现状一致追求体积小。
- 输出文件位置：仓库根（与 `Dockerfile` 中 `COPY k3s-images-$TARGETARCH.tar.zst` 路径一致）。
- 架构识别保持现状（`uname -m` → `amd64` / `arm64` / `arm-v7`），允许通过 `ARCH` 环境变量覆盖。
- 添加 `--no-pull` / `--keep-existing` 选项？暂不引入，保持脚本最简，有需要再加。

### 决策 4：脚本失败模式与可观测性

- 顶部加 `set -eu`（保留 POSIX `sh`，不强转 bash）。
- 在每个关键阶段打印一行进度：`==> resolving k3s version`、`==> fetching k3s-images.txt`、`==> pulling N images for linux/<arch>`、`==> saving to k3s-images-<arch>.tar.zst`。
- 若 `docker info` 不可用、`zstd` 不可用、`curl` 不可用——分别给出明确的安装提示（与现状中的 `install_zstd` 思路一致）。

### 决策 5：旧离线包的处理

- 在脚本生成新文件前先 `rm -f k3s-images-<arch>.tar.zst`，避免 zstd 误以为追加。
- 提交新生成的 `k3s-images-arm64.tar.zst`。`amd64` 的离线包同样需要更新；本变更要求至少 arm64 重打（与开发者主流环境一致）；amd64 在能联网的 amd64 构建机上重跑脚本生成（任务 3.x 中标注为可选并允许遗留到下次随手补上）。

## Risks / Trade-offs

- **Risk**：执行环境无法访问 `github.com` 或 `docker.io`，脚本失败。
  → Mitigation：脚本明确报错；文档中说明需要可联网的 builder 机器；提供 `K3S_IMAGES_TXT_URL` 环境变量覆盖（兜底，可指向内网镜像仓库的同名清单文件）。
- **Risk**：未来 k3s 改了 release asset 命名（如把 `k3s-images.txt` 改名）。
  → Mitigation：本变更通过 env + URL 模板，URL 路径变化只需改脚本，影响面可控；并在变更说明中记录依赖。
- **Risk**：清单中包含运行时其实不会用到的镜像（如 traefik、servicelb——standalone `config.yaml` 已 `disable: traefik/servicelb/local-storage`），导致离线包体积偏大。
  → Mitigation：保守地全部纳入避免漏；体积变化由 zstd 压缩抵消；后续可加可选过滤。
- **Trade-off**：引入 `standalone/k3s-version.env` 让 `Dockerfile` 中的 URL 不再纯字面量；构建时读取 env 需要在 `standalone_build.sh` 或 `Dockerfile` 中协调。本变更选择"`Dockerfile` 用 `ARG K3S_VERSION`，默认值由 `standalone_build.sh` 从 env 读取并 `--build-arg`"，扩散面最小。

## Migration Plan

1. 引入 `standalone/k3s-version.env`，写入 `K3S_VERSION=v1.33.10+k3s1`。
2. 重构 `standalone/images-package.sh`，按本设计实现。
3. 调整 `standalone/Dockerfile`：把行 7/9 中硬编码的 `v1.33.10+k3s1`（含 URL 编码 `%2B`）替换为 `ARG K3S_VERSION` + 在 `RUN` 中拼出 URL 编码形态。
4. 更新 `standalone_build.sh`：先 `source standalone/k3s-version.env`，再 `./standalone/images-package.sh`，最后 `docker buildx build --build-arg K3S_VERSION="${K3S_VERSION}" ...`。
5. 在能联网到 github.com / docker.io 的构建机上跑 `./standalone/images-package.sh`，覆盖 `k3s-images-arm64.tar.zst`（必）与 `k3s-images-amd64.tar.zst`（条件允许）。
6. 重新构建 `rainbond-dev:v6.7.1-release` 镜像，使用 docker compose 重新拉起栈，验证 coredns / metrics-server / helm-install-rainbond-cluster 不再 `ImagePullBackOff`。
7. 回滚策略：保留旧版 `k3s-images-arm64.tar.zst` 在 git 历史，必要时 `git checkout` 回退即可。

## Open Questions

- 是否需要把 amd64 的离线包也在本变更内提交？取决于是否有 amd64 联网构建机可用；若无，留作后续随手补提交，不阻塞本变更交付。
- Rainbond Helm Chart 的业务镜像（rbd-app-ui、rainbond-operator 等）是否纳入离线打包？暂不纳入本变更；建议作为下一变更 `bundle-rainbond-app-images`。
