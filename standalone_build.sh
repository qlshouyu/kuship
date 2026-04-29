#!/bin/bash
#
# kuship standalone 构建编排入口。
#
# 用法：
#   ./standalone_build.sh                        # 默认行为：照常拉/复用离线包并构建镜像
#   ./standalone_build.sh enable_proxy=1         # 为 curl/docker 等本进程命令导出 http://127.0.0.1:7897 代理
#   ./standalone_build.sh force_rebuild=1        # 即使 k3s-images-<arch>.tar.zst 已存在也强制重新生成
#   ./standalone_build.sh -h | --help            # 打印用法
#
# 注意：enable_proxy 仅影响本脚本进程内的命令；docker daemon 拉取基础镜像
# (alpine/helm:3、ubuntu:24.04 等) 是否走代理由 Docker Desktop / dockerd 自身配置决定。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

ENABLE_PROXY=0
FORCE_REBUILD=0
# 本进程内（curl 取 k3s-images.txt、本机 docker pull）走 127.0.0.1
PROXY_URL="http://127.0.0.1:7897"
# buildx 容器内访问宿主机代理需用 host.docker.internal（macOS Docker Desktop 自动解析为宿主机 IP）
BUILD_PROXY_URL="http://host.docker.internal:7897"

usage() {
    cat <<EOF
Usage: ./standalone_build.sh [enable_proxy=0|1] [force_rebuild=0|1]

  enable_proxy=1   set ${PROXY_URL} as proxy for curl/docker in this process (default 0)
  force_rebuild=1  re-run images-package.sh even if k3s-images-<arch>.tar.zst exists (default 0)
  -h, --help       show this help and exit
EOF
}

is_truthy() {
    case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
        1|true|yes) return 0 ;;
        *)          return 1 ;;
    esac
}

########################################
# 1. 解析参数
########################################
for arg in "$@"; do
    case "$arg" in
        enable_proxy=*)  ENABLE_PROXY="${arg#*=}" ;;
        force_rebuild=*) FORCE_REBUILD="${arg#*=}" ;;
        -h|--help)       usage; exit 0 ;;
        *)
            echo "ERROR: unknown argument: $arg" >&2
            usage >&2
            exit 2
            ;;
    esac
done

########################################
# 2. 加载 K3S_VERSION（单一真相）
########################################
set -a
. ./standalone/k3s-version.env
set +a

if [ -z "${K3S_VERSION:-}" ]; then
    echo "ERROR: K3S_VERSION not set; check standalone/k3s-version.env" >&2
    exit 1
fi

########################################
# 3. 推断架构（与 standalone/images-package.sh 规则保持一致）
########################################
if [ -z "${ARCH:-}" ]; then
    case "$(uname -m)" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        armv7l)         ARCH="linux/arm/v7" ;;
        *)              ARCH="linux/$(uname -m)" ;;
    esac
fi
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
CACHE_FILE="${SCRIPT_DIR}/k3s-images-${ARCH_TAG}.tar.zst"

########################################
# 4. 代理开关
########################################
if is_truthy "${ENABLE_PROXY}"; then
    export HTTP_PROXY="${PROXY_URL}"
    export HTTPS_PROXY="${PROXY_URL}"
    export http_proxy="${PROXY_URL}"
    export https_proxy="${PROXY_URL}"
    export ALL_PROXY="${PROXY_URL}"
    export NO_PROXY="${NO_PROXY:-localhost,127.0.0.1,::1}"
    export no_proxy="${no_proxy:-${NO_PROXY}}"
    echo "==> proxy enabled: ${PROXY_URL}（确保本地代理已监听 7897；docker daemon 的代理需在 Docker Desktop 中另行配置）"
fi

########################################
# 5. 复用 / 重建离线包
########################################
if [ -f "${CACHE_FILE}" ] && ! is_truthy "${FORCE_REBUILD}"; then
    echo "==> 检测到 ${CACHE_FILE}，跳过 ./standalone/images-package.sh"
    echo "    （如已升级 standalone/k3s-version.env，请加 force_rebuild=1 强制刷新离线包）"
else
    echo "==> 生成离线包 ${CACHE_FILE}"
    ./standalone/images-package.sh
fi

########################################
# 6. 构建镜像
########################################
BUILD_PROXY_ARGS=()
if is_truthy "${ENABLE_PROXY}"; then
    BUILD_PROXY_ARGS=(
        --build-arg "HTTP_PROXY=${BUILD_PROXY_URL}"
        --build-arg "HTTPS_PROXY=${BUILD_PROXY_URL}"
        --build-arg "http_proxy=${BUILD_PROXY_URL}"
        --build-arg "https_proxy=${BUILD_PROXY_URL}"
        --build-arg "NO_PROXY=localhost,127.0.0.1"
        --build-arg "no_proxy=localhost,127.0.0.1"
    )
fi

echo "==> docker buildx build (K3S_VERSION=${K3S_VERSION}, ARCH=${ARCH}${ENABLE_PROXY:+, proxy=${BUILD_PROXY_URL}})"
docker buildx build \
    -f standalone/Dockerfile \
    --build-arg "K3S_VERSION=${K3S_VERSION}" \
    "${BUILD_PROXY_ARGS[@]}" \
    -t rainbond-dev:v6.7.1-release \
    .
