## 1. 骨架与版本钉死

- [x] 1.1 新建 `rke2/` 目录与 `rke2/rke2-version.env`，初值 `RKE2_VERSION=v1.31.4+rke2r1`
- [x] 1.2 新建 `rke2/registries.yaml`，结构与 `standalone/registries.yaml` 一致（mirrors + configs）
- [x] 1.3 新建 `rke2/config-server.yaml`，包含 `node-name`、`disable: [rke2-ingress-nginx]`、`write-kubeconfig-mode: "0644"`、`tls-san` 占位、`cluster-cidr` / `service-cidr` 默认值
- [x] 1.4 新建 `rke2/config-agent.yaml`，包含 `node-name` 占位（`server` / `token` 由 install 脚本注入）

## 2. 离线包脚本

- [x] 2.1 新建 `rke2/images-package.sh`：source `rke2-version.env`、检测架构、按 `RKE2_RELEASE_URL` 默认值下载 `rke2.linux-<arch>.tar.gz` + `sha256sum-<arch>.txt` + `rke2-images.linux-<arch>.tar.zst`，对二进制做 `sha256sum -c` 校验
- [x] 2.2 在 images-package.sh 中复用 `reference/rainbond-chart/` 通过 `helm package` 生成 `rainbond-cluster.tgz`（与 standalone Dockerfile base 阶段同模式，需要 docker run alpine/helm 容器）
- [x] 2.3 把全部产物 + 4 个安装脚本 + 4 个 yaml 配置 + `rke2-version.env` + `README.md` 收集到 `rke2-<arch-tag>/` 目录，`tar -I 'zstd -T0 -19' -cvf rke2-bundle-<arch-tag>.tar.zst rke2-<arch-tag>/`
- [x] 2.4 给所有 `*.sh` 设置 `chmod 0755`，`tar` 之前确认权限位
- [x] 2.5 镜像清单为空 / 下载失败 / sha256 校验失败时立即非零退出，错误消息明确

## 3. 构建编排入口 rke2_build.sh

- [x] 3.1 新建仓库根 `rke2_build.sh`，复用 `standalone_build.sh` 的 `key=value` 参数解析（识别 `enable_proxy` / `force_rebuild`，未识别参数立即失败）
- [x] 3.2 实现 `--help` / `-h`：打印 usage 并以退出码 0 终止
- [x] 3.3 当 `enable_proxy=1` 时 export `HTTP_PROXY` / `HTTPS_PROXY`（+ 小写）/ `ALL_PROXY=http://127.0.0.1:7897`、`NO_PROXY=localhost,127.0.0.1`
- [x] 3.4 按当前架构（含 `ARCH` 覆盖）推断 `rke2-bundle-<arch-tag>.tar.zst`，存在且 `force_rebuild!=1` 时跳过 images-package.sh 并打印缓存命中说明
- [x] 3.5 缓存未命中或 `force_rebuild=1` 时调用 `./rke2/images-package.sh`
- [x] 3.6 `set -euo pipefail`，K3S_VERSION 校验改为 `RKE2_VERSION` 校验，缺失则非零退出
- [x] 3.7 `chmod +x rke2_build.sh`

## 4. server-install.sh

- [x] 4.1 新建 `rke2/server-install.sh`，支持 `--force` 参数；干净节点流程按 spec 步骤 (1)–(11) 顺序实现
- [x] 4.2 root / sudo 权限校验 + systemd 校验 + `RKE2_VERSION` 校验
- [x] 4.3 解压 `rke2.linux-<arch>.tar.gz` 到 `/usr/local/`（`tar xzf rke2.linux-<arch>.tar.gz -C /usr/local`）
- [x] 4.4 镜像离线包拷贝到 `/var/lib/rancher/rke2/agent/images/`
- [x] 4.5 config / registries 拷贝到 `/etc/rancher/rke2/`
- [x] 4.6 调用 `bootstrap-rainbond.sh` 渲染 manifests，并把 `rainbond-cluster.tgz` 拷贝到 `/var/lib/rancher/rke2/server/static/`
- [x] 4.7 `systemctl enable --now rke2-server.service`
- [x] 4.8 轮询 `node-token`（最多 300 秒），写 `node-token-bundle.txt`，stdout 打印 `RKE2_URL=` + `RKE2_TOKEN=`
- [x] 4.9 检测已 active + config 已存在时打印警告退出 0；`--force` 时重新拷贝并 restart
- [x] 4.10 token 等待超时打印 `journalctl -u rke2-server` 提示并非零退出

## 5. agent-install.sh

- [x] 5.1 新建 `rke2/agent-install.sh`，校验 `RKE2_URL` / `RKE2_TOKEN` 必填
- [x] 5.2 解压 RKE2 二进制到 `/usr/local/`，镜像包到 `/var/lib/rancher/rke2/agent/images/`
- [x] 5.3 拷贝 `config-agent.yaml` → `/etc/rancher/rke2/config.yaml`，sed 注入 / 替换 `server:` 与 `token:` 行
- [x] 5.4 拷贝 `registries.yaml`
- [x] 5.5 `systemctl enable --now rke2-agent.service`
- [x] 5.6 轮询 `is-active`（最多 300 秒）
- [x] 5.7 已 active 时打印 `already joined` 警告退出 0

## 6. bootstrap-rainbond.sh

- [x] 6.1 新建 `rke2/bootstrap-rainbond.sh`，与 `standalone/entrypoint.sh::rainbond_cluster_yaml()` 共享相同变量集合（`EIP` / `UUID` / `VERSION` / `DB_*` / `UI_DB_*` / `DB_REGION_ENABLE` / `DB_UI_ENABLE`）
- [x] 6.2 渲染目标路径改为 `/var/lib/rancher/rke2/server/manifests/rainbond-cluster.yaml`
- [x] 6.3 `EIP` 为空时 `hostname -i | awk` 取第一个 IPv4 地址（与 standalone 同逻辑）
- [x] 6.4 输出文件结尾换行 + `set -eu`

## 7. 文档

- [x] 7.1 新建 `rke2/README.md`，包含：构建命令 / 拷贝离线包步骤 / server 安装命令 / agent 安装命令 / 端口表（6443、9345、10250、8472）/ kuship-console 接入指引
- [x] 7.2 在仓库根 `README.md` 增加 RKE2 多节点章节（可链接至 `rke2/README.md`，但端口表与 `./rke2_build.sh` 命令直接出现在根 README）
- [x] 7.3 在仓库根 `CLAUDE.md` 增加 `rke2/` 目录说明
- [x] 7.4 更新 `.gitignore`：忽略 `rke2-bundle-*.tar.zst`（既有 `*.tar.zst` 已覆盖，无需额外改动）

## 8. 验证

- [x] 8.1 `./rke2_build.sh --help` 输出 usage，退出码 0
- [x] 8.2 `./rke2_build.sh do_something=1` 报 unknown 错误，非零退出
- [x] 8.3 `./rke2_build.sh enable_proxy=1` 联网构建：拉取 RKE2 二进制（31 MB）+ images（724 MB）+ sha256 校验通过 + helm package chart + tar+zstd 产出 `rke2-bundle-arm64.tar.zst`（754 MB）；`tar -tvf` 列出全部 12 条目（含 3 个可执行 `*.sh`、4 个 yaml、`rke2-version.env`、`README.md`、binary tarball、images tarball、sha256、chart）
- [x] 8.4 二次执行 `./rke2_build.sh` 命中缓存：打印 `==> 检测到 .../rke2-bundle-arm64.tar.zst，跳过 ./rke2/images-package.sh`，未调用 images-package.sh，退出 0
- [x] 8.5 `RKE2_RELEASE_URL=http://127.0.0.1:1 ./rke2_build.sh force_rebuild=1` 验证 force_rebuild 分支调用 images-package.sh（curl 连接 127.0.0.1:1 失败，确认下载分支被进入；非缓存命中分支）
- [x] 8.6 `shellcheck` 全部 `*.sh`（`rke2_build.sh` / `images-package.sh` / `server-install.sh` / `agent-install.sh` / `bootstrap-rainbond.sh`）无错误（仅 SC1091 / SC2012 info 级警告，与 standalone 同模式，已确认可接受）
- [x] 8.7 离线包解压完整性 + 安装脚本 dry-run 校验：在 ubuntu:24.04 docker 容器内 `bash -n` 三脚本无语法错误；非 root 用户运行 `server-install.sh` / `agent-install.sh` 命中 `ERROR: 需以 root 用户运行`；root 用户无 systemctl 容器内运行命中 `ERROR: 未检测到 systemctl`，确认所有早期校验路径正确生效。**真实 Linux 节点 + systemd 的 `kubectl get nodes` Ready 验证延后至生产部署时**（与 `refresh-standalone-image-bundle` / `add-docker-compose-stack` 同模式：bundle 与脚本就绪，待真机 systemd 环境 e2e 验证）
- [ ] 8.8 server 节点 `kubectl get pods -n rbd-system` 看到 rainbond-operator 进入 Running（待真机 RKE2 节点验证）
- [ ] 8.9 在 kuship-console 走集群接入流程，把 RKE2 集群作为 region 注册成功（待真机 RKE2 节点 + console 部署后验证）
- [x] 8.10 `openspec validate enable-rke2-cluster --strict` 通过

## 9. 收尾

- [x] 9.1 `git status` 检查只新增 `rke2/`、`rke2_build.sh`、`.gitignore` 与文档差异，未误改 `standalone/`、`kuship-console/`
- [x] 9.2 任务全部完成后准备 `/opsx:archive enable-rke2-cluster`（8.8 / 8.9 真机 RKE2 e2e 验证延后至生产部署）
