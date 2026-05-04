# rke2-cluster-bootstrap

## Purpose

定义 kuship 多节点 RKE2 集群的离线交付与引导能力。覆盖：版本钉死单一真相源（`rke2/rke2-version.env`）、镜像 + 二进制 + helm chart 的离线打包契约（`rke2/images-package.sh`、`rke2_build.sh`）、单文件 `rke2-bundle-<arch>.tar.zst` 离线包结构、server-install / agent-install 安装脚本契约（root + systemd 校验、幂等、`--force`、token 输出）、rainbond-cluster HelmChart manifest 引导（与 standalone/entrypoint.sh 同源）、文档与端口表，以及与 kuship-console region 注册流程的衔接。本能力不修改 kuship-console / kuship-ui 任何代码，集群安装完毕后用户通过 console 现有「kubeconfig 接入」流程把 RKE2 集群注册为 region。

## Requirements

### Requirement: 单一真相源版本钉死

`rke2/rke2-version.env` SHALL 是 RKE2 版本的唯一真相源，文件以 shell `KEY=VALUE` 格式提供 `RKE2_VERSION` 一行（形如 `RKE2_VERSION=v1.31.4+rke2r1`）。`rke2/images-package.sh`、`rke2/server-install.sh`、`rke2/agent-install.sh`、`rke2_build.sh` SHALL 全部通过 `. rke2-version.env` 读取该值，禁止在脚本内硬编码版本字符串。当 `RKE2_VERSION` 为空或文件不存在时，所有脚本 SHALL 以非零退出码立即终止并打印明确错误。

#### Scenario: 缺失版本文件时打包脚本失败

- **WHEN** 删除 `rke2/rke2-version.env` 后执行 `./rke2/images-package.sh`
- **THEN** 脚本 SHALL 以非零退出码终止并打印包含 `rke2-version.env` 字样的错误，不下载任何文件

#### Scenario: 空版本变量时安装脚本失败

- **WHEN** `rke2-version.env` 中 `RKE2_VERSION=`（值为空），目标节点上执行 `./server-install.sh`
- **THEN** 脚本 SHALL 在调用 `systemctl` 之前以非零退出码终止，并打印 `RKE2_VERSION` 相关错误

#### Scenario: 多脚本读取同一版本

- **WHEN** `rke2-version.env` 内 `RKE2_VERSION=v1.31.4+rke2r1`，依次执行 `./images-package.sh` 与 `./server-install.sh`
- **THEN** 两份脚本下载与启动的 RKE2 二进制 / 镜像 tag SHALL 完全一致（均为 `v1.31.4+rke2r1`）

### Requirement: 离线包构建编排入口

`rke2_build.sh` SHALL 位于仓库根，作为离线包构建编排入口，行为与 `standalone_build.sh` 同构：接受 `key=value` 参数，至少识别 `enable_proxy=<bool>` 与 `force_rebuild=<bool>`；`<bool>` 中 `1`/`true`/`yes`（不区分大小写）视为开启；遇到未识别参数 SHALL 以非零退出码终止并打印 usage；`-h`/`--help` SHALL 打印 usage 并以 0 退出。当 `enable_proxy=1` 时 SHALL 在执行后续命令前 export `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`ALL_PROXY` 为 `http://127.0.0.1:7897`，并把 `NO_PROXY` 至少包含 `localhost,127.0.0.1`。脚本 SHALL 在调用 `./rke2/images-package.sh` 前按当前架构推断目标包路径 `<repo-root>/rke2-bundle-<arch-tag>.tar.zst`；若该文件存在且 `force_rebuild` 为假值，SHALL 跳过 images-package.sh 并打印一行说明。脚本最后 SHALL 在仓库根产出形如 `rke2-bundle-<arch-tag>.tar.zst` 的离线包文件。

#### Scenario: 帮助参数

- **WHEN** 执行 `./rke2_build.sh --help`
- **THEN** 脚本 SHALL 输出 usage（至少包含 `enable_proxy` 与 `force_rebuild` 两个开关说明）并以退出码 0 终止，不产生任何离线包

#### Scenario: 未识别参数报错

- **WHEN** 执行 `./rke2_build.sh do_something=1`
- **THEN** 脚本 SHALL 打印包含 `unknown` 字样的错误信息与 usage，并以非零退出码终止

#### Scenario: 命中缓存跳过打包

- **WHEN** 仓库根已存在 `rke2-bundle-arm64.tar.zst`（在 `uname -m=arm64` 机器上），执行 `./rke2_build.sh`
- **THEN** 脚本 SHALL 不调用 `./rke2/images-package.sh`，直接打印缓存命中说明并以退出码 0 终止

#### Scenario: 强制重建

- **WHEN** 仓库根已存在 `rke2-bundle-arm64.tar.zst`，执行 `./rke2_build.sh force_rebuild=1`
- **THEN** 脚本 SHALL 调用 `./rke2/images-package.sh` 重新生成离线包

#### Scenario: 开启代理时透传

- **WHEN** 执行 `./rke2_build.sh enable_proxy=1`
- **THEN** 在 `./rke2/images-package.sh` 调用时，环境中 SHALL 同时存在 `HTTPS_PROXY=http://127.0.0.1:7897` 与 `https_proxy=http://127.0.0.1:7897`，且 `NO_PROXY` 包含 `localhost,127.0.0.1`

### Requirement: 离线包结构

`rke2/images-package.sh` 产出的 `rke2-bundle-<arch-tag>.tar.zst` SHALL 是单文件压缩包，解压后顶层目录名为 `rke2-<arch-tag>/`，且至少包含以下条目：`rke2.linux-<arch>.tar.gz`（RKE2 官方 air-gap 二进制 tarball）、`sha256sum-<arch>.txt`（RKE2 校验和）、`rke2-images.linux-<arch>.tar.zst`（容器镜像离线包）、`rainbond-cluster.tgz`（helm chart）、`server-install.sh`、`agent-install.sh`、`bootstrap-rainbond.sh`、`config-server.yaml`、`config-agent.yaml`、`registries.yaml`、`rke2-version.env`、`README.md`。所有 `*.sh` 文件 SHALL 具有可执行位（mode 0755）。压缩 SHALL 使用 zstd（`-T0 -19`）。架构 tag SHALL 与 standalone images-package.sh 规则一致：`linux/amd64` → `amd64`、`linux/arm64` → `arm64`。

#### Scenario: 包内容完整

- **WHEN** 在 arm64 主机执行 `./rke2_build.sh`，并对产物 `rke2-bundle-arm64.tar.zst` 执行 `tar -I zstd -tvf`
- **THEN** 输出 SHALL 列出 `rke2-arm64/server-install.sh`、`rke2-arm64/agent-install.sh`、`rke2-arm64/bootstrap-rainbond.sh`、`rke2-arm64/rainbond-cluster.tgz`、`rke2-arm64/rke2.linux-arm64.tar.gz`、`rke2-arm64/rke2-images.linux-arm64.tar.zst`、`rke2-arm64/rke2-version.env` 全部条目

#### Scenario: 脚本可执行

- **WHEN** 解压 `rke2-bundle-arm64.tar.zst` 后查看 `rke2-arm64/server-install.sh`
- **THEN** 该文件权限位 SHALL 包含可执行位（`u+x`），可直接通过 `./server-install.sh` 调用

#### Scenario: 镜像包格式

- **WHEN** 检查包内 `rke2-images.linux-<arch>.tar.zst`
- **THEN** 该文件 SHALL 是 zstd 压缩的 tar 包，解压后 `tar -tvf` 包含若干 docker save 风格的镜像 layer 条目

### Requirement: 镜像清单与版本对齐

`rke2/images-package.sh` SHALL 从 RKE2 官方 release 拉取与 `RKE2_VERSION` 完全一致的二进制（`rke2.linux-<arch>.tar.gz`）与镜像清单（`rke2-images.linux-<arch>.tar.zst` 或等价文件），不在脚本中硬编码任何版本字符串。脚本 SHALL 支持 `RKE2_RELEASE_URL` 环境变量覆盖默认下载 URL（默认 `https://github.com/rancher/rke2/releases/download/<version>/`），便于内网用户指向私有镜像源。下载 SHALL 至少重试 3 次（`curl --retry 3 --retry-delay 5`），任一文件下载失败 SHALL 立即终止脚本。

#### Scenario: 默认 URL 拉取

- **WHEN** 执行 `./rke2/images-package.sh`，`RKE2_VERSION=v1.31.4+rke2r1`
- **THEN** 脚本 SHALL 通过 HTTPS 访问 `https://github.com/rancher/rke2/releases/download/v1.31.4%2Brke2r1/`（`+` 编码为 `%2B`）下载 RKE2 二进制 tarball

#### Scenario: 内网 mirror 覆盖

- **WHEN** 执行 `RKE2_RELEASE_URL=https://mirror.local/rke2 ./rke2/images-package.sh`
- **THEN** 脚本 SHALL 改为访问 `https://mirror.local/rke2/v<version>/...` 下载

#### Scenario: 校验和验证

- **WHEN** RKE2 二进制 tarball 下载完成
- **THEN** 脚本 SHALL 用 `sha256sum -c` 对下载产物按官方 `sha256sum-<arch>.txt` 进行校验；校验失败 SHALL 立即非零退出

### Requirement: server-install 安装脚本契约

`rke2/server-install.sh` SHALL 在目标 server 节点执行下述步骤（顺序固定）：(1) 校验当前用户为 root 或具备 sudo 权限；(2) 校验 systemd 可用（`command -v systemctl`）；(3) 加载 `rke2-version.env`；(4) 解压 `rke2.linux-<arch>.tar.gz` 到 `/usr/local/`（与 RKE2 官方 install.sh 行为一致）；(5) 把 `rke2-images.linux-<arch>.tar.zst` 拷贝到 `/var/lib/rancher/rke2/agent/images/`；(6) 把 `config-server.yaml` 拷贝到 `/etc/rancher/rke2/config.yaml`，把 `registries.yaml` 拷贝到 `/etc/rancher/rke2/registries.yaml`；(7) 调用 `bootstrap-rainbond.sh` 渲染 `/var/lib/rancher/rke2/server/manifests/rainbond-cluster.yaml`；(8) 把 `rainbond-cluster.tgz` 拷贝到 `/var/lib/rancher/rke2/server/static/`；(9) `systemctl enable --now rke2-server.service`；(10) 等待 `/var/lib/rancher/rke2/server/node-token` 出现，最多轮询 300 秒；(11) 把 `node-token` 与 `https://<本机非回环 IP>:9345` 一并写入 `/var/lib/rancher/rke2/server/node-token-bundle.txt` 并打印到 stdout。当 `/etc/rancher/rke2/config.yaml` 已存在且 `rke2-server.service` 已 active 时，脚本 SHALL 仅打印警告并以退出码 0 退出，除非传入 `--force` 参数。

#### Scenario: 干净节点首装

- **WHEN** 在干净节点（无既有 `/etc/rancher/rke2/config.yaml`）以 root 执行 `./server-install.sh`
- **THEN** 步骤 (1)–(11) SHALL 全部完成；最终 `systemctl is-active rke2-server` SHALL 输出 `active`，stdout SHALL 同时打印 `RKE2_URL=https://...:9345` 与 `RKE2_TOKEN=...`

#### Scenario: 已安装节点重复执行

- **WHEN** 在已 `rke2-server.service` active 的节点重复执行 `./server-install.sh`（不带 `--force`）
- **THEN** 脚本 SHALL 打印警告（含 `already installed` 字样）并以退出码 0 退出，且不修改 `/etc/rancher/rke2/config.yaml`、不重启 service

#### Scenario: --force 重装

- **WHEN** 在已安装节点执行 `./server-install.sh --force`
- **THEN** 脚本 SHALL 重新拷贝 config / images / manifests，最后 `systemctl restart rke2-server.service`

#### Scenario: 非 root 失败

- **WHEN** 以普通用户（非 root、无 sudo）执行 `./server-install.sh`
- **THEN** 脚本 SHALL 在执行任何 systemctl 操作前以非零退出码终止，并打印权限相关错误

#### Scenario: token 等待超时

- **WHEN** rke2-server 启动后 300 秒内 `/var/lib/rancher/rke2/server/node-token` 仍未出现
- **THEN** 脚本 SHALL 以非零退出码终止，并打印包含 `node-token` 字样的错误，提示用户 `journalctl -u rke2-server` 排查

### Requirement: agent-install 加入脚本契约

`rke2/agent-install.sh` SHALL 通过两个必填环境变量 `RKE2_URL`（形如 `https://<server-ip>:9345`）与 `RKE2_TOKEN`（来自 server 节点的 `node-token`）控制 agent 加入。任一变量为空 SHALL 立即非零退出并打印 usage。脚本 SHALL 顺序执行：(1) 解压 `rke2.linux-<arch>.tar.gz` 到 `/usr/local/`；(2) 把镜像离线包拷贝到 `/var/lib/rancher/rke2/agent/images/`；(3) 把 `config-agent.yaml` 拷贝到 `/etc/rancher/rke2/config.yaml`，并在该文件追加 `server: ${RKE2_URL}` 与 `token: ${RKE2_TOKEN}` 两行（若已存在则用 sed 替换）；(4) 把 `registries.yaml` 拷贝到 `/etc/rancher/rke2/registries.yaml`；(5) `systemctl enable --now rke2-agent.service`；(6) 等待 `systemctl is-active rke2-agent` 返回 `active`，最多轮询 300 秒。

#### Scenario: 缺少 URL 立即失败

- **WHEN** 执行 `RKE2_TOKEN=xyz ./agent-install.sh`（未设置 `RKE2_URL`）
- **THEN** 脚本 SHALL 在任何文件操作前以非零退出码终止，打印 `RKE2_URL` 相关错误

#### Scenario: 缺少 TOKEN 立即失败

- **WHEN** 执行 `RKE2_URL=https://x:9345 ./agent-install.sh`（未设置 `RKE2_TOKEN`）
- **THEN** 脚本 SHALL 立即非零退出，打印 `RKE2_TOKEN` 相关错误

#### Scenario: 正常加入

- **WHEN** server 节点 install 成功后，在 agent 节点执行 `RKE2_URL=https://10.0.0.1:9345 RKE2_TOKEN=$(cat node-token) ./agent-install.sh`
- **THEN** 脚本 SHALL 完成步骤 (1)–(6)，最终 `systemctl is-active rke2-agent` SHALL 为 `active`

#### Scenario: 已加入节点重复执行幂等

- **WHEN** 在已 `rke2-agent.service` active 的节点重复执行 `agent-install.sh`
- **THEN** 脚本 SHALL 打印 `already joined` 警告并以退出码 0 退出

### Requirement: rainbond-cluster 引导

`rke2/bootstrap-rainbond.sh` SHALL 在 server 节点把 `rainbond-cluster.yaml` 渲染到 `/var/lib/rancher/rke2/server/manifests/`。渲染内容 SHALL 与 `standalone/entrypoint.sh` 中 `rainbond_cluster_yaml()` 函数同构，至少包含 `apiVersion: helm.cattle.io/v1`、`kind: HelmChart`、`metadata.name: rainbond-cluster`、`metadata.namespace: rbd-system`、`spec.chart: https://%{KUBERNETES_API}%/static/rainbond-cluster.tgz`、`spec.targetNamespace: rbd-system` 字段。`valuesContent` SHALL 至少接受以下环境变量覆盖（与 standalone 完全一致）：`EIP`、`UUID`、`VERSION`、`DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD`、`UI_DB_HOST` / `UI_DB_PORT` / `UI_DB_USER` / `UI_DB_PASSWORD`、`DB_REGION_ENABLE`、`DB_UI_ENABLE`。当 `EIP` 为空时 SHALL 回退到 `hostname -i` 第一个 IPv4 地址（与 standalone 行为一致）。

#### Scenario: 默认渲染

- **WHEN** 在 server 节点执行 `EIP=10.0.0.1 UUID=abc-123 ./bootstrap-rainbond.sh`
- **THEN** `/var/lib/rancher/rke2/server/manifests/rainbond-cluster.yaml` SHALL 包含 `gatewayIngressIPs: 10.0.0.1`、`eid: abc-123`、`installVersion: v6.7.1-release`（默认值）

#### Scenario: 数据库覆盖

- **WHEN** 在 server 节点执行 `DB_HOST=10.0.0.99 DB_USER=rb DB_PASSWORD=p ./bootstrap-rainbond.sh`
- **THEN** 渲染产物 SHALL 包含 `regionDatabase.host: 10.0.0.99`、`regionDatabase.username: rb`、`regionDatabase.password: p`，且未设置的字段保持默认值

#### Scenario: 与 standalone 字段一致

- **WHEN** 与 `standalone/entrypoint.sh` 用同一组环境变量分别渲染
- **THEN** 两份产物的字段名、缩进、键序 SHALL 一致（除路径前缀差异外），可通过 `diff` 工具比对仅有的差异项位于 `chart` URL 与 `targetNamespace` 上下文

#### Scenario: 无 EIP 时回退

- **WHEN** 在 server 节点不设置 `EIP` 执行 `UUID=abc ./bootstrap-rainbond.sh`
- **THEN** 渲染产物 `gatewayIngressIPs` SHALL 为 `hostname -i` 命中的第一个非回环 IPv4 地址

### Requirement: 文档与端口表

仓库根 `README.md` 和 `CLAUDE.md` SHALL 至少新增一段 RKE2 多节点部署章节，章节内 SHALL 至少覆盖：（1）从仓库构建离线包的命令（`./rke2_build.sh` + 可选 `enable_proxy=1`）；（2）把离线包拷贝到目标节点的步骤；（3）server 与 agent 安装的命令样例；（4）端口表，至少包含 `6443/tcp`（kube-api）、`9345/tcp`（RKE2 supervisor）、`10250/tcp`（kubelet）、`8472/udp`（VXLAN）四项；（5）安装完成后如何在 kuship-console 通过 kubeconfig 接入该集群的指引（不复述 console UI 步骤，但要显式指向已有「集群接入」流程并提示替换 kubeconfig 中 `127.0.0.1` 为 server 节点可达 IP）。

#### Scenario: README 含构建命令

- **WHEN** grep `README.md` 中的 RKE2 章节
- **THEN** 该章节 SHALL 包含 `./rke2_build.sh` 字符串与 `enable_proxy=1` 开关说明

#### Scenario: 端口表完整

- **WHEN** 阅读 RKE2 章节的端口表
- **THEN** 表中 SHALL 同时出现 `6443`、`9345`、`10250`、`8472`，并标注 TCP/UDP 与对应用途

#### Scenario: 集群接入指引

- **WHEN** 阅读「安装完成后接入 kuship-console」段落
- **THEN** 段落 SHALL 显式提示 `cat /etc/rancher/rke2/rke2.yaml` 取 kubeconfig，并提示把其中 `127.0.0.1`（或 `0.0.0.0`）替换为 server 节点对外 IP
