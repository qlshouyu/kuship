# kuship RKE2 多节点离线交付

本目录提供把 kuship（rainbond-cluster）部署到 **多节点 RKE2 集群**的离线脚本与配置，与仓库根 `standalone/`（单节点 k3s）路径解耦、互不影响。

适用场景：生产环境需要 1 个控制面 server + N 个 worker agent 的拓扑，目标节点 **可联网或可访问内网 mirror**（首次构建离线包需要），上线节点 **完全离线**。

## 目录与产物

| 文件 | 角色 |
|------|------|
| `rke2-version.env`     | RKE2 版本钉死单一真相源（`RKE2_VERSION=v1.31.4+rke2r1`） |
| `images-package.sh`    | 拉 RKE2 二进制 + 镜像 + chart，打成 `rke2-bundle-<arch>.tar.zst` |
| `server-install.sh`    | 在 server 节点解包、写 config、启动 `rke2-server.service`、引导 rainbond-cluster |
| `agent-install.sh`     | 在 agent 节点加入集群（依赖 server 输出的 `RKE2_URL` / `RKE2_TOKEN`） |
| `bootstrap-rainbond.sh`| 在 server 节点渲染 `rainbond-cluster.yaml` HelmChart manifest |
| `config-server.yaml`   | RKE2 server 默认配置（disable ingress、CIDR、tls-san 占位） |
| `config-agent.yaml`    | RKE2 agent 默认配置（server / token 由 install 脚本注入） |
| `registries.yaml`      | containerd 镜像仓库配置（goodrain.me） |

仓库根 `rke2_build.sh` 是 **构建编排入口**（与 `standalone_build.sh` 同构，支持 `enable_proxy=` / `force_rebuild=`），其产物 `rke2-bundle-<arch>.tar.zst` 即是上线交付物。

## 端口表（防火墙 / 安全组放行）

| 端口        | 协议 | 用途                                                       |
|-------------|------|------------------------------------------------------------|
| 6443/tcp    | TCP  | Kubernetes API（kubectl 与 console 接入）                  |
| 9345/tcp    | TCP  | RKE2 supervisor（agent 注册、节点间控制面通信）            |
| 10250/tcp   | TCP  | kubelet（指标、日志、kubectl exec/logs）                   |
| 8472/udp    | UDP  | Flannel/Canal VXLAN（pod 跨节点网络）                      |
| 2379-2380/tcp | TCP | etcd peer（多 server HA 才需要，本期单 server 可不放行）  |

## 快速开始

### 1. 在开发机/构建机上生成离线包

```bash
# 默认（不开代理）
./rke2_build.sh

# 国内开发者：本机已开 v2ray/clash 监听 127.0.0.1:7897
./rke2_build.sh enable_proxy=1

# 强制重新生成（升级 RKE2_VERSION 后）
./rke2_build.sh enable_proxy=1 force_rebuild=1
```

产物：仓库根 `rke2-bundle-<arch>.tar.zst`（约 1.5 GB）。

### 2. 上传到 server 节点并解包

```bash
scp rke2-bundle-amd64.tar.zst root@<server-ip>:/tmp/
ssh root@<server-ip> 'tar -I zstd -xf /tmp/rke2-bundle-amd64.tar.zst -C /root/'
ssh root@<server-ip>  # 进入 server 节点
cd /root/rke2-amd64
```

### 3. 在 server 节点执行安装

```bash
# 干净节点：可选先生成固定 UUID
export UUID="$(uuidgen)"

# 控制面 IP（默认按 hostname -I 取第一个非回环；多网卡时建议显式指定）
export EIP="10.0.0.1"

sudo -E ./server-install.sh
# ...等待最多 5 分钟...
# 输出：
#   RKE2_URL=https://10.0.0.1:9345
#   RKE2_TOKEN=K10xxxxx::server:xxxxx
```

server 节点同时落地：
- `/etc/rancher/rke2/rke2.yaml`（kubeconfig，模式 0644）
- `/var/lib/rancher/rke2/server/node-token-bundle.txt`（仅 root 可读）

### 4. 在每个 agent 节点执行加入

```bash
# 复制同一份离线包并解压
scp rke2-bundle-amd64.tar.zst root@<agent-ip>:/tmp/
ssh root@<agent-ip> 'tar -I zstd -xf /tmp/rke2-bundle-amd64.tar.zst -C /root/'
ssh root@<agent-ip>
cd /root/rke2-amd64

sudo RKE2_URL='https://10.0.0.1:9345' \
     RKE2_TOKEN='K10xxxxx::server:xxxxx' \
     ./agent-install.sh
```

完成后在 server 节点执行：

```bash
kubectl --kubeconfig /etc/rancher/rke2/rke2.yaml get nodes
# NAME              STATUS   ROLES                       AGE
# kuship-server-1   Ready    control-plane,etcd,master   5m
# kuship-agent-1    Ready    <none>                      1m
```

### 5. 把集群接入 kuship-console（region 注册）

集群安装完成后，rainbond-cluster 已通过 HelmChart manifest 自动安装到 `rbd-system` namespace。把该集群作为一个 region 注册到 kuship-console：

1. 在 server 节点执行 `cat /etc/rancher/rke2/rke2.yaml`，复制 kubeconfig；
2. **把其中 `127.0.0.1` 替换为 server 节点对外可达 IP**（例如步骤 3 的 `EIP`）；
3. 在 kuship-console 控制台走「集群接入 → 通过 kubeconfig 接入」流程，粘贴上一步 kubeconfig，即可完成 region 注册。

> 本期不实现 console 自动 SSH 安装、token 自动获取等流程；上述步骤与 rainbond-console 既有 region 接入路径一致。

## 幂等与重装

| 场景 | 行为 |
|------|------|
| `server-install.sh` 在已 active 的节点重跑 | 打印 `already installed` 警告并 0 退出 |
| `server-install.sh --force` | 重新拷贝 config / images / manifests 并 `systemctl restart rke2-server` |
| `agent-install.sh` 在已 active 节点重跑   | 打印 `already joined` 警告并 0 退出 |
| 卸载 server | `sudo /usr/local/bin/rke2-uninstall.sh` |
| 卸载 agent  | `sudo /usr/local/bin/rke2-uninstall.sh`（同名脚本，自动识别角色） |

## 升级 RKE2 版本

1. 编辑 `rke2/rke2-version.env`，修改 `RKE2_VERSION=` 为目标版本；
2. `./rke2_build.sh force_rebuild=1` 重新生成离线包；
3. 把新包传到所有节点；
4. **逐节点**执行 `sudo ./server-install.sh --force` 或 `sudo ./agent-install.sh`（在已加入节点上 agent 重装会先打印 `already joined`，需先 `systemctl stop rke2-agent` 再删除 `/etc/rancher/rke2/config.yaml` 才会重写——版本升级建议直接 `rke2-uninstall.sh` 后重新 join）。

## 故障排查

| 现象 | 排查命令 |
|------|----------|
| server 启动慢 / token 不出现 | `journalctl -u rke2-server -f` |
| agent 加不进来 | server 节点 `journalctl -u rke2-server`、agent 节点 `journalctl -u rke2-agent`，确认 9345 / 6443 端口可达（`nc -zv <server-ip> 9345`） |
| pod 拉镜像失败 | `crictl images`（在 `/var/lib/rancher/rke2/bin/` PATH 下）；离线镜像未导入说明 `agent/images/*.tar.zst` 缺失 |
| rainbond-operator 不 Running | `kubectl -n rbd-system get helmchart rainbond-cluster -o yaml` 看 chart 状态；`kubectl -n rbd-system logs -l app=rainbond-operator` |
