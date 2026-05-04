## Context

`standalone_build.sh` 是仓库的构建入口，当前长这样：

```bash
#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"; cd "${SCRIPT_DIR}"
set -a; . ./standalone/k3s-version.env; set +a
[ -z "${K3S_VERSION:-}" ] && exit 1
./standalone/images-package.sh
docker buildx build -f standalone/Dockerfile --build-arg "K3S_VERSION=${K3S_VERSION}" -t rainbond-dev:v6.7.1-release .
```

两个体验痛点（详见 proposal）：网络受限时没有代理通道；离线包已存在时仍重打。

约束：
- 必须保持向后兼容：不带任何参数时行为与现状一致。
- 代理开关只在脚本 shell 进程中临时生效，不污染调用方环境。
- 离线包文件命名由 `images-package.sh` 决定（`k3s-images-<arch-tag>.tar.zst`，`<arch-tag>` 由 `uname -m` 推断或 `ARCH` 环境变量覆盖）。本脚本必须用相同规则推断文件名，否则缓存判断失真。

## Goals / Non-Goals

**Goals:**
- `enable_proxy=1` 时把 `http://127.0.0.1:7897` 同时设给 `curl`（取 k3s-images.txt）、`docker pull`（拉镜像）、`docker buildx build`（构建阶段拉基础镜像 `alpine/helm:3` / `ubuntu:24.04`）。
- 仓库根存在 `k3s-images-<arch>.tar.zst` 时跳过 `images-package.sh`，并明确告诉用户跳过原因；可通过 `force_rebuild=1` 强制重生成。
- 参数解析容错：未识别的参数 → 报错并打印用法；`enable_proxy=0` / 未提供 → 不改环境变量。

**Non-Goals:**
- 不引入复杂的命令行解析框架（getopts/argparse）——`key=value` 与 proposal 中用户给定的写法一致即可。
- 不让 `images-package.sh` 自己读代理参数（保持单一职责）。本脚本通过 export 环境变量传递。
- 不改 `Dockerfile` 中的网络配置；buildx 自动接收宿主机的 `HTTP(S)_PROXY` 环境变量。
- 不持久化代理设置（仅在本次执行有效）。

## Decisions

### 决策 1：参数语法与解析

- 采用 `key=value` 风格，与用户在 propose 中的样例一致：`enable_proxy=1`、`force_rebuild=1`。
- 解析方式：`for arg in "$@"; do case "$arg" in enable_proxy=*) ENABLE_PROXY="${arg#*=}" ;; force_rebuild=*) FORCE_REBUILD="${arg#*=}" ;; -h|--help) usage; exit 0 ;; *) echo "ERROR: unknown arg $arg"; usage; exit 2 ;; esac; done`。
- 取值规范：`1`/`true`/`yes` 视为开启，其它（含 `0`/空）视为关闭。脚本里用一个小函数 `is_truthy()` 统一判定，避免 if 链散落。
- 参数未传 → 默认值 `ENABLE_PROXY=0` / `FORCE_REBUILD=0`。
- 为避免与 `set -u` 冲突，所有变量使用前先初始化默认值。

### 决策 2：代理设置范围

- 选择：当 `is_truthy "${ENABLE_PROXY}"` 时执行：
  ```bash
  export HTTP_PROXY="http://127.0.0.1:7897"
  export HTTPS_PROXY="http://127.0.0.1:7897"
  export http_proxy="http://127.0.0.1:7897"
  export https_proxy="http://127.0.0.1:7897"
  export ALL_PROXY="http://127.0.0.1:7897"
  export NO_PROXY="${NO_PROXY:-localhost,127.0.0.1,::1}"
  export no_proxy="${no_proxy:-${NO_PROXY}}"
  ```
- 大小写两套同时设置：`curl` 既看 `https_proxy` 也看 `HTTPS_PROXY`，`docker buildx`/`docker pull` 通过 `HTTPS_PROXY`，但若用户的 docker daemon 已有独立 proxy 配置则以 daemon 为准。脚本不会修改 `~/.docker/config.json`，只在当前 shell 范围里 export。
- 备选：仅设置大写——`curl` 文档建议小写，省略可能导致取清单失败。
- 备选：不设置 `NO_PROXY`——若用户本机 docker daemon 通过 unix socket，`HTTP(S)_PROXY` 不影响 docker CLI 与 daemon 的通信，但其它本地服务（如 buildx 的 frontend）走 TCP 时会受影响；保守地把 loopback 列入 `NO_PROXY`。
- 不写 `~/.docker/config.json`：避免污染用户长期配置；提示用户：构建期对 docker daemon 拉基础镜像 (`alpine/helm:3` / `ubuntu:24.04`) 的代理需要 daemon 自身配置，本脚本无法替代——会在 README 与脚本输出中提醒一行。

### 决策 3：缓存命中判断

- 计算与 `images-package.sh` 一致的 `ARCH_TAG`：
  ```bash
  if [ -z "${ARCH:-}" ]; then
      case "$(uname -m)" in
          x86_64|amd64) ARCH="linux/amd64" ;;
          aarch64|arm64) ARCH="linux/arm64" ;;
          armv7l) ARCH="linux/arm/v7" ;;
          *) ARCH="linux/$(uname -m)" ;;
      esac
  fi
  ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
  ```
  把这段放在 `standalone_build.sh` 顶部、参数解析之后；让用户也可以通过 `ARCH=linux/amd64 ./standalone_build.sh` 覆盖。
- 缓存判断：
  ```bash
  CACHE_FILE="${SCRIPT_DIR}/k3s-images-${ARCH_TAG}.tar.zst"
  if [ -f "${CACHE_FILE}" ] && ! is_truthy "${FORCE_REBUILD}"; then
      echo "==> 检测到 ${CACHE_FILE}，跳过 ./standalone/images-package.sh（force_rebuild=1 可强制重建）"
  else
      ./standalone/images-package.sh
  fi
  ```
- `force_rebuild=1` 仅控制是否跳过 `images-package.sh`；`images-package.sh` 内部本来就有 `rm -f` 旧文件再生成的逻辑，无需重复处理。
- 备选：用文件 mtime 与 `k3s-version.env` 的 mtime 比较，旧则自动重建。**不采用**——隐式行为容易误导，让用户显式 `force_rebuild=1` 更直观。
- 备选：把缓存判断下沉到 `images-package.sh`。**不采用**——`images-package.sh` 的契约是"被调用即生成"，缓存属于上层编排关注点。

### 决策 4：用法 / 帮助

- 加一个 `usage()` 函数，输出三行：
  ```
  Usage: ./standalone_build.sh [enable_proxy=0|1] [force_rebuild=0|1]
    enable_proxy=1   set http://127.0.0.1:7897 as proxy for curl/docker (default 0)
    force_rebuild=1  re-run images-package.sh even if k3s-images-<arch>.tar.zst exists (default 0)
  ```
- 触发：`-h`/`--help` 显示并退出 0；遇到未知参数显示并退出 2。

### 决策 5：与 `docker buildx build` 的代理传递

经过实测发现：BuildKit **不会**自动把宿主机 `HTTP_PROXY` 透传到 build 容器内的 RUN 步骤。第一次实测 `enable_proxy=1` 后 `apt-get update` 仍直连 `ports.ubuntu.com` 并报 502，证明仅 export 环境变量并不够。需要做两件事：

1. **`standalone_build.sh` 显式注入 `--build-arg`**：当 `enable_proxy=1` 时给 `docker buildx build` 追加：
   ```
   --build-arg HTTP_PROXY=http://host.docker.internal:7897
   --build-arg HTTPS_PROXY=http://host.docker.internal:7897
   --build-arg http_proxy=http://host.docker.internal:7897
   --build-arg https_proxy=http://host.docker.internal:7897
   --build-arg NO_PROXY=localhost,127.0.0.1
   --build-arg no_proxy=localhost,127.0.0.1
   ```
   - 用 `host.docker.internal:7897` 而非 `127.0.0.1:7897`：build 容器内 `127.0.0.1` 指向容器自身，`host.docker.internal` 在 macOS Docker Desktop 里被解析为宿主机网关；Linux 主机自 Docker 24+ 也可通过 `--add-host=host.docker.internal:host-gateway` 启用，本仓库主流为 macOS，先满足这条。
2. **`Dockerfile` 在 `base` 与 `stage-1` 都声明 `ARG HTTP_PROXY` 等**：build-arg 仅当 Dockerfile 显式 `ARG` 后才能进入 RUN 步骤的 shell 环境；apk/wget/curl 自动 honor `http_proxy`（小写）。
- 不修改 `~/.docker/config.json` 或 Docker Desktop 配置，避免污染用户环境；脚本仅在 enable_proxy=1 时输出一行温和的提示。
- daemon 自身拉镜像（如 `alpine/helm:3`、`ubuntu:24.04`）仍受 daemon proxy 配置约束——本仓库已被本机 docker 缓存命中，不再造成阻塞。

### 决策 6：apt 走国内 mirror 直连，不走代理

实测发现两层网络问题：apt 默认 mirror `ports.ubuntu.com` 出现 502 抖动；当代理被注入后 apt 也会自动走代理，但用户希望 apt 直连国内 mirror 而非穿透代理（绕一道），二者诉求一致——把 `apt` 这一步与"代理"解耦。

- 选择：`stage-1` 在执行 `apt-get update` 之前：
  ```dockerfile
  ARG APT_MIRROR=http://mirrors.aliyun.com/ubuntu-ports
  RUN sed -i "s|http://ports.ubuntu.com/ubuntu-ports|${APT_MIRROR}|g" /etc/apt/sources.list.d/ubuntu.sources && \
      sed -i "s|^Components:.*|Components: main|" /etc/apt/sources.list.d/ubuntu.sources && \
      unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy && \
      apt-get update && apt-get -y install --no-install-recommends ca-certificates && \
      rm -rf /var/lib/apt/lists/*
  ```
- `Components: main`：实测发现 `noble/universe` 索引（~2MB）在弱网下反复 `Connection failed`，但 ca-certificates 实际只在 `main` 仓库；关掉 universe/multiverse/restricted 后 update 时间从 ~9 分钟 / 21MB 降到 ~3 分钟 / 数 MB，并稳定通过。
- `unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy`：关掉本步的代理穿透，apt 直连阿里云 mirror，既符合用户意图也减少不必要的代理跳数。
- `--no-install-recommends`：避免 ca-certificates 拉一堆推荐包。
- `APT_MIRROR` 默认值用 ARG 提供，未来若需切 tuna/ustc 可在 `--build-arg APT_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports` 覆盖。
- 备选：仍走默认 `ports.ubuntu.com` + 让代理穿透。**不采用**——上游不稳定，且与用户明确意图相悖。
- 备选：完全替换 sources.list 文件。**不采用**——deb822 格式的 `ubuntu.sources` 用 sed 二处替换最小侵入；不动 keyring/Signed-By。

## Risks / Trade-offs

- **Risk**：`enable_proxy=1` 设了代理但宿主机本地代理未启动，`curl`/`docker pull` 反而更慢甚至失败。
  → Mitigation：脚本不预先探活代理（避免增加 60s 等待）；在开启代理时打印一行 `==> proxy enabled: http://127.0.0.1:7897 (确保本地代理已监听 7897)`，让用户自检。
- **Risk**：缓存命中后用户实际想要新版本，却忘了 `force_rebuild=1`，构建出来的镜像与 `k3s-version.env` 不匹配（旧 tar.zst + 新 k3s 二进制 → 重新引入 ImagePullBackOff）。
  → Mitigation：在缓存命中时打印警告：`如已升级 standalone/k3s-version.env，建议加 force_rebuild=1 强制刷新离线包`。可作为后续 CHANGE 的优化项（自动比较版本）。
- **Risk**：未来 `images-package.sh` 变更命名规则，本脚本的 `ARCH_TAG` 推断与之失同步。
  → Mitigation：把"`<arch-tag>` 推断规则与 `images-package.sh` 一致"写进 spec，并在两份脚本顶部加同样的注释提示对方位置；后续可考虑抽到共享 `standalone/_common.sh`，但当前两处重复成本低，避免过度抽象。
- **Trade-off**：`enable_proxy` 不修改 docker daemon 配置，所以 `docker buildx` 拉基础镜像 (`alpine/helm:3` / `ubuntu:24.04`) 仍受 daemon 配置约束。本变更选择"诚实告知"而非"伪装解决"，符合脚本职责边界。

## Migration Plan

1. 重写 `standalone_build.sh`，按本设计实现参数解析、代理导出、缓存命中、`docker buildx build`。
2. 更新 README 的 standalone 章节，给出 `enable_proxy=1` / `force_rebuild=1` 用法示例。
3. 验证：
   - `./standalone_build.sh -h` 显示用法。
   - 不传参 + 仓库根有 `k3s-images-arm64.tar.zst` → 跳过 `images-package.sh`，直接 `docker buildx build`。
   - 不传参 + 删除 `k3s-images-arm64.tar.zst` → 触发 `images-package.sh`。
   - `force_rebuild=1` + 已有 tar.zst → 重新执行 `images-package.sh`。
   - `enable_proxy=1` → 输出代理提示行；`echo "${HTTPS_PROXY}"` 在子 shell 中可见 `http://127.0.0.1:7897`（通过 `bash -x` 或临时 `echo` 验证）。
4. 回滚：`git revert` 即可恢复无参版本。

## Open Questions

- 是否需要把代理地址也参数化（如 `proxy=http://x:7890`）？暂不引入；`http://127.0.0.1:7897` 是用户明确指定的默认值，未来真有需要再加 `proxy=URL`。
- 是否需要在缓存命中时校验 `k3s-images-<arch>.tar.zst` 与 `k3s-version.env` 的版本一致性（解压抽 tag 比对）？需要解压成本不低；当前以 `force_rebuild=1` 显式控制为准，留作下一变更可选优化。
