## ADDED Requirements

### Requirement: 支持 key=value 参数与帮助

`standalone_build.sh` SHALL 接受零个或多个形如 `key=value` 的位置参数，识别 `enable_proxy=<bool>` 与 `force_rebuild=<bool>`；`<bool>` 中 `1`/`true`/`yes`（不区分大小写）视为开启，其它视为关闭；遇到未识别参数 SHALL 以非零退出码终止并打印 usage；`-h`/`--help` SHALL 打印 usage 并以 0 退出。未传任何参数时 SHALL 使用默认值 `enable_proxy=0` 与 `force_rebuild=0`。

#### Scenario: 不带参数运行向后兼容

- **WHEN** 在仓库根直接执行 `./standalone_build.sh`
- **THEN** 脚本 SHALL 不导出任何 `*_PROXY` 环境变量，且按缓存规则决定是否调用 `./standalone/images-package.sh`，最后执行 `docker buildx build`

#### Scenario: 帮助参数

- **WHEN** 执行 `./standalone_build.sh --help` 或 `./standalone_build.sh -h`
- **THEN** 脚本 SHALL 输出 usage（至少包含 `enable_proxy` 与 `force_rebuild` 两个开关说明）并以退出码 `0` 终止，不执行任何构建动作

#### Scenario: 未识别参数报错

- **WHEN** 执行 `./standalone_build.sh do_something=1`
- **THEN** 脚本 SHALL 打印包含 `unknown` 字样的错误信息与 usage，并以非零退出码终止，不执行 `images-package.sh` 与 `docker buildx build`

### Requirement: enable_proxy 控制本进程代理

当解析到 `enable_proxy=1`（或同义真值）时，`standalone_build.sh` SHALL 在执行后续命令前，把 `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`ALL_PROXY` 全部 `export` 为 `http://127.0.0.1:7897`，并把 `NO_PROXY` 至少包含 `localhost,127.0.0.1`；当 `enable_proxy` 缺省或为假值时 SHALL 不修改任何代理相关环境变量。代理设置 SHALL 仅在脚本执行的子进程范围内生效，不写入用户的 `~/.docker/config.json` 或 shell rc 文件。

#### Scenario: 开启代理

- **WHEN** 执行 `./standalone_build.sh enable_proxy=1`
- **THEN** 在 `./standalone/images-package.sh`（若需调用）与 `docker buildx build` 调用时，环境中 SHALL 同时存在 `HTTPS_PROXY=http://127.0.0.1:7897` 与 `https_proxy=http://127.0.0.1:7897`，且 `NO_PROXY` 包含 `localhost,127.0.0.1`

#### Scenario: 默认不开启代理

- **WHEN** 执行 `./standalone_build.sh`（不传 `enable_proxy`）
- **THEN** 脚本 SHALL 不 export 任何 `*_PROXY` / `*_proxy` 变量；如果调用方 shell 原本已有 `HTTPS_PROXY`，脚本 SHALL 不覆盖它

#### Scenario: 显式关闭代理

- **WHEN** 执行 `./standalone_build.sh enable_proxy=0`
- **THEN** 行为 SHALL 与未传 `enable_proxy` 时一致

### Requirement: 复用已有 k3s 离线包

`standalone_build.sh` SHALL 在调用 `./standalone/images-package.sh` 前，按当前架构（自动识别或读取 `ARCH` 环境变量，规则与 `standalone/images-package.sh` 完全一致）推断目标离线包路径 `<repo-root>/k3s-images-<arch-tag>.tar.zst`；若该文件存在且 `force_rebuild` 为假值，SHALL 跳过 `images-package.sh` 并打印一行说明（包含被跳过的文件路径与提示 `force_rebuild=1` 可强制重建）；若文件不存在或 `force_rebuild=1`，SHALL 正常调用 `images-package.sh`。

#### Scenario: arm64 机器命中缓存

- **WHEN** 在 `uname -m=arm64` 的机器上，仓库根已存在 `k3s-images-arm64.tar.zst`，执行 `./standalone_build.sh`
- **THEN** 脚本 SHALL 不调用 `./standalone/images-package.sh`，直接进入 `docker buildx build`

#### Scenario: amd64 机器命中缓存

- **WHEN** 在 `uname -m=x86_64` 的机器上，仓库根已存在 `k3s-images-amd64.tar.zst`，执行 `./standalone_build.sh`
- **THEN** 脚本 SHALL 不调用 `./standalone/images-package.sh`，直接进入 `docker buildx build`

#### Scenario: 强制重建

- **WHEN** 仓库根已存在 `k3s-images-arm64.tar.zst`，执行 `./standalone_build.sh force_rebuild=1`
- **THEN** 脚本 SHALL 调用 `./standalone/images-package.sh` 重新生成离线包，再进入 `docker buildx build`

#### Scenario: 缓存缺失时仍触发构建

- **WHEN** 仓库根不存在对应架构的 `k3s-images-<arch-tag>.tar.zst`，执行 `./standalone_build.sh`
- **THEN** 脚本 SHALL 调用 `./standalone/images-package.sh` 生成离线包，再进入 `docker buildx build`

#### Scenario: ARCH 覆盖时按覆盖值判断缓存

- **WHEN** 在 amd64 机器上执行 `ARCH=linux/arm64 ./standalone_build.sh`，且仓库根存在 `k3s-images-arm64.tar.zst`
- **THEN** 脚本 SHALL 跳过 `images-package.sh`，按 arm64 离线包进入 `docker buildx build`

### Requirement: 代理穿透到 buildx 容器

当 `enable_proxy=1` 时，`standalone_build.sh` 在调用 `docker buildx build` 时 SHALL 同时附加 `--build-arg HTTP_PROXY=http://host.docker.internal:7897`、`--build-arg HTTPS_PROXY=http://host.docker.internal:7897`、`--build-arg http_proxy=http://host.docker.internal:7897`、`--build-arg https_proxy=http://host.docker.internal:7897`、`--build-arg NO_PROXY=localhost,127.0.0.1`、`--build-arg no_proxy=localhost,127.0.0.1`。`standalone/Dockerfile` 的 `base` 与 `stage-1` 阶段 SHALL 都通过 `ARG HTTP_PROXY` / `ARG HTTPS_PROXY` / `ARG http_proxy` / `ARG https_proxy` / `ARG NO_PROXY` / `ARG no_proxy` 接受这些 build-arg，使 RUN 步骤的 wget/curl/apk 等命令通过宿主机代理访问外网。`enable_proxy` 缺省或假值时 SHALL 不附加任何 proxy build-arg。

#### Scenario: 开启代理时 buildx 收到 build-arg

- **WHEN** 执行 `./standalone_build.sh enable_proxy=1`
- **THEN** 实际 `docker buildx build` 命令行 SHALL 同时包含 `--build-arg HTTP_PROXY=http://host.docker.internal:7897`、`--build-arg HTTPS_PROXY=http://host.docker.internal:7897` 与对应小写 4 个 + `NO_PROXY` / `no_proxy=localhost,127.0.0.1`

#### Scenario: 不开代理时 buildx 命令行不变

- **WHEN** 执行 `./standalone_build.sh`（不带 `enable_proxy`）
- **THEN** `docker buildx build` 命令行 SHALL 不包含任何 `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY` 相关 `--build-arg`

#### Scenario: Dockerfile 接收代理 build-arg

- **WHEN** 任意构建中 `--build-arg HTTP_PROXY=<url>` 被传入
- **THEN** `standalone/Dockerfile` 中 `base` 与 `stage-1` 两个 FROM 之后 SHALL 都存在 `ARG HTTP_PROXY` 声明（其它代理 ARG 同），使 RUN 步骤可见对应环境变量

### Requirement: apt 走国内 mirror 且不走代理

`standalone/Dockerfile` 的 `stage-1` 在执行 `apt-get update` 之前 SHALL 把 `/etc/apt/sources.list.d/ubuntu.sources` 中的 `http://ports.ubuntu.com/ubuntu-ports` 替换为 `${APT_MIRROR}`（默认 `http://mirrors.aliyun.com/ubuntu-ports`，可通过 `--build-arg APT_MIRROR=...` 覆盖），并把 `Components:` 行收敛为 `Components: main`；同一 RUN 中 SHALL `unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy` 让 apt 直连国内 mirror 而非穿透代理；`apt-get -y install ca-certificates` SHALL 携带 `--no-install-recommends`。

#### Scenario: 默认使用阿里云 mirror

- **WHEN** 执行 `docker buildx build` 而未传 `--build-arg APT_MIRROR`
- **THEN** stage-1 实际拉取索引时 SHALL 访问 `http://mirrors.aliyun.com/ubuntu-ports`，且 sources 中的 `Components` SHALL 仅为 `main`

#### Scenario: 自定义 mirror 覆盖

- **WHEN** 执行 `docker buildx build --build-arg APT_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports ...`
- **THEN** stage-1 实际拉取索引 SHALL 访问 tuna mirror

#### Scenario: apt 不走代理

- **WHEN** 在 `enable_proxy=1` 场景下进入 stage-1 的 apt RUN 步骤
- **THEN** 该 RUN 内 `env | grep -i proxy` 在 `unset` 之后 SHALL 不包含 `HTTP_PROXY` / `HTTPS_PROXY` / `http_proxy` / `https_proxy`，apt 直连国内 mirror 完成 update 与 install

### Requirement: 与既有构建流程的兼容

`standalone_build.sh` SHALL 仍完成下列既有动作：从 `standalone/k3s-version.env` 加载 `K3S_VERSION` 并校验非空；调用 `docker buildx build -f standalone/Dockerfile --build-arg K3S_VERSION=<value> -t rainbond-dev:v6.7.1-release .`；`set -euo pipefail` 风格的失败立即退出。

#### Scenario: K3S_VERSION 缺失时立即失败

- **WHEN** `standalone/k3s-version.env` 中 `K3S_VERSION` 为空
- **THEN** 脚本 SHALL 在调用 `images-package.sh` 与 `docker buildx build` 前以非零退出码终止，并打印关于 `K3S_VERSION` 的明确错误

#### Scenario: 构建命令携带版本号

- **WHEN** 任意参数组合下进入 `docker buildx build`
- **THEN** 该命令 SHALL 携带 `--build-arg K3S_VERSION=<env 中读到的值>` 与 `-t rainbond-dev:v6.7.1-release`，并以仓库根作为构建上下文
