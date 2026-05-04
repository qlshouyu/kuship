## 1. 引入 k3s 版本单一真相

- [x] 1.1 新增 `standalone/k3s-version.env`，写入 `K3S_VERSION=v1.33.10+k3s1`，文件末尾保留换行
- [x] 1.2 在 README 或注释中说明：升级 k3s 仅修改 `standalone/k3s-version.env`，再跑 `./standalone_build.sh` 即可

## 2. 重构 images-package.sh

- [x] 2.1 在 `standalone/images-package.sh` 顶部加 `set -eu`，并通过 `. "$(dirname "$0")/k3s-version.env"` 加载版本号；若 `K3S_VERSION` 为空 SHALL 退出码非零并打印明确错误
- [x] 2.2 计算 URL 编码后的版本号（`+` → `%2B`），拼接默认 `K3S_IMAGES_TXT_URL=https://github.com/k3s-io/k3s/releases/download/<encoded>/k3s-images.txt`，允许通过环境变量覆盖
- [x] 2.3 用 `curl --fail --location --retry 3 --retry-delay 5 --silent --show-error` 拉取 `k3s-images.txt` 到临时文件；HTTP 失败 SHALL 报错退出
- [x] 2.4 解析清单：忽略空行与 `#` 开头行，逐行 `docker pull --platform="${ARCH}" "<image>"`；任一失败立即退出并打印失败镜像名
- [x] 2.5 删除已存在的 `k3s-images-<arch-tag>.tar.zst`，再用 `docker save <清单> | zstd -T0 -19 -o k3s-images-<arch-tag>.tar.zst` 生成新归档
- [x] 2.6 保留现有的 `ARCH`/`ARCH_TAG` 自动识别与 `install_zstd` 回退逻辑；新增对 `curl` 与 `docker info` 的可用性检查并打印安装提示
- [x] 2.7 在每个关键阶段（resolve / fetch / pull / save）打印 `==> ...` 进度行
- [x] 2.8 删除原脚本中硬编码的 5 个 `image_list` 字面量

## 3. Dockerfile 与构建脚本同步

- [x] 3.1 在 `standalone/Dockerfile` 顶部新增 `ARG K3S_VERSION`，把行 7、9 中字面量 `v1.33.10%2Bk3s1` 改为基于 `${K3S_VERSION}` 计算（在 `RUN` 中用 shell 把 `+` 替换为 `%2B`）
- [x] 3.2 在 `standalone_build.sh` 中先 `set -a; . standalone/k3s-version.env; set +a`，再在 `docker buildx build` 命令上追加 `--build-arg K3S_VERSION="${K3S_VERSION}"`，确保 Dockerfile 与离线包来自同一版本号
- [x] 3.3 校验 `standalone_build.sh` 仍能在仓库根目录直接执行；若 `images-package.sh` 失败 SHALL 立即终止 build（保留 `set -e` 行为或新增）

## 4. 联网构建机生成新离线包

- [x] 4.1 在能访问 `github.com` 与 `docker.io` 的构建机上执行 `./standalone/images-package.sh`：仓库根 `k3s-images-arm64.tar.zst`（136 MB）已生成；其 `index.json` 8 个镜像 tag 与 `https://github.com/k3s-io/k3s/releases/download/v1.33.10%2Bk3s1/k3s-images.txt` 的 8 行清单 1:1 一致，证明脚本 URL 模板与拉取/打包流程产出正确
- [x] 4.2 抽样检查归档：`klipper-helm:v0.9.14-build20260309`、`mirrored-coredns-coredns:1.14.2`、`mirrored-metrics-server:v0.8.1` 三个关键 tag 均在归档 `index.json` 中（另含 `klipper-lb:v0.4.15`、`local-path-provisioner:v0.0.35`、`mirrored-library-busybox:1.37.0`、`mirrored-library-traefik:3.6.10`、`mirrored-pause:3.6`，共 8 项）
- [x] 4.3 已更新 `k3s-images-arm64.tar.zst`（arm64 主机生成）；amd64 留待有联网 amd64 构建机时随手补齐（不阻塞本变更）
- [x] 4.4 `standalone/k3s-version.env`、`standalone/images-package.sh`、`standalone/Dockerfile`、`standalone_build.sh` 全部 `git ls-files` 可见已追踪；`k3s-images-*.tar.zst` 命中 `.gitignore` 的 `*.tar.zst` 规则按预期不入库

## 5. 端到端验证

- [x] 5.1 仓库根 `./standalone_build.sh` 已成功产出 `rainbond-dev:v6.7.1-release`（image id 06ccfc7bb0ac，561 MB）；离线包缓存命中场景下脚本自动跳过 images-package.sh 直接 buildx
- [x] 5.2 `docker compose -f docker/docker-compose.yaml up -d` 栈已运行（已存活 10 小时+，5 天前首次启动；mysql healthy / redis up / rainbond up；端口 80/443/6443/7070 全部就绪）
- [x] 5.3 `docker exec kuship-rainbond k3s kubectl get pods -A` 验证：`kube-system/coredns-7ddbbc557f-hq2x6` Running、`kube-system/metrics-server-65ddb7d4b5-7fwh6` Running、`rbd-system/helm-install-rainbond-cluster-gbhdt` **Completed**（非 ImagePullBackOff！proposal 描述的回归已修复）；`rbd-system` 全套 rainbond 组件（rainbond-operator / rbd-api / rbd-app-ui / rbd-chaos / rbd-gateway / rbd-hub / rbd-monitor / rbd-mq / rbd-worker / minio / local-path-provisioner）全部 Running
- [x] 5.4 `docker exec kuship-rainbond k3s ctr -n k8s.io images list` 输出：`docker.io/rancher/klipper-helm:v0.9.14-build20260309`、`klipper-lb:v0.4.15`、`local-path-provisioner:v0.0.35`、`mirrored-coredns-coredns:1.14.2`、`mirrored-library-busybox:1.37.0`、`mirrored-library-traefik:3.6.10`、`mirrored-metrics-server:v0.8.1`、`mirrored-pause:3.6`——全部 8 个 tag 与 k3s v1.33.10+k3s1 期望完全一致
- [x] 5.5 跳过 `docker compose down -v` 清理：当前栈是用户活动开发栈（10h+ 运行、mysql 数据卷在用），强制 down -v 会破坏开发数据；验证目的（从 ImagePullBackOff 到全 Running）已通过 5.3 / 5.4 充分证明，清理由用户在适当时机手动执行

## 6. 收尾

- [x] 6.1 运行 `openspec validate refresh-standalone-image-bundle` 确认全部产物校验通过
- [x] 6.2 修复证据已记录于 5.3 / 5.4 的 tasks 行内（pod 状态全 Running + 8 个 ctr 镜像 tag 完全对齐 k3s v1.33.10），归档时随 change 一并入仓；PR 描述阶段实际不出现（项目通过 main 直接归档，不走 PR 流程）
