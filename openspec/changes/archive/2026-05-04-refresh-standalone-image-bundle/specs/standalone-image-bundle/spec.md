## ADDED Requirements

### Requirement: 单一真相的 k3s 版本

仓库 SHALL 在 `standalone/k3s-version.env` 中以 `K3S_VERSION=<tag>` 形式声明唯一的 k3s 版本号；`standalone/Dockerfile` 与 `standalone/images-package.sh` SHALL 从该文件读取版本号，不得各自硬编码。

#### Scenario: 升级 k3s 版本只改一处

- **WHEN** 开发者把 `standalone/k3s-version.env` 中的 `K3S_VERSION` 从 `v1.33.10+k3s1` 改为更高版本并保存
- **THEN** 仅靠这一处修改，再次执行 `./standalone_build.sh` 即可让生成的离线包与 Dockerfile 内嵌的 k3s 二进制保持同一版本，无需修改其他文件中的版本字面量

#### Scenario: 缺失版本文件时构建失败明确

- **WHEN** 在缺失 `standalone/k3s-version.env` 的情况下执行 `./standalone/images-package.sh`
- **THEN** 脚本 SHALL 以非零退出码终止，并输出可识别的错误（包含文件路径与"K3S_VERSION not set"语义）

### Requirement: 镜像清单来自 k3s 官方 release

`standalone/images-package.sh` SHALL 通过 HTTPS 从 `https://github.com/k3s-io/k3s/releases/download/<url-encoded K3S_VERSION>/k3s-images.txt` 获取该 k3s 版本的官方镜像清单作为权威来源；镜像名 SHALL 严格来自该清单，不得在脚本中硬编码 tag。脚本 SHALL 支持通过环境变量 `K3S_IMAGES_TXT_URL` 覆盖默认 URL，便于内网镜像源场景。

#### Scenario: 默认 URL 拼接正确

- **WHEN** `K3S_VERSION=v1.33.10+k3s1` 且未设置 `K3S_IMAGES_TXT_URL`
- **THEN** 脚本实际访问的 URL SHALL 为 `https://github.com/k3s-io/k3s/releases/download/v1.33.10%2Bk3s1/k3s-images.txt`（即 `+` 已 URL 编码为 `%2B`）

#### Scenario: 网络失败时报错而非继续

- **WHEN** 拉取 `k3s-images.txt` 在重试后仍失败（HTTP 非 2xx 或网络超时）
- **THEN** 脚本 SHALL 以非零退出码终止，并输出包含目标 URL 与失败原因的错误信息，不得继续执行 `docker pull`

#### Scenario: 自定义清单 URL

- **WHEN** 调用方设置 `K3S_IMAGES_TXT_URL=https://internal.example/k3s-images-1.33.10.txt`
- **THEN** 脚本 SHALL 从该 URL 获取镜像清单，忽略 GitHub 默认 URL

### Requirement: 按当前架构生成离线归档

`standalone/images-package.sh` SHALL 自动识别构建机架构（`x86_64`/`amd64` 映射为 `linux/amd64`，`aarch64`/`arm64` 映射为 `linux/arm64`，`armv7l` 映射为 `linux/arm/v7`），按该架构 `docker pull --platform=linux/<arch>` 清单中所有镜像，并 SHALL 通过 `docker save | zstd` 生成位于仓库根目录的 `k3s-images-<arch-tag>.tar.zst`，其中 `<arch-tag>` 与 `standalone/Dockerfile` 中 `COPY k3s-images-$TARGETARCH.tar.zst` 使用的 `TARGETARCH` 命名保持一致。开发者 MAY 通过环境变量 `ARCH` 覆盖自动识别结果。

#### Scenario: arm64 机器输出 arm64 归档

- **WHEN** 在 `uname -m` 为 `arm64` 的 macOS 机器上执行 `./standalone/images-package.sh`
- **THEN** 脚本 SHALL 在仓库根目录写出 `k3s-images-arm64.tar.zst`，且其中所有镜像 layer 的架构 SHALL 为 `linux/arm64`

#### Scenario: 通过 ARCH 强制指定架构

- **WHEN** 在 amd64 机器上执行 `ARCH=linux/arm64 ./standalone/images-package.sh`
- **THEN** 脚本 SHALL 输出 `k3s-images-arm64.tar.zst`，所有镜像按 `linux/arm64` 平台拉取

#### Scenario: 拉取失败立即终止

- **WHEN** 清单中某条镜像在 `docker pull` 阶段失败
- **THEN** 脚本 SHALL 以非零退出码立即终止，并输出失败的镜像名，不得生成残缺的 `k3s-images-<arch>.tar.zst`

### Requirement: 离线包内容与 k3s 运行期一致

容器启动后 k3s 通过 `/opt/rainbond/k3s/agent/images/k3s-images.tar.zst` 注入到 containerd 的全部镜像 tag，SHALL 完整覆盖该 k3s 版本运行期需要的系统镜像（至少包含 coredns、metrics-server、klipper-helm、pause），且 tag 与该 k3s 版本启动时尝试拉取的 tag 完全一致。

#### Scenario: ImagePullBackOff 不再发生

- **WHEN** 在已断网（无法访问 docker.io / github.com）的环境中通过 `docker compose -f docker/docker-compose.yaml up -d` 启动 standalone 栈，并等待 5 分钟
- **THEN** `kubectl get pods -A` 中 `kube-system/coredns-*`、`kube-system/metrics-server-*`、`rbd-system/helm-install-rainbond-cluster-*` SHALL 均不再出现 `ImagePullBackOff` 或 `ErrImagePull` 状态

#### Scenario: containerd 镜像版本与 k3s 一致

- **WHEN** 在容器内执行 `k3s ctr -n k8s.io images list`
- **THEN** 输出 SHALL 包含与 `K3S_VERSION` 对应的 `rancher/mirrored-coredns-coredns`、`rancher/mirrored-metrics-server`、`rancher/klipper-helm` tag（例如 `v1.33.10+k3s1` 对应 `1.14.2`、`v0.8.1`、`v0.9.14-build20260309`），不再是历史的 `1.10.1`/`v0.7.0`/`v0.8.4-build20240523`
