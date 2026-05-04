#!/bin/sh
#
# 在 RKE2 server 节点（控制面 + etcd）上一键安装：
#   1. 校验 root / systemd / RKE2_VERSION
#   2. 解压 rke2 二进制到 /usr/local
#   3. 离线镜像落到 /var/lib/rancher/rke2/agent/images/
#   4. 写 /etc/rancher/rke2/{config.yaml,registries.yaml}
#   5. 渲染 rainbond-cluster manifests + 拷贝 chart 到 server/static
#   6. systemctl enable --now rke2-server
#   7. 等待 node-token 出现，写入 node-token-bundle.txt 并打印
#
# 用法：
#   sudo ./server-install.sh                  # 干净节点首装
#   sudo ./server-install.sh --force          # 已安装节点强制重装并 restart
#
# 离线包解压后的目录是脚本工作目录，所有相对路径基于 SCRIPT_DIR。

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FORCE=0

case "${1:-}" in
    --force) FORCE=1 ;;
    "")      ;;
    *)       echo "ERROR: unknown argument: $1（仅支持 --force）" >&2; exit 2 ;;
esac

########################################
# 1. 前置校验
########################################
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: 需以 root 用户运行（或用 sudo）" >&2
    exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
    echo "ERROR: 未检测到 systemctl，仅支持基于 systemd 的发行版（Ubuntu 22.04+ / CentOS 8+ / Rocky 9 等）" >&2
    exit 1
fi

ENV_FILE="${SCRIPT_DIR}/rke2-version.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} 不存在" >&2
    exit 1
fi
# shellcheck disable=SC1090
. "${ENV_FILE}"
if [ -z "${RKE2_VERSION:-}" ]; then
    echo "ERROR: RKE2_VERSION 为空" >&2
    exit 1
fi

case "$(uname -m)" in
    x86_64|amd64)   ARCH_TAG="amd64" ;;
    aarch64|arm64)  ARCH_TAG="arm64" ;;
    *)              echo "ERROR: 不支持的架构 $(uname -m)" >&2; exit 1 ;;
esac

BIN_TARBALL="${SCRIPT_DIR}/rke2.linux-${ARCH_TAG}.tar.gz"
IMAGES_TARBALL="${SCRIPT_DIR}/rke2-images.linux-${ARCH_TAG}.tar.zst"
CHART_TGZ="${SCRIPT_DIR}/rainbond-cluster.tgz"
CONFIG_SRC="${SCRIPT_DIR}/config-server.yaml"
REGISTRIES_SRC="${SCRIPT_DIR}/registries.yaml"

for f in "${BIN_TARBALL}" "${IMAGES_TARBALL}" "${CHART_TGZ}" "${CONFIG_SRC}" "${REGISTRIES_SRC}"; do
    [ -f "$f" ] || { echo "ERROR: 离线包缺少 $f" >&2; exit 1; }
done

########################################
# 2. 幂等检测
########################################
CONFIG_DST="/etc/rancher/rke2/config.yaml"
ALREADY_INSTALLED=0
if [ -f "${CONFIG_DST}" ] && systemctl is-active --quiet rke2-server.service; then
    ALREADY_INSTALLED=1
fi

if [ "${ALREADY_INSTALLED}" -eq 1 ] && [ "${FORCE}" -ne 1 ]; then
    echo "==> rke2-server.service is already active and ${CONFIG_DST} exists; skip (already installed)"
    echo "    若需重装，请使用 sudo ./server-install.sh --force"
    exit 0
fi

########################################
# 3. 解压 RKE2 二进制
########################################
echo "==> installing rke2 binary (${RKE2_VERSION}, ${ARCH_TAG}) to /usr/local"
tar -xzf "${BIN_TARBALL}" -C /usr/local

########################################
# 4. 离线镜像落到 agent/images
########################################
mkdir -p /var/lib/rancher/rke2/agent/images
cp "${IMAGES_TARBALL}" "/var/lib/rancher/rke2/agent/images/$(basename "${IMAGES_TARBALL}")"

########################################
# 5. 写 config / registries
########################################
mkdir -p /etc/rancher/rke2
cp "${CONFIG_SRC}"       "${CONFIG_DST}"
cp "${REGISTRIES_SRC}"   "/etc/rancher/rke2/registries.yaml"

########################################
# 6. 引导 rainbond-cluster manifests
########################################
mkdir -p /var/lib/rancher/rke2/server/manifests /var/lib/rancher/rke2/server/static
cp "${CHART_TGZ}" "/var/lib/rancher/rke2/server/static/rainbond-cluster.tgz"
"${SCRIPT_DIR}/bootstrap-rainbond.sh"

########################################
# 7. 启动 / 重启 rke2-server.service
########################################
if [ "${ALREADY_INSTALLED}" -eq 1 ] && [ "${FORCE}" -eq 1 ]; then
    echo "==> --force: restarting rke2-server.service"
    systemctl daemon-reload
    systemctl restart rke2-server.service
else
    echo "==> systemctl enable --now rke2-server.service"
    systemctl enable --now rke2-server.service
fi

########################################
# 8. 等待 node-token 出现
########################################
TOKEN_FILE="/var/lib/rancher/rke2/server/node-token"
echo "==> waiting for ${TOKEN_FILE} (up to 300s)"
i=0
while [ ! -f "${TOKEN_FILE}" ]; do
    i=$((i + 1))
    if [ "${i}" -ge 60 ]; then
        echo "ERROR: 等待 ${TOKEN_FILE} 超时（300s）；请检查 'journalctl -u rke2-server'" >&2
        exit 1
    fi
    sleep 5
done

########################################
# 9. 输出 RKE2_URL / RKE2_TOKEN
########################################
TOKEN_VALUE="$(cat "${TOKEN_FILE}")"
SERVER_IP="$(hostname -I 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ && $i !~ /^127\./) { print $i; exit }}')"
if [ -z "${SERVER_IP}" ]; then
    SERVER_IP="$(hostname -i 2>/dev/null | awk '{print $1}')"
fi
RKE2_URL="https://${SERVER_IP}:9345"

BUNDLE_FILE="/var/lib/rancher/rke2/server/node-token-bundle.txt"
{
    echo "RKE2_URL=${RKE2_URL}"
    echo "RKE2_TOKEN=${TOKEN_VALUE}"
} > "${BUNDLE_FILE}"
chmod 0600 "${BUNDLE_FILE}"

cat <<EOF

==> rke2-server is up. Use the following on each agent node:

RKE2_URL=${RKE2_URL}
RKE2_TOKEN=${TOKEN_VALUE}

（已写入 ${BUNDLE_FILE}，仅 root 可读）

下一步：把离线包拷贝到 agent 节点，执行
  sudo RKE2_URL='${RKE2_URL}' RKE2_TOKEN='${TOKEN_VALUE}' ./agent-install.sh
EOF
