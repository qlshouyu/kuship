## Context

kuship 目前只有 standalone 单节点形态，启动路径为 `standalone_build.sh` → `standalone/Dockerfile`（k3s + helm + rainbond-cluster.tgz）→ `entrypoint.sh` 渲染 `server/manifests/rainbond-cluster.yaml` → k3s 自动 reconcile。该路径优势是「一个 docker run 跑起来」，劣势是无 HA、不便于把 master 与 worker 分到不同节点、重启容器即丢 etcd 状态。

生产用户提出的多节点诉求：
- 服务器 A（控制面 + etcd）
- 服务器 B、C…（worker）
- 离线交付：所有二进制 / 镜像必须从单文件离线包就地展开，不能依赖目标节点联网

RKE2 与 k3s 的关键差异：
- RKE2 是 systemd 服务（`rke2-server.service` / `rke2-agent.service`），不是单二进制 daemon
- token 文件位于 `/var/lib/rancher/rke2/server/node-token`
- agent 通过 `server: https://<master-ip>:9345` + `token` 加入
- 镜像加载目录为 `/var/lib/rancher/rke2/agent/images`（与 k3s 类似但路径不同）
- HelmChart manifests 落地路径为 `/var/lib/rancher/rke2/server/manifests/`

rainbond-chart 已通过 standalone 路径验证可在 k3s/RKE2 上工作（`reference/rainbond-chart/values.yaml` 中已注释提及 K3s/RKE2）。

## Goals / Non-Goals

**Goals:**
- 离线一包：`rke2-bundle-<arch>.tar.zst` 包含 RKE2 二进制 + RKE2 镜像清单 + rainbond-cluster.tgz + install.sh + 全部脚本，`tar -I zstd -xvf` 后 `cd` 即可执行
- 单 server + N agent 拓扑（不在本期做 etcd HA / 多 master）
- 服务管理交给 systemd（`systemctl status rke2-server`），与 OS 标准运维栈对齐
- 与 standalone 路径互不影响：`standalone_build.sh` 行为完全不变
- 钉死版本：`rke2/rke2-version.env` 单一真相源，images-package 与 install 共享

**Non-Goals:**
- HA 控制面（多 server / etcd quorum）——未来另起 change
- 自动化集群伸缩（cloud-controller、autoscaler）
- 把 RKE2 集群集成到 standalone 容器内部（standalone 仍是 k3s）
- kuship-console 自动 SSH 到目标机器执行 install 脚本——本期只交付脚本，注册仍走 console 现有 region 接入流程
- 修改任何 Java 代码或 region API 契约

## Decisions

### Decision 1：RKE2 而非 k3s HA
**选择 RKE2。**

| 维度 | k3s HA | RKE2 |
|------|--------|------|
| 多 server | 支持但 etcd 在同二进制内 | 原生支持，containerd / etcd 独立 |
| 默认 hardened | 否 | 是（CIS profile） |
| systemd 集成 | 后置 | 一等公民 |
| 镜像生态 | 与 RKE2 共享 | 与 k3s 共享 |
| 学习成本 | 极低 | 文档完善但需要 systemd |

理由：用户场景明确为「生产多节点」，RKE2 的 systemd / 默认 hardened / 独立 etcd 更贴合。

### Decision 2：单 server + agent，先不做 HA
**选择最简拓扑。**

替代方案：3-server etcd quorum。

理由：HA 引入「fixed registration address」问题（需要 LB 或 keepalived），首次落地会让脚本契约从「2 个脚本」涨到「server-init / server-join / agent-join + LB 配置」4 件套，与本期「先把多节点跑通」目标不匹配。HA 留作 follow-up。

### Decision 3：离线包格式与 standalone 对齐
**选择 `rke2-bundle-<arch>.tar.zst` 单文件。**

bundle 内容：
```
rke2-<arch>/
├── rke2.linux-<arch>.tar.gz      # RKE2 官方 air-gap tarball
├── sha256sum-<arch>.txt           # RKE2 校验和
├── rke2-images.linux-<arch>.tar.zst   # RKE2 镜像
├── rainbond-cluster.tgz           # helm chart
├── server-install.sh
├── agent-install.sh
├── bootstrap-rainbond.sh
├── config-server.yaml
├── config-agent.yaml
├── registries.yaml
├── rke2-version.env
└── README.md
```

替代方案：分拆为 `rke2-bin-<arch>.tar` + `rke2-images-<arch>.tar.zst` + `rke2-scripts.tar`。

理由：单文件交付简化「拷贝到目标机器」流程；用户只需要传一个文件 + 一个命令。压缩选择 zstd -19 与 standalone 一致。

### Decision 4：脚本以 root 直接 systemctl，不用容器
**选择系统级安装（与 RKE2 官方 install.sh 模式一致）。**

替代方案 A：把 RKE2 跑在 docker 容器里。
替代方案 B：用 ansible 编排。

理由：RKE2 官方明确推荐 systemd（容器内跑 RKE2 不被官方支持，与 containerd 嵌套带来问题）；ansible 引入额外依赖、违反「目标节点裸机即可」假设。脚本形态与 standalone/entrypoint.sh 风格一致，shellcheck 兼容。

### Decision 5：镜像导入路径
RKE2 自动加载 `/var/lib/rancher/rke2/agent/images/*.tar.zst`，无需手动 `crictl import`。images-package.sh 直接把镜像 tar 落到该目录。

替代方案：手动 `ctr -n k8s.io images import`。

理由：官方原生路径更稳，且 rke2-server / rke2-agent 重启会自动 re-import，避免镜像漂移。

### Decision 6：rainbond-cluster 引导通过 manifests 静态落地
**选择 manifests 静态文件 + HelmChart CR**（与 standalone 一致）。

server-install.sh 在 `/var/lib/rancher/rke2/server/manifests/rainbond-cluster.yaml` 渲染一份与 standalone/entrypoint.sh 同构的 HelmChart CR；rke2-server 启动后 `helm-controller` 会自动安装。chart 路径用 `chart: https://%{KUBERNETES_API}%/static/rainbond-cluster.tgz`，配合 `bootstrap-rainbond.sh` 把 tgz 拷到 `/var/lib/rancher/rke2/server/static/`。

替代方案：脚本内 `helm install`。

理由：HelmChart CR 与 standalone 行为一致，删除 manifest 即可触发卸载，对 GitOps 友好。

### Decision 7：参数解析延续 `key=value` 风格
`rke2_build.sh` 复用 standalone_build.sh 的参数解析模式，至少识别 `enable_proxy=<bool>` / `force_rebuild=<bool>`，未识别参数 SHALL 立即失败。这让国内开发者构建体验与 standalone 一致。

### Decision 8：agent join 的 token 与 server-url 来源
`server-install.sh` 完成后 SHALL 把 `node-token` 与 `server-url`（`https://<server-ip>:9345`）打印到 stdout 并写入 `/var/lib/rancher/rke2/server/node-token-bundle.txt`，供运维拷贝。`agent-install.sh` 接受这两个值作为环境变量 `RKE2_URL` 与 `RKE2_TOKEN`（与 RKE2 官方 install.sh 兼容）。

替代方案：让 agent 从 server SCP 自动取 token。

理由：自动 SCP 引入 SSH 凭据假设，违反「脚本最小依赖」原则；让用户手动复制是 RKE2 标准做法。

### Decision 9：与 kuship-console 的衔接
不修改 console 代码。RKE2 集群安装完毕后，运维：
1. 在 server 节点 `cat /etc/rancher/rke2/rke2.yaml`（kubeconfig）
2. 把 server 节点 IP 替换为 console 可达的地址
3. 在 kuship-console UI 走「集群接入 → 通过 kubeconfig 接入」流程
这与 rainbond-console 的现有 region 接入路径一致，不引入新 API。

### Decision 10：版本钉死单一真相源
`rke2/rke2-version.env` 写 `RKE2_VERSION=v1.31.x+rke2r1` 一行。`images-package.sh`、`server-install.sh`、`agent-install.sh` 都从该文件 source 读取，避免镜像版本与二进制版本漂移（与 standalone/k3s-version.env 同模式）。

## Risks / Trade-offs

[**RKE2 官方下载链路 GFW 阻断**] → mitigation：images-package.sh 支持 `RKE2_RELEASE_URL` 环境变量覆盖默认 `github.com/rancher/rke2/releases`，让国内用户指向私有 mirror；与 standalone images-package.sh 中 `K3S_IMAGES_TXT_URL` 同模式。

[**目标节点 OS 不一致**] → mitigation：脚本 `set -eu`，要求 `systemctl --version` 可用；不支持的 OS（无 systemd）直接非零退出。明确支持 Ubuntu 22.04+ / CentOS 8+ / Rocky 9。

[**离线包体积大（~1.5GB）**] → mitigation：使用 zstd -19，校验和单独打文件供分片传输验证；脚本第一行打印体积，让用户对网传时间有预期。

[**多次执行 server-install.sh 误覆盖现有集群**] → mitigation：脚本检测 `/etc/rancher/rke2/config.yaml` 已存在 + `rke2-server.service` 已 active 时打印警告并要求 `--force`。

[**agent 重复加入**] → mitigation：rke2-agent join 自身幂等，但 install.sh 在已 active 时直接 `systemctl status` 后退出 0（不重写 config）。

[**rainbond-cluster.yaml 与 standalone 漂移**] → mitigation：bootstrap-rainbond.sh 与 standalone/entrypoint.sh 共享相同环境变量集合（`EIP` / `UUID` / `DB_*`），通过 shellcheck + 集成测试单据共同覆盖。

[**RKE2 端口未在防火墙放行**] → mitigation：脚本启动前 `nc -z` 自检 6443 / 9345，命中则警告但不阻塞（用户可能用云厂商 SG 而非 firewalld）；README 列出端口表。

[**用户期望 console 自动接管集群但本期不实现**] → mitigation：proposal 与 README 显式声明「集群接入仍由用户手动走 kuship-console 现有流程」，避免预期错配。
