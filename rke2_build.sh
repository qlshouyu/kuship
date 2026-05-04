#!/bin/bash
#
# kuship rke2 多节点离线包构建编排入口。
#
# 用法：
#   ./rke2_build.sh                        # 默认行为：照常拉/复用离线包
#   ./rke2_build.sh enable_proxy=1         # 为 curl/docker 等本进程命令导出 http://127.0.0.1:7897 代理
#   ./rke2_build.sh force_rebuild=1        # 即使 rke2-bundle-<arch>.tar.zst 已存在也强制重新生成
#   ./rke2_build.sh -h | --help            # 打印用法
#
# 注意：enable_proxy 仅影响本脚本进程内的命令；docker daemon 本身代理需在 Docker Desktop 中另行配置。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

ENABLE_PROXY=0
FORCE_REBUILD=0
PROXY_URL="http://127.0.0.1:7897"

usage() {
    cat <<EOF
Usage: ./rke2_build.sh [enable_proxy=0|1] [force_rebuild=0|1]

  enable_proxy=1   set ${PROXY_URL} as proxy for curl/docker in this process (default 0)
  force_rebuild=1  re-run rke2/images-package.sh even if rke2-bundle-<arch>.tar.zst exists (default 0)
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
# 2. 加载 RKE2_VERSION（单一真相）
########################################
set -a
. ./rke2/rke2-version.env
set +a

if [ -z "${RKE2_VERSION:-}" ]; then
    echo "ERROR: RKE2_VERSION not set; check rke2/rke2-version.env" >&2
    exit 1
fi

########################################
# 3. 推断架构（与 rke2/images-package.sh 规则保持一致）
########################################
if [ -z "${ARCH:-}" ]; then
    case "$(uname -m)" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        *)              echo "ERROR: 暂不支持架构 $(uname -m)（仅 amd64 / arm64）" >&2; exit 1 ;;
    esac
fi
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
CACHE_FILE="${SCRIPT_DIR}/rke2-bundle-${ARCH_TAG}.tar.zst"

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
    echo "==> proxy enabled: ${PROXY_URL}（确保本地代理已监听 7897）"
fi

########################################
# 5. 复用 / 重建离线包
########################################
if [ -f "${CACHE_FILE}" ] && ! is_truthy "${FORCE_REBUILD}"; then
    echo "==> 检测到 ${CACHE_FILE}，跳过 ./rke2/images-package.sh"
    echo "    （如已升级 rke2/rke2-version.env，请加 force_rebuild=1 强制刷新离线包）"
    exit 0
fi

echo "==> 生成离线包 ${CACHE_FILE} (RKE2_VERSION=${RKE2_VERSION}, ARCH=${ARCH}${ENABLE_PROXY:+, proxy=${PROXY_URL}})"
ARCH="${ARCH}" ./rke2/images-package.sh

echo "==> rke2 bundle ready: ${CACHE_FILE}"
