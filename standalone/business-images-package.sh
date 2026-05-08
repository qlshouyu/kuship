#!/bin/sh
#
# 根据 standalone/rainbond-images.env 中声明的 RAINBOND_VERSION 与 RAINBOND_IMAGES，
# 拉取 rainbond 业务镜像并按当前架构生成 rainbond-images-<arch>.tar.zst。
# 该离线包通过 docker-compose 挂载到容器内 /opt/rainbond/k3s/agent/images/，
# 由 k3s 启动时自动 import，避免首次启动时在线拉取业务镜像导致的等待。
#
# 用法：
#   ./standalone/business-images-package.sh                  # 自动识别架构
#   ARCH=linux/arm64 ./standalone/business-images-package.sh # 强制指定架构

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

########################################
# 1. 加载 rainbond-images.env（单一真相）
########################################
ENV_FILE="${SCRIPT_DIR}/rainbond-images.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} 不存在；请先创建并写入 RAINBOND_VERSION 与 RAINBOND_IMAGES" >&2
    exit 1
fi

# env 文件里 RAINBOND_IMAGES 用 ${RAINBOND_VERSION} 做插值，set -u 下若用户删了
# RAINBOND_VERSION 行直接 source 会以 "unbound variable" 报错（信息不友好）。
# 临时关掉 -u 完成 source，再切回严格模式做语义校验，给出明确错误。
set +u
# shellcheck disable=SC1090
. "${ENV_FILE}"
set -u

if [ -z "${RAINBOND_VERSION:-}" ]; then
    echo "ERROR: RAINBOND_VERSION not set in ${ENV_FILE}" >&2
    exit 1
fi
if [ -z "${RAINBOND_IMAGES:-}" ]; then
    echo "ERROR: RAINBOND_IMAGES not set in ${ENV_FILE}" >&2
    exit 1
fi

# 收集非空、非注释镜像（RAINBOND_IMAGES = chart 一致集合，需 1:1 校对）
CHART_IMAGE_LIST="$(printf '%s\n' "${RAINBOND_IMAGES}" | sed -e 's/[[:space:]]\{1,\}/\n/g' | grep -vE '^[[:space:]]*(#|$)' | awk 'NF>0{print $1}')"
if [ -z "${CHART_IMAGE_LIST}" ]; then
    echo "ERROR: RAINBOND_IMAGES is empty after parsing ${ENV_FILE}" >&2
    exit 1
fi

# RAINBOND_EXTRA_IMAGES 可选：chart 不渲染、运行期 CRD/operator 使用的镜像
EXTRA_IMAGE_LIST=""
if [ -n "${RAINBOND_EXTRA_IMAGES:-}" ]; then
    EXTRA_IMAGE_LIST="$(printf '%s\n' "${RAINBOND_EXTRA_IMAGES}" | sed -e 's/[[:space:]]\{1,\}/\n/g' | grep -vE '^[[:space:]]*(#|$)' | awk 'NF>0{print $1}')"
fi

# 实际打包列表 = chart 集合 + 额外集合
if [ -n "${EXTRA_IMAGE_LIST}" ]; then
    IMAGE_LIST="$(printf '%s\n%s\n' "${CHART_IMAGE_LIST}" "${EXTRA_IMAGE_LIST}" | awk 'NF>0')"
else
    IMAGE_LIST="${CHART_IMAGE_LIST}"
fi

IMAGE_COUNT="$(printf '%s\n' "${IMAGE_LIST}" | wc -l | tr -d ' ')"
EXTRA_COUNT=0
if [ -n "${EXTRA_IMAGE_LIST}" ]; then
    EXTRA_COUNT="$(printf '%s\n' "${EXTRA_IMAGE_LIST}" | wc -l | tr -d ' ')"
fi
echo "==> RAINBOND_VERSION=${RAINBOND_VERSION}, ${IMAGE_COUNT} images declared (extras: ${EXTRA_COUNT})"

########################################
# 2. 工具检查
########################################
require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: 需要命令 '$1'，请先安装：$2" >&2
        exit 1
    fi
}
require_cmd curl   "macOS 自带；Linux 用包管理器安装 curl"
require_cmd docker "请安装并启动 Docker / Docker Desktop"
if ! docker info >/dev/null 2>&1; then
    echo "ERROR: docker daemon 不可用，请确认 Docker Desktop 已启动" >&2
    exit 1
fi

# helm 用于 chart 校对：宿主机有 helm 直接用；否则降级到 alpine/helm:3 容器，
# 与 standalone/Dockerfile 第一阶段使用的镜像保持一致，避免要求开发机额外安装。
HELM_CMD="helm"
if ! command -v helm >/dev/null 2>&1; then
    HELM_CMD="docker run --rm -v ${REPO_ROOT}:/repo -w /repo alpine/helm:3"
    echo "==> helm 未安装，将通过 alpine/helm:3 容器执行 chart 校对"
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
# 3. 架构识别（与 standalone/images-package.sh 规则一致）
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
# 4. 与 rainbond-chart 渲染产物做集合差集校验
########################################
CHART_DIR="${REPO_ROOT}/reference/rainbond-chart"
if [ ! -d "${CHART_DIR}" ]; then
    echo "ERROR: rainbond chart 目录不存在: ${CHART_DIR}" >&2
    echo "       请确认 git submodule 已初始化（git submodule update --init）" >&2
    exit 1
fi

echo "==> verifying image list against ${CHART_DIR}"

TMP_RENDER="$(mktemp -t rainbond-chart.XXXXXX)"
TMP_CHART_LIST="$(mktemp -t chart-images.XXXXXX)"
TMP_ENV_LIST="$(mktemp -t env-images.XXXXXX)"
trap 'rm -f "${TMP_RENDER}" "${TMP_CHART_LIST}" "${TMP_ENV_LIST}"' EXIT INT TERM

# 兼容宿主机 helm 与 docker run alpine/helm:3 两种调用方式：容器内路径需要相对 /repo
if [ "${HELM_CMD}" = "helm" ]; then
    chart_arg="${CHART_DIR}"
else
    chart_arg="reference/rainbond-chart"
fi

# shellcheck disable=SC2086
if ! ${HELM_CMD} template rainbond-cluster "${chart_arg}" \
        --set "Cluster.installVersion=${RAINBOND_VERSION}" \
        > "${TMP_RENDER}" 2>/dev/null; then
    echo "ERROR: helm template 渲染失败：${CHART_DIR}" >&2
    exit 1
fi

# chart 模板里有 `image: <a>@<b>` 形式（apisix-ingress-controller@apisix），按 @ 拆成两条
grep -E '^[[:space:]]*image:' "${TMP_RENDER}" \
    | sed -e 's/^[[:space:]]*image:[[:space:]]*//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/" \
    | tr '@' '\n' \
    | awk 'NF>0{print $1}' \
    | sort -u > "${TMP_CHART_LIST}"

printf '%s\n' "${CHART_IMAGE_LIST}" | sort -u > "${TMP_ENV_LIST}"

ONLY_IN_CHART="$(comm -23 "${TMP_CHART_LIST}" "${TMP_ENV_LIST}")"
ONLY_IN_ENV="$(comm -13 "${TMP_CHART_LIST}" "${TMP_ENV_LIST}")"

DRIFT=0
if [ -n "${ONLY_IN_CHART}" ]; then
    DRIFT=1
    echo "ERROR: chart 中存在但 RAINBOND_IMAGES 未声明的镜像：" >&2
    printf '  - %s\n' ${ONLY_IN_CHART} >&2
fi
if [ -n "${ONLY_IN_ENV}" ]; then
    DRIFT=1
    echo "ERROR: RAINBOND_IMAGES 中存在但 chart 未渲染出的镜像：" >&2
    printf '  - %s\n' ${ONLY_IN_ENV} >&2
fi
if [ "${DRIFT}" -ne 0 ]; then
    echo "       请同步更新 ${ENV_FILE} 与 ${CHART_DIR}" >&2
    exit 1
fi
echo "==> chart image list verified"

########################################
# 5. 拉取镜像
########################################
echo "==> pulling ${IMAGE_COUNT} images for ${ARCH}"
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
OUTPUT="${REPO_ROOT}/rainbond-images-${ARCH_TAG}.tar.zst"
echo "==> saving to ${OUTPUT}"
rm -f "${OUTPUT}"

# shellcheck disable=SC2086
docker save ${IMAGE_LIST} | zstd -T0 -19 -o "${OUTPUT}"

echo "==> done: $(ls -lh "${OUTPUT}" | awk '{print $5, $9}')"
