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

- [ ] 4.1 在能访问 `github.com` 与 `docker.io` 的构建机上执行 `./standalone/images-package.sh`，确认输出 `k3s-images-arm64.tar.zst`（arm64 机器）或 `k3s-images-amd64.tar.zst`（amd64 机器）
- [ ] 4.2 用 `zstd -d --stdout k3s-images-<arch>.tar.zst | tar -tf - | head -50` 抽样检查归档中包含 `klipper-helm:v0.9.14-build20260309`、`mirrored-coredns-coredns:1.14.2`、`mirrored-metrics-server:v0.8.1` 等期望 tag
- [ ] 4.3 至少更新 `k3s-images-arm64.tar.zst`（必）；若有 amd64 联网构建机，同时更新 `k3s-images-amd64.tar.zst`
- [ ] 4.4 `git add` 新生成的 `k3s-images-<arch>.tar.zst` 与 `standalone/k3s-version.env`、改动后的 `standalone/images-package.sh`、`standalone/Dockerfile`、`standalone_build.sh`

## 5. 端到端验证

- [ ] 5.1 在仓库根执行 `./standalone_build.sh`，确认顺序为：加载 env → 重打离线包 → 构建镜像，且 `rainbond-dev:v6.7.1-release` 镜像构建成功
- [ ] 5.2 执行 `docker compose -f docker/docker-compose.yaml up -d`，等待至少 3 分钟
- [ ] 5.3 执行 `docker exec kuship-rainbond k3s kubectl get pods -A`，确认 `kube-system/coredns-*`、`kube-system/metrics-server-*` 进入 `Running`，且 `rbd-system/helm-install-rainbond-cluster-*` 不再 `ImagePullBackOff`
- [ ] 5.4 执行 `docker exec kuship-rainbond k3s ctr -n k8s.io images list | grep -E "(klipper-helm|coredns|metrics-server)"`，确认输出的版本号与 k3s v1.33.10+k3s1 期望一致
- [ ] 5.5 执行 `docker compose -f docker/docker-compose.yaml down -v` 清理验证环境

## 6. 收尾

- [x] 6.1 运行 `openspec validate refresh-standalone-image-bundle` 确认全部产物校验通过
- [ ] 6.2 在 PR 描述中链接 proposal/design/specs/tasks，并附 5.3、5.4 的关键输出截图或粘贴片段，作为修复证据
