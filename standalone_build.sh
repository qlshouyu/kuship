#!/bin/bash
#
# kuship standalone 构建编排入口。
#
# 用法：
#   ./standalone_build.sh                          # 默认行为：照常拉/复用离线包并构建镜像
#   ./standalone_build.sh enable_proxy=1           # 为 curl/docker 等本进程命令导出 http://127.0.0.1:7897 代理
#   ./standalone_build.sh force_rebuild=1          # 即使 k3s-images-<arch>.tar.zst 已存在也强制重新生成
#   ./standalone_build.sh build_business_images=1  # 同时生成 rainbond-images-<arch>.tar.zst（业务镜像离线包）
#   ./standalone_build.sh push=1                    # 构建并推送至 registry.cn-hangzhou.aliyuncs.com/egojit/rainbond-dev:v6.9.0-release
#                                                   # （需先 docker login registry.cn-hangzhou.aliyuncs.com）
#   ./standalone_build.sh platforms=linux/amd64,linux/arm64 push=1  # 构建多架构镜像并推送（多架构必须 push）
#   ./standalone_build.sh -h | --help              # 打印用法
#
# 注意：enable_proxy 仅影响本脚本进程内的命令；docker daemon 拉取基础镜像
# (alpine/helm:3、ubuntu:24.04 等) 是否走代理由 Docker Desktop / dockerd 自身配置决定。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

ENABLE_PROXY=0
FORCE_REBUILD=0
BUILD_BUSINESS_IMAGES=0
PUSH=0
# 目标平台：逗号分隔，可多架构（如 linux/amd64,linux/arm64）。留空时按宿主机架构推断单平台。
PLATFORMS_ARG=""
# 镜像坐标（推送目标）：registry.cn-hangzhou.aliyuncs.com/egojit/rainbond-dev:v6.9.0-release
IMAGE_REGISTRY="registry.cn-hangzhou.aliyuncs.com"
IMAGE_NAMESPACE="egojit"
IMAGE_NAME="rainbond-dev"
IMAGE_TAG="v6.9.0-release"
# 本进程内（curl 取 k3s-images.txt、本机 docker pull）走 127.0.0.1
PROXY_URL="http://127.0.0.1:7897"
# buildx 容器内访问宿主机代理需用 host.docker.internal（macOS Docker Desktop 自动解析为宿主机 IP）
BUILD_PROXY_URL="http://host.docker.internal:7897"

usage() {
    cat <<EOF
Usage: ./standalone_build.sh [enable_proxy=0|1] [force_rebuild=0|1] [build_business_images=0|1]

  enable_proxy=1           set ${PROXY_URL} as proxy for curl/docker in this process (default 0)
  force_rebuild=1          re-run images-package.sh even if k3s-images-<arch>.tar.zst exists; also forces
                           regeneration of rainbond-images-<arch>.tar.zst when build_business_images=1
                           (default 0)
  build_business_images=1  invoke standalone/business-images-package.sh to generate
                           rainbond-images-<arch>.tar.zst (rainbond business image bundle); skipped when
                           rainbond-images-<arch>.tar.zst already exists unless force_rebuild=1 (default 0)
  push=1                   push the built image to ${IMAGE_REGISTRY}/${IMAGE_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}
                           (requires 'docker login ${IMAGE_REGISTRY}' beforehand); default 0 keeps the result in
                           the buildx cache only (default 0)
  platforms=<list>         comma-separated build platforms, e.g. linux/amd64,linux/arm64 (default: host arch).
                           multi-platform builds MUST set push=1 (buildx cannot --load a multi-arch image)
  -h, --help               show this help and exit
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
        enable_proxy=*)          ENABLE_PROXY="${arg#*=}" ;;
        force_rebuild=*)         FORCE_REBUILD="${arg#*=}" ;;
        build_business_images=*) BUILD_BUSINESS_IMAGES="${arg#*=}" ;;
        push=*)                  PUSH="${arg#*=}" ;;
        platforms=*)             PLATFORMS_ARG="${arg#*=}" ;;
        -h|--help)               usage; exit 0 ;;
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
# 3. 解析目标平台列表（与 standalone/images-package.sh 的 ARCH/ARCH_TAG 规则保持一致）
#    platforms= 覆盖；否则按 ARCH 环境变量；再否则按宿主机架构推断单平台
########################################
# linux/arm/v7 -> arm-v7；linux/amd64 -> amd64
arch_tag_of() { echo "$1" | sed -e 's|^linux/||' -e 's|/|-|g'; }
# archtag -> k3s release 资源名（amd64 的资源名无后缀）
k3s_asset_of() {
    case "$1" in
        amd64)  echo "k3s" ;;
        arm64)  echo "k3s-arm64" ;;
        arm-v7) echo "k3s-armhf" ;;
        *)      echo "k3s-$1" ;;
    esac
}

PLATFORMS=()
if [ -n "${PLATFORMS_ARG}" ]; then
    # 去掉空格后按逗号拆分
    IFS=',' read -r -a PLATFORMS <<< "$(echo "${PLATFORMS_ARG}" | tr -d '[:space:]')"
elif [ -n "${ARCH:-}" ]; then
    PLATFORMS=("${ARCH}")
else
    case "$(uname -m)" in
        x86_64|amd64)   PLATFORMS=("linux/amd64") ;;
        aarch64|arm64)  PLATFORMS=("linux/arm64") ;;
        armv7l)         PLATFORMS=("linux/arm/v7") ;;
        *)              PLATFORMS=("linux/$(uname -m)") ;;
    esac
fi

# 多架构镜像无法 --load 进本地 docker（buildkit 限制），必须推送到仓库
if [ "${#PLATFORMS[@]}" -gt 1 ] && ! is_truthy "${PUSH}"; then
    echo "ERROR: 多架构构建（${PLATFORMS[*]}）必须推送到仓库，请加 push=1" >&2
    echo "       原因：buildx 无法把多平台镜像 --load 进本地 docker。" >&2
    exit 1
fi
echo "==> 目标平台: ${PLATFORMS[*]}"

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
# 5. 复用 / 重建各平台离线包（每个平台一份 k3s-images-<arch>.tar.zst）
########################################
for platform in "${PLATFORMS[@]}"; do
    tag="$(arch_tag_of "${platform}")"
    cache_file="${SCRIPT_DIR}/k3s-images-${tag}.tar.zst"
    if [ -f "${cache_file}" ] && ! is_truthy "${FORCE_REBUILD}"; then
        echo "==> 检测到 ${cache_file}，跳过 ./standalone/images-package.sh (${platform})"
        echo "    （如已升级 standalone/k3s-version.env，请加 force_rebuild=1 强制刷新离线包）"
    else
        echo "==> 生成离线包 ${cache_file} (${platform})"
        ARCH="${platform}" ./standalone/images-package.sh
    fi
done

########################################
# 5b. 复用 / 重建 rainbond 业务镜像离线包（可选，每个平台一份）
########################################
if is_truthy "${BUILD_BUSINESS_IMAGES}"; then
    for platform in "${PLATFORMS[@]}"; do
        tag="$(arch_tag_of "${platform}")"
        business_cache_file="${SCRIPT_DIR}/rainbond-images-${tag}.tar.zst"
        if [ -f "${business_cache_file}" ] && ! is_truthy "${FORCE_REBUILD}"; then
            echo "==> 检测到 ${business_cache_file}，跳过 ./standalone/business-images-package.sh (${platform})"
            echo "    （如已升级 standalone/rainbond-images.env，请加 force_rebuild=1 强制刷新离线包）"
        else
            echo "==> 生成 rainbond 业务镜像离线包 ${business_cache_file} (${platform})"
            ARCH="${platform}" ./standalone/business-images-package.sh
        fi
    done
fi

########################################
# 5c. 宿主机预下载各平台 k3s 二进制（curl 续传+重试）
#     代理对 GitHub release CDN 的大文件下载会随机断流，构建容器内的 wget 无续传能力
#     一断即失败；改为在宿主机用 curl --retry --continue-at 拉完整后由 Dockerfile COPY。
########################################
ENCODED_K3S_VERSION="$(echo "${K3S_VERSION}" | sed -e 's/+/%2B/g')"
CURL_PROXY_ARGS=()
if is_truthy "${ENABLE_PROXY}"; then
    CURL_PROXY_ARGS=(-x "${PROXY_URL}")
fi
for platform in "${PLATFORMS[@]}"; do
    tag="$(arch_tag_of "${platform}")"
    asset="$(k3s_asset_of "${tag}")"
    bin_file="${SCRIPT_DIR}/k3s-${tag}"
    url="https://github.com/k3s-io/k3s/releases/download/${ENCODED_K3S_VERSION}/${asset}"
    if [ -s "${bin_file}" ] && ! is_truthy "${FORCE_REBUILD}"; then
        echo "==> 检测到 ${bin_file}，跳过 k3s 二进制下载 (${platform})"
        echo "    （如已升级 standalone/k3s-version.env，请加 force_rebuild=1 强制重下）"
    else
        echo "==> 下载 k3s 二进制 ${url} -> ${bin_file}"
        is_truthy "${FORCE_REBUILD}" && rm -f "${bin_file}"
        # --retry-all-errors + --continue-at - 让 SSL 中断后从断点续传，直到拉完整个文件
        curl -fL ${CURL_PROXY_ARGS[@]+"${CURL_PROXY_ARGS[@]}"} \
            --retry 20 --retry-delay 2 --retry-all-errors \
            --continue-at - -o "${bin_file}" "${url}"
        chmod +x "${bin_file}"
        echo "==> done: $(ls -lh "${bin_file}" | awk '{print $5, $9}')"
    fi
done

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

FULL_IMAGE="${IMAGE_REGISTRY}/${IMAGE_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

# push=1 -> 直接推送到远端仓库；否则沿用默认（仅留在 buildx 构建缓存）
OUTPUT_ARGS=()
if is_truthy "${PUSH}"; then
    OUTPUT_ARGS=(--push)
    echo "==> 将推送镜像至 ${FULL_IMAGE}（请确保已 docker login ${IMAGE_REGISTRY}）"
fi

PLATFORM_CSV="$(IFS=,; echo "${PLATFORMS[*]}")"

echo "==> docker buildx build (K3S_VERSION=${K3S_VERSION}, PLATFORMS=${PLATFORM_CSV}${ENABLE_PROXY:+, proxy=${BUILD_PROXY_URL}})"
docker buildx build \
    -f standalone/Dockerfile \
    --platform "${PLATFORM_CSV}" \
    --build-arg "K3S_VERSION=${K3S_VERSION}" \
    ${BUILD_PROXY_ARGS[@]+"${BUILD_PROXY_ARGS[@]}"} \
    -t "${FULL_IMAGE}" \
    ${OUTPUT_ARGS[@]+"${OUTPUT_ARGS[@]}"} \
    .
