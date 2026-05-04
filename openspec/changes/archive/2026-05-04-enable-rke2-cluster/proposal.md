## Why

当前 kuship 仅交付 standalone（单节点 k3s）部署形态，生产场景需要多节点高可用集群。RKE2 是 Rancher 出品的强化版 Kubernetes 发行版，相比 k3s 更贴近生产（CIS 默认 hardened、systemd 单元、稳定的多 server / agent 模型），且与 rainbond-chart 完全兼容。本次新增一条独立的 `rke2/` 离线交付路径，让用户在不影响 standalone 的前提下，使用一份脚本初始化「1 个 server + N 个 agent」集群，并把 rainbond-cluster 安装到该集群之上，最终由 kuship-console 通过 region API 注册管理。

## What Changes

- 新建 `rke2/` 目录，提供 `rke2-version.env`（与 standalone/k3s-version.env 对齐的版本钉死机制）、`config-server.yaml`、`config-agent.yaml`、`registries.yaml`、`server-install.sh`、`agent-install.sh`、`bootstrap-rainbond.sh`、`images-package.sh`。
- 新增仓库根脚本 `rke2_build.sh`：与 `standalone_build.sh` 同构，离线打包 rke2 二进制 + 镜像清单 + rainbond-cluster.tgz 到 `rke2-bundle-<arch>.tar.zst` 单文件。
- `server-install.sh` 在目标节点完成：写入 `/etc/rancher/rke2/config.yaml`、加载离线镜像到 `containerd-images`、`systemctl enable --now rke2-server`、等待 `kubectl get nodes` Ready、在 `server/manifests/` 下落地 rainbond-cluster HelmChart。
- `agent-install.sh` 接受 `SERVER_URL` + `TOKEN` 两个必填参数，写入 agent config，启动 `rke2-agent.service` 并等待加入。
- `bootstrap-rainbond.sh` 在 server 上幂等渲染 `rainbond-cluster.yaml`（允许 `EIP=`、`UUID=`、`DB_*` 等环境变量覆盖，与 standalone/entrypoint.sh 行为对齐）。
- 文档：在仓库根 README 和 CLAUDE.md 增加 RKE2 多节点部署章节（如何把脚本与离线包传到目标机器、token 来源、kuship-console 如何把该集群作为 region 注册）。
- 不修改 kuship-console 任何 Java 代码——RKE2 集群通过既有的 region 注册流程接入；server 节点装好之后用户照常在 kuship-console 调用「集群接入」即可。

## Capabilities

### New Capabilities

- `rke2-cluster-bootstrap`: 多节点 RKE2 集群离线交付与引导能力，覆盖版本钉死、镜像离线打包、server / agent 安装脚本契约、rainbond-chart 引导落地与 kuship-console region 注册接入指引。

### Modified Capabilities

（无——standalone 路径与 kuship-console-app 行为不变。）

## Impact

- 新增脚本与配置：`rke2_build.sh`、`rke2/server-install.sh`、`rke2/agent-install.sh`、`rke2/bootstrap-rainbond.sh`、`rke2/images-package.sh`、`rke2/config-server.yaml`、`rke2/config-agent.yaml`、`rke2/registries.yaml`、`rke2/rke2-version.env`。
- 影响范围仅限仓库根 + `rke2/` 目录与文档；`standalone/`、`kuship-console/`、`kuship-ui/` 不变。
- 离线包产物 `rke2-bundle-<arch>.tar.zst` 体积预计 ~1.5GB，加入 `.gitignore`（已有同模式条目）。
- 用户外部依赖：目标节点需 systemd、root、外网或本地 mirror（仅初次安装时拉取 OS 包）；多节点之间需要 6443/9345/10250 端口互通——文档显式声明。
