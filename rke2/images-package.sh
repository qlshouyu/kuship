#!/bin/sh
#
# 根据 rke2/rke2-version.env 中声明的 RKE2 版本，从 RKE2 官方 release 拉取
# 二进制 tarball + 镜像清单 + sha256 校验和；并通过 docker run alpine/helm 把
# reference/rainbond-chart 打成 rainbond-cluster.tgz；最终把所有制品 + 安装脚本
# 收集到 rke2-<arch>/ 并 zstd 压缩为 rke2-bundle-<arch>.tar.zst。
#
# 用法：
#   ./rke2/images-package.sh                        # 自动识别架构
#   ARCH=linux/arm64 ./rke2/images-package.sh       # 强制指定架构
#   RKE2_RELEASE_URL=https://mirror/rke2 ...        # 覆盖 release 基础 URL（默认指向 github）

set -eu
# 启用 pipefail：tar | zstd 中任一段失败都要传播
# shellcheck disable=SC3040
(set -o pipefail 2>/dev/null) && set -o pipefail || true

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

########################################
# 1. 加载 RKE2_VERSION（单一真相）
########################################
ENV_FILE="${SCRIPT_DIR}/rke2-version.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} 不存在；请先创建并写入 RKE2_VERSION=<tag>" >&2
    exit 1
fi

# shellcheck disable=SC1090
. "${ENV_FILE}"

if [ -z "${RKE2_VERSION:-}" ]; then
    echo "ERROR: RKE2_VERSION not set in ${ENV_FILE}" >&2
    exit 1
fi

echo "==> resolving RKE2 version: ${RKE2_VERSION}"

########################################
# 2. 检测架构（与 standalone/images-package.sh 规则一致）
########################################
if [ -z "${ARCH:-}" ]; then
    machine="$(uname -m)"
    case "${machine}" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        *)              echo "ERROR: 暂不支持架构 ${machine}（仅 amd64 / arm64）" >&2; exit 1 ;;
    esac
fi
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
echo "==> target platform: ${ARCH} (archive suffix: ${ARCH_TAG})"

########################################
# 3. 必备工具
########################################
require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: 需要命令 '$1'，请先安装：$2" >&2
        exit 1
    fi
}
require_cmd curl "macOS 自带；Linux 用包管理器安装 curl"
require_cmd docker "请安装并启动 Docker / Docker Desktop（用于 helm package）"
if ! docker info >/dev/null 2>&1; then
    echo "ERROR: docker daemon 不可用，请确认 Docker Desktop 已启动" >&2
    exit 1
fi

# 选择 sha256 校验工具：Linux/Alpine 用 sha256sum；macOS 默认用 shasum -a 256
SHA256_CMD=""
if command -v sha256sum >/dev/null 2>&1; then
    SHA256_CMD="sha256sum"
elif command -v shasum >/dev/null 2>&1; then
    SHA256_CMD="shasum -a 256"
else
    echo "ERROR: 需要 sha256sum 或 shasum 工具" >&2
    exit 1
fi

install_zstd() {
    if command -v zstd >/dev/null 2>&1; then return 0; fi
    OS="$(uname -s)"
    case "${OS}" in
        Darwin)
            if ! command -v brew >/dev/null 2>&1; then
                echo "ERROR: macOS 上未检测到 Homebrew，请先安装：https://brew.sh" >&2
                exit 1
            fi
            brew install zstd ;;
        Linux)
            if   command -v apt-get >/dev/null 2>&1; then sudo apt-get update && sudo apt-get -y install zstd
            elif command -v yum     >/dev/null 2>&1; then sudo yum install -y zstd
            elif command -v dnf     >/dev/null 2>&1; then sudo dnf install -y zstd
            elif command -v apk     >/dev/null 2>&1; then sudo apk add --no-cache zstd
            else echo "ERROR: 未识别的 Linux 包管理器，请手动安装 zstd" >&2; exit 1; fi ;;
        *)  echo "ERROR: 不支持的操作系统：${OS}" >&2; exit 1 ;;
    esac
}
install_zstd

########################################
# 4. 拼接 RKE2 release URL（+ -> %2B），允许 RKE2_RELEASE_URL 覆盖
########################################
ENCODED_VERSION="$(echo "${RKE2_VERSION}" | sed -e 's/+/%2B/g')"
DEFAULT_RELEASE_URL="https://github.com/rancher/rke2/releases/download/${ENCODED_VERSION}"
RELEASE_URL="${RKE2_RELEASE_URL:-${DEFAULT_RELEASE_URL}}"

echo "==> downloading RKE2 release artifacts from: ${RELEASE_URL}"

WORK_DIR="${REPO_ROOT}/rke2-${ARCH_TAG}"
rm -rf "${WORK_DIR}"
mkdir -p "${WORK_DIR}"

BIN_TARBALL="rke2.linux-${ARCH_TAG}.tar.gz"
SHA256_FILE="sha256sum-${ARCH_TAG}.txt"
IMAGES_TARBALL="rke2-images.linux-${ARCH_TAG}.tar.zst"

curl_with_retry() {
    src="$1"; dst="$2"
    # --continue-at - 续传；--retry-all-errors 在 partial 之类瞬时错误也重试
    # 大文件（rke2-images.linux-<arch>.tar.zst ~700MB）下走代理易遇 SSL/keepalive 抖动，给充裕的 retry-max-time
    if ! curl --fail --location \
            --retry 6 --retry-delay 10 --retry-max-time 1800 \
            --retry-all-errors \
            --continue-at - \
            --connect-timeout 30 \
            --progress-bar \
            -o "${dst}" "${src}"; then
        echo "ERROR: 下载失败：${src}" >&2
        echo "       请检查网络是否可达，或通过 RKE2_RELEASE_URL 指定可达的内网 mirror" >&2
        exit 1
    fi
}

curl_with_retry "${RELEASE_URL}/${BIN_TARBALL}"    "${WORK_DIR}/${BIN_TARBALL}"
curl_with_retry "${RELEASE_URL}/${SHA256_FILE}"    "${WORK_DIR}/${SHA256_FILE}"
curl_with_retry "${RELEASE_URL}/${IMAGES_TARBALL}" "${WORK_DIR}/${IMAGES_TARBALL}"

########################################
# 5. 校验 sha256（仅校验本次下载的两个 tar）
########################################
echo "==> verifying sha256 for ${BIN_TARBALL} / ${IMAGES_TARBALL}"
(
    cd "${WORK_DIR}"
    # RKE2 sha256sum-<arch>.txt 包含全量条目；只校验我们下载的两份文件即可
    grep -E "(${BIN_TARBALL}|${IMAGES_TARBALL})\$" "${SHA256_FILE}" > /tmp/rke2-sha256-subset.txt
    if [ ! -s /tmp/rke2-sha256-subset.txt ]; then
        echo "ERROR: ${SHA256_FILE} 中未找到 ${BIN_TARBALL} 或 ${IMAGES_TARBALL} 的校验条目" >&2
        exit 1
    fi
    if ! ${SHA256_CMD} -c /tmp/rke2-sha256-subset.txt; then
        echo "ERROR: sha256 校验失败" >&2
        rm -f /tmp/rke2-sha256-subset.txt
        exit 1
    fi
    rm -f /tmp/rke2-sha256-subset.txt
)

########################################
# 6. 用 alpine/helm 打 rainbond-cluster.tgz
########################################
CHART_SRC="${REPO_ROOT}/reference/rainbond-chart"
if [ ! -d "${CHART_SRC}" ]; then
    echo "ERROR: ${CHART_SRC} 不存在；请确认 git submodule 已初始化" >&2
    exit 1
fi

echo "==> packaging rainbond-cluster.tgz via alpine/helm:3"
docker run --rm \
    -v "${CHART_SRC}:/src/rainbond-chart:ro" \
    -v "${WORK_DIR}:/out" \
    -w /out \
    alpine/helm:3 package /src/rainbond-chart >/dev/null

# helm package 会输出 rainbond-<chart-version>.tgz，统一改名
mv "${WORK_DIR}"/rainbond-*.tgz "${WORK_DIR}/rainbond-cluster.tgz"

########################################
# 7. 收集脚本与配置（chmod +x 全部 *.sh）
########################################
echo "==> collecting install scripts and configs"
for f in server-install.sh agent-install.sh bootstrap-rainbond.sh \
         config-server.yaml config-agent.yaml registries.yaml \
         rke2-version.env README.md; do
    if [ ! -f "${SCRIPT_DIR}/${f}" ]; then
        echo "ERROR: 缺少 ${SCRIPT_DIR}/${f}" >&2
        exit 1
    fi
    cp "${SCRIPT_DIR}/${f}" "${WORK_DIR}/${f}"
done
chmod 0755 "${WORK_DIR}"/*.sh

########################################
# 8. 打包压缩
########################################
OUTPUT="${REPO_ROOT}/rke2-bundle-${ARCH_TAG}.tar.zst"
echo "==> compressing to ${OUTPUT}"
rm -f "${OUTPUT}"

(
    # 使用 tar | zstd 管道而非 GNU tar 的 -I，使 macOS bsdtar 与 GNU tar 都兼容
    cd "${REPO_ROOT}"
    tar -cf - "rke2-${ARCH_TAG}" | zstd -T0 -19 -o "${OUTPUT}"
)

# 清理工作目录（保留产物即可）
rm -rf "${WORK_DIR}"

echo "==> done: $(ls -lh "${OUTPUT}" | awk '{print $5, $9}')"
