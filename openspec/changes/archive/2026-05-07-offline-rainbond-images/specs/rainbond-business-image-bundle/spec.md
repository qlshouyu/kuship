## ADDED Requirements

### Requirement: 单一真相的 Rainbond 业务镜像清单

仓库 SHALL 在 `standalone/rainbond-images.env` 中以 `RAINBOND_VERSION=<tag>` 声明 rainbond 业务镜像的统一版本号，并以两个变量分别枚举：`RAINBOND_IMAGES=` 列出 chart 渲染产物覆盖的镜像（含固定 tag 的 minio / apisix / registry / rbd-monitor / rbd-db / local-path-provisioner / apisix-ingress-controller 与按 `RAINBOND_VERSION` 拼接 tag 的 rbd-api / rbd-chaos / rbd-mq / rbd-worker / rainbond / rainbond-operator），`RAINBOND_EXTRA_IMAGES=`（可选）列出 chart 不渲染但运行期由 operator/CRD 动态使用的镜像（如 `alpine:3`）。`standalone/business-images-package.sh` SHALL 仅从该文件读取镜像列表，不得在脚本中硬编码 tag；最终打包内容 SHALL 是 `RAINBOND_IMAGES` 与 `RAINBOND_EXTRA_IMAGES` 的并集。

#### Scenario: 升级 rainbond 业务版本只改一处

- **WHEN** 开发者把 `standalone/rainbond-images.env` 中的 `RAINBOND_VERSION` 从 `v6.7.1-release` 改为更高版本并保存
- **THEN** 仅靠这一处修改，再次执行 `./standalone/business-images-package.sh` 即可让产出的离线包内 `rbd-api` / `rbd-app-ui` / `rbd-chaos` / `rbd-mq` / `rbd-worker` / `rainbond` / `rainbond-operator` 均使用新版本 tag

#### Scenario: 缺失 env 文件时构建失败明确

- **WHEN** 在缺失 `standalone/rainbond-images.env` 的情况下执行 `./standalone/business-images-package.sh`
- **THEN** 脚本 SHALL 以非零退出码终止，并输出可识别的错误（包含文件路径与"RAINBOND_VERSION not set" 或 "RAINBOND_IMAGES not set" 语义）

#### Scenario: 镜像列表为空时报错

- **WHEN** `RAINBOND_IMAGES` 在 env 文件中存在但展开后为空
- **THEN** 脚本 SHALL 以非零退出码终止，错误信息中包含 `RAINBOND_IMAGES is empty`

### Requirement: 业务镜像清单与 rainbond-chart 渲染一致

`standalone/business-images-package.sh` SHALL 在拉取前对 `reference/rainbond-chart` 执行 `helm template` 渲染（注入 `Cluster.installVersion=$RAINBOND_VERSION`），从渲染结果中提取所有 `image:` 字段并与 `RAINBOND_IMAGES` 列表（不含 `RAINBOND_EXTRA_IMAGES`）做集合差集；存在任一方独有镜像时 SHALL 立即报错并打印两边差集。`RAINBOND_EXTRA_IMAGES` 不参与 chart 校对，但同样进入打包列表。脚本 SHALL 在宿主机已安装 `helm` 时直接调用，否则降级为 `docker run --rm alpine/helm:3` 容器化执行（与 `standalone/Dockerfile` 第一阶段使用的 helm 镜像一致），不强制开发机本地安装 helm。

#### Scenario: chart 与 env 一致时通过校验

- **WHEN** `rainbond-images.env` 列表与 chart 渲染产物完全一致
- **THEN** 脚本 SHALL 打印 `==> chart image list verified` 并继续后续步骤

#### Scenario: chart 中出现 env 未声明的镜像时报错

- **WHEN** `reference/rainbond-chart` 模板新增了一个镜像，但 `rainbond-images.env` 未同步更新
- **THEN** 脚本 SHALL 在拉取前以非零退出码终止，错误信息 SHALL 同时打印 chart 中独有的镜像名

#### Scenario: env 中存在 chart 已删除的镜像时报错

- **WHEN** `rainbond-images.env` 仍列出已被 chart 删除的镜像
- **THEN** 脚本 SHALL 在拉取前以非零退出码终止，错误信息 SHALL 同时打印 env 中独有的镜像名

### Requirement: 按当前架构生成业务镜像离线包

`standalone/business-images-package.sh` SHALL 自动识别构建机架构（`x86_64`/`amd64` 映射为 `linux/amd64`，`aarch64`/`arm64` 映射为 `linux/arm64`，`armv7l` 映射为 `linux/arm/v7`，规则与 `standalone/images-package.sh` 完全一致），按该架构 `docker pull --platform=linux/<arch>` 列表中所有镜像，并 SHALL 通过 `docker save | zstd` 生成位于仓库根目录的 `rainbond-images-<arch-tag>.tar.zst`。开发者 MAY 通过环境变量 `ARCH` 覆盖自动识别结果。

#### Scenario: arm64 机器输出 arm64 离线包

- **WHEN** 在 `uname -m` 为 `arm64` 的 macOS 机器上执行 `./standalone/business-images-package.sh`
- **THEN** 脚本 SHALL 在仓库根目录写出 `rainbond-images-arm64.tar.zst`，且其中所有镜像 layer 的架构 SHALL 为 `linux/arm64`

#### Scenario: 通过 ARCH 强制指定架构

- **WHEN** 在 amd64 机器上执行 `ARCH=linux/arm64 ./standalone/business-images-package.sh`
- **THEN** 脚本 SHALL 输出 `rainbond-images-arm64.tar.zst`，所有镜像按 `linux/arm64` 平台拉取

#### Scenario: 任一镜像拉取失败立即终止

- **WHEN** 列表中某条镜像在 `docker pull` 阶段失败
- **THEN** 脚本 SHALL 以非零退出码立即终止，并输出失败的镜像名，不得生成残缺的 `rainbond-images-<arch>.tar.zst`

### Requirement: 必备工具与依赖检查

`standalone/business-images-package.sh` SHALL 在执行任何拉取前检查 `curl`、`docker`、`zstd` 命令存在；缺失时 SHALL 以非零退出码终止并打印安装提示（macOS Homebrew / Linux 各包管理器）。`docker info` 不可用时同样报错终止。`helm` 命令缺失时 SHALL 不报错，而是降级为 `docker run --rm alpine/helm:3` 容器化执行 chart 校对。

#### Scenario: helm 缺失时降级为容器执行

- **WHEN** 构建机未安装 helm，开发者执行 `./standalone/business-images-package.sh`
- **THEN** 脚本 SHALL 不因缺失 helm 退出，输出一行 `helm 未安装，将通过 alpine/helm:3 容器执行 chart 校对` 提示，并继续走 chart 校对流程

#### Scenario: docker daemon 不可用时报错

- **WHEN** Docker Desktop 未启动，开发者执行 `./standalone/business-images-package.sh`
- **THEN** 脚本 SHALL 以非零退出码终止，错误信息 SHALL 包含 `docker daemon 不可用`

### Requirement: 容器启动时自动加载业务镜像离线包

`standalone/entrypoint.sh` SHALL 在 k3s 启动前检测挂载点 `/tmp/rainbond-images.tar.zst`：当该路径是常规文件且大小 ≥ 1 MB 时 SHALL 将其拷贝（或硬链接）至 `/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst`，由 k3s agent 启动时自动 import；当该路径不存在、是空目录（docker 在源文件缺失时自动创建的占位）或文件小于 1 MB 时 SHALL 打印一行 warning（包含 `rainbond-images.tar.zst missing or empty, falling back to online pull`）并继续启动；不得因离线包缺失而以非零退出码终止。该步骤 SHALL 幂等：重复启动不会重复消耗、不会因目标已存在而报错。

#### Scenario: 离线包存在时被 k3s 自动加载

- **WHEN** 容器启动时 `/tmp/rainbond-images.tar.zst` 是大小为 2.4 GB 的常规文件
- **THEN** entrypoint SHALL 把它就位到 `/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst`，且容器内执行 `k3s ctr -n k8s.io images list` SHALL 在 k3s 启动后 60 秒内列出 `registry.cn-hangzhou.aliyuncs.com/goodrain/rbd-api:<RAINBOND_VERSION>` 等业务镜像

#### Scenario: 离线包缺失时退化为在线拉取

- **WHEN** 容器启动时 `/tmp/rainbond-images.tar.zst` 不存在、是空目录或文件大小 < 1 MB
- **THEN** entrypoint SHALL 打印一行 warning，不向 `/opt/rainbond/k3s/agent/images/` 写入业务离线包，k3s SHALL 正常启动，rainbond 业务 pod SHALL 通过在线拉取就绪（与未引入本能力前的现状一致）

#### Scenario: 离线包加载后启动耗时缩短

- **WHEN** 离线包存在且包含 chart 渲染所需的全部业务镜像
- **THEN** 从 `docker compose up -d` 起算，`rbd-system` 命名空间内全部 pod 进入 `Running` 的耗时 SHALL 不超过 4 分钟（基线：在线拉取场景下 ≥ 12 分钟）

### Requirement: 离线包不入库

`rainbond-images-*.tar.zst` SHALL 在仓库 `.gitignore` 中显式忽略；`docker/README.md` 与 `README.md` 的相关章节 SHALL 明确说明该文件为构建产物、不入库。

#### Scenario: gitignore 覆盖业务镜像离线包

- **WHEN** 开发者执行 `./standalone/business-images-package.sh` 后在仓库根运行 `git status`
- **THEN** `rainbond-images-<arch>.tar.zst` SHALL 不出现在 untracked 文件列表中
