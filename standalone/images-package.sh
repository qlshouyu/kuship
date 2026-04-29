#!/bin/sh
#
# 根据 standalone/k3s-version.env 中声明的 k3s 版本，从 k3s 官方 release 拉取
# 该版本对应的 k3s-images.txt 镜像清单，按当前架构生成 k3s-images-<arch>.tar.zst。
# 镜像 tag 不在脚本中硬编码，确保离线包与 standalone/Dockerfile 中嵌入的 k3s
# 二进制版本始终一致。
#
# 用法：
#   ./standalone/images-package.sh                       # 自动识别架构
#   ARCH=linux/arm64 ./standalone/images-package.sh      # 强制指定架构
#   K3S_IMAGES_TXT_URL=https://internal/x.txt ...        # 覆盖清单 URL（内网镜像源）

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

########################################
# 1. 加载 K3S_VERSION（单一真相）
########################################
ENV_FILE="${SCRIPT_DIR}/k3s-version.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} 不存在；请先创建并写入 K3S_VERSION=<tag>" >&2
    exit 1
fi

# shellcheck disable=SC1090
. "${ENV_FILE}"

if [ -z "${K3S_VERSION:-}" ]; then
    echo "ERROR: K3S_VERSION not set in ${ENV_FILE}" >&2
    exit 1
fi

echo "==> resolving k3s version: ${K3S_VERSION}"

########################################
# 2. 检测架构
########################################
if [ -z "${ARCH:-}" ]; then
    machine="$(uname -m)"
    case "${machine}" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        armv7l)         ARCH="linux/arm/v7" ;;
        *)              ARCH="linux/${machine}" ;;
    esac
fi
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"
echo "==> target platform: ${ARCH} (archive suffix: ${ARCH_TAG})"

########################################
# 3. 必备工具检查
########################################
require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: 需要命令 '$1'，请先安装：$2" >&2
        exit 1
    fi
}
require_cmd curl "macOS 自带；Linux 用包管理器安装 curl"
require_cmd docker "请安装并启动 Docker / Docker Desktop"
if ! docker info >/dev/null 2>&1; then
    echo "ERROR: docker daemon 不可用，请确认 Docker Desktop 已启动" >&2
    exit 1
fi

install_zstd() {
    if command -v zstd >/dev/null 2>&1; then
        return 0
    fi
    OS="$(uname -s)"
    case "${OS}" in
        Darwin)
            if ! command -v brew >/dev/null 2>&1; then
                echo "ERROR: macOS 上未检测到 Homebrew，请先安装：https://brew.sh" >&2
                exit 1
            fi
            brew install zstd
            ;;
        Linux)
            if command -v apt-get >/dev/null 2>&1; then
                sudo apt-get update && sudo apt-get -y install zstd
            elif command -v yum >/dev/null 2>&1; then
                sudo yum install -y zstd
            elif command -v dnf >/dev/null 2>&1; then
                sudo dnf install -y zstd
            elif command -v apk >/dev/null 2>&1; then
                sudo apk add --no-cache zstd
            else
                echo "ERROR: 未识别的 Linux 包管理器，请手动安装 zstd" >&2
                exit 1
            fi
            ;;
        *)
            echo "ERROR: 不支持的操作系统：${OS}" >&2
            exit 1
            ;;
    esac
}
install_zstd

########################################
# 4. 拼接 k3s-images.txt URL（+ -> %2B），允许 K3S_IMAGES_TXT_URL 覆盖
########################################
ENCODED_VERSION="$(echo "${K3S_VERSION}" | sed -e 's/+/%2B/g')"
DEFAULT_IMAGES_TXT_URL="https://github.com/k3s-io/k3s/releases/download/${ENCODED_VERSION}/k3s-images.txt"
IMAGES_TXT_URL="${K3S_IMAGES_TXT_URL:-${DEFAULT_IMAGES_TXT_URL}}"

echo "==> fetching k3s-images.txt from: ${IMAGES_TXT_URL}"

TMP_LIST="$(mktemp -t k3s-images.XXXXXX)"
trap 'rm -f "${TMP_LIST}"' EXIT INT TERM

if ! curl --fail --location --retry 3 --retry-delay 5 --silent --show-error \
        -o "${TMP_LIST}" "${IMAGES_TXT_URL}"; then
    echo "ERROR: 无法获取 k3s-images.txt: ${IMAGES_TXT_URL}" >&2
    echo "       请检查网络是否可达 github.com，或通过 K3S_IMAGES_TXT_URL 指定可达的内网镜像源" >&2
    exit 1
fi

# 收集非空、非注释镜像
IMAGE_LIST="$(grep -vE '^[[:space:]]*(#|$)' "${TMP_LIST}" | awk 'NF>0{print $1}')"
if [ -z "${IMAGE_LIST}" ]; then
    echo "ERROR: 镜像清单为空，请检查 ${IMAGES_TXT_URL}" >&2
    exit 1
fi

IMAGE_COUNT="$(printf '%s\n' "${IMAGE_LIST}" | wc -l | tr -d ' ')"
echo "==> pulling ${IMAGE_COUNT} images for ${ARCH}"

########################################
# 5. 拉取镜像
########################################
for image in ${IMAGE_LIST}; do
    echo "    -> docker pull --platform=${ARCH} ${image}"
    if ! docker pull --platform="${ARCH}" "${image}"; then
        echo "ERROR: 拉取失败：${image}" >&2
        exit 1
    fi
done

########################################
# 6. 打包并压缩
########################################
OUTPUT="${REPO_ROOT}/k3s-images-${ARCH_TAG}.tar.zst"
echo "==> saving to ${OUTPUT}"
rm -f "${OUTPUT}"

# shellcheck disable=SC2086
docker save ${IMAGE_LIST} | zstd -T0 -19 -o "${OUTPUT}"

echo "==> done: $(ls -lh "${OUTPUT}" | awk '{print $5, $9}')"
