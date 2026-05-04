## 1. 重写 standalone_build.sh

- [x] 1.1 在 `standalone_build.sh` 顶部初始化默认值 `ENABLE_PROXY=0`、`FORCE_REBUILD=0`，并实现 `is_truthy()` 与 `usage()` 帮助函数（usage 至少列出 `enable_proxy` 与 `force_rebuild` 两个开关）
- [x] 1.2 实现 `for arg in "$@"` 解析循环：识别 `enable_proxy=*`、`force_rebuild=*`、`-h`/`--help`；未识别参数 SHALL 调 `usage` 并以非零退出
- [x] 1.3 在加载 `standalone/k3s-version.env` 之后、调用 `images-package.sh` 之前，根据当前 `uname -m`（允许 `ARCH` 覆盖）推断 `ARCH_TAG`，与 `standalone/images-package.sh` 中规则保持一致
- [x] 1.4 当 `is_truthy "${ENABLE_PROXY}"` 时，`export` `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`ALL_PROXY` 为 `http://127.0.0.1:7897`，并把 `localhost,127.0.0.1,::1` 写入 `NO_PROXY`/`no_proxy`，输出 `==> proxy enabled: http://127.0.0.1:7897 ...` 提示
- [x] 1.5 当 `${SCRIPT_DIR}/k3s-images-${ARCH_TAG}.tar.zst` 存在且 `force_rebuild` 非真值时，跳过 `./standalone/images-package.sh` 并打印 `==> 检测到 ... 跳过` 与 `force_rebuild=1` 的提示；否则照常执行 `./standalone/images-package.sh`
- [x] 1.6 调用 `docker buildx build -f standalone/Dockerfile --build-arg K3S_VERSION="${K3S_VERSION}" -t rainbond-dev:v6.7.1-release .`，保持 `set -euo pipefail`，构建命令前补充一行进度日志 `==> docker buildx build ...`
- [x] 1.7 `bash -n standalone_build.sh` 与 `shellcheck`（如有）通过

## 2. 文档与可发现性

- [x] 2.1 README 中 standalone 章节追加用法示例：`./standalone_build.sh enable_proxy=1`、`./standalone_build.sh force_rebuild=1`、`./standalone_build.sh -h`
- [x] 2.2 在 README 中说明 `enable_proxy=1` 仅控制 `curl` / 当前 shell 进程的代理；`docker buildx` 拉取基础镜像 (`alpine/helm:3` / `ubuntu:24.04`) 仍受 docker daemon 自身代理配置影响，需要时另行配置 Docker Desktop 的 Proxy

## 3. 验证

- [x] 3.1 `./standalone_build.sh --help` 输出 usage 并以 0 退出
- [x] 3.2 `./standalone_build.sh do_something=1` 报 `unknown` 并以非零退出
- [x] 3.3 仓库根存在 `k3s-images-arm64.tar.zst` 时，`./standalone_build.sh` 输出"检测到 ... 跳过"，确认 `images-package.sh` 未被执行（可通过日志或 `set -x` 验证）
- [x] 3.4 临时把 `k3s-images-arm64.tar.zst` 改名隐藏，再执行 `./standalone_build.sh`，确认调用了 `images-package.sh`（联网受限场景可中断验证后立刻恢复文件）
- [x] 3.5 仓库根存在 `k3s-images-arm64.tar.zst` 时执行 `./standalone_build.sh force_rebuild=1`，确认 `images-package.sh` 被调用
- [x] 3.6 执行 `./standalone_build.sh enable_proxy=1` 时，在脚本中通过 `echo "HTTPS_PROXY=$HTTPS_PROXY"` 临时打点（或读取脚本输出的提示行），确认代理变量被正确导出
- [x] 3.7 `./standalone_build.sh` 不带任何参数，且本机有可用网络/已有缓存时能跑通到 `docker buildx build` 阶段（与现状行为一致）

## 4. buildx 代理穿透与 apt mirror（首次实测后追加）

- [x] 4.1 在 `standalone/Dockerfile` 的 `base` 与 `stage-1` 两个 FROM 之后均加 `ARG HTTP_PROXY ARG HTTPS_PROXY ARG http_proxy ARG https_proxy ARG NO_PROXY ARG no_proxy`
- [x] 4.2 在 `standalone/Dockerfile` 的 `stage-1` 加 `ARG APT_MIRROR=http://mirrors.aliyun.com/ubuntu-ports`
- [x] 4.3 在 `stage-1` 的 `apt-get update` 之前 `sed` 替换 mirror、`sed` 收敛 `Components: main`、`unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy`，并把 install 改为 `apt-get -y install --no-install-recommends ca-certificates`
- [x] 4.4 在 `standalone_build.sh` 中：当 `enable_proxy=1` 时给 `docker buildx build` 追加 6 条 `--build-arg`（HTTP_PROXY/HTTPS_PROXY/http_proxy/https_proxy/NO_PROXY/no_proxy），URL 用 `http://host.docker.internal:7897`；缺省时不附加任何 proxy build-arg
- [x] 4.5 实测 `./standalone_build.sh enable_proxy=1` 端到端走通，镜像 `rainbond-dev:v6.7.1-release` 成功 export

## 5. 收尾

- [x] 5.1 运行 `openspec validate add-build-proxy-and-cache` 确认全部产物校验通过
- [ ] 5.2 PR 描述中链接 proposal/design/specs/tasks，并附 3.1–3.6、4.5 的关键命令输出片段
