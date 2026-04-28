#!/bin/sh

image_list="rancher/klipper-helm:v0.8.4-build20240523
rancher/mirrored-coredns-coredns:1.10.1
rancher/mirrored-metrics-server:v0.7.0
rancher/mirrored-pause:3.6
busybox:latest"

# 自动检测架构（若未通过环境变量指定 ARCH）
if [ -z "${ARCH}" ]; then
    machine="$(uname -m)"
    case "${machine}" in
        x86_64|amd64)   ARCH="linux/amd64" ;;
        aarch64|arm64)  ARCH="linux/arm64" ;;
        armv7l)         ARCH="linux/arm/v7" ;;
        *)              ARCH="linux/${machine}" ;;
    esac
fi

# 用于输出文件名的简短架构标识（去掉 linux/ 前缀，将 / 替换为 -）
ARCH_TAG="$(echo "${ARCH}" | sed -e 's|^linux/||' -e 's|/|-|g')"

# 检测操作系统并安装 zstd
OS="$(uname -s)"
install_zstd() {
    if command -v zstd >/dev/null 2>&1; then
        return 0
    fi
    case "${OS}" in
        Darwin)
            if ! command -v brew >/dev/null 2>&1; then
                echo "错误：macOS 上未检测到 Homebrew，请先安装：https://brew.sh" >&2
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
                echo "错误：未识别的 Linux 包管理器，请手动安装 zstd" >&2
                exit 1
            fi
            ;;
        *)
            echo "错误：不支持的操作系统：${OS}" >&2
            exit 1
            ;;
    esac
}

for image in ${image_list}; do
    docker pull --platform="${ARCH}" "${image}"
done

install_zstd

docker save ${image_list} | zstd -T0 -19 -o k3s-images-"${ARCH_TAG}".tar.zst
