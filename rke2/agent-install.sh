#!/bin/sh
#
# 在 RKE2 agent 节点（worker）上一键加入集群：
#   1. 校验 root / systemd / RKE2_VERSION / RKE2_URL / RKE2_TOKEN
#   2. 解压 rke2 二进制到 /usr/local
#   3. 离线镜像落到 /var/lib/rancher/rke2/agent/images/
#   4. 写 /etc/rancher/rke2/{config.yaml,registries.yaml}（注入 server / token）
#   5. systemctl enable --now rke2-agent.service
#   6. 等待 is-active
#
# 用法：
#   sudo RKE2_URL=https://<server-ip>:9345 RKE2_TOKEN=<node-token> ./agent-install.sh

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
    cat <<EOF >&2
Usage:
  sudo RKE2_URL=https://<server-ip>:9345 RKE2_TOKEN=<node-token> ./agent-install.sh

Required env:
  RKE2_URL    server 节点 supervisor 地址，形如 https://10.0.0.1:9345
  RKE2_TOKEN  server 节点 /var/lib/rancher/rke2/server/node-token 的内容
EOF
}

########################################
# 1. 前置校验
########################################
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: 需以 root 用户运行（或用 sudo）" >&2
    usage
    exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
    echo "ERROR: 未检测到 systemctl，仅支持基于 systemd 的发行版" >&2
    exit 1
fi

if [ -z "${RKE2_URL:-}" ]; then
    echo "ERROR: RKE2_URL 环境变量未设置" >&2
    usage
    exit 1
fi
if [ -z "${RKE2_TOKEN:-}" ]; then
    echo "ERROR: RKE2_TOKEN 环境变量未设置" >&2
    usage
    exit 1
fi

ENV_FILE="${SCRIPT_DIR}/rke2-version.env"
[ -f "${ENV_FILE}" ] || { echo "ERROR: ${ENV_FILE} 不存在" >&2; exit 1; }
# shellcheck disable=SC1090
. "${ENV_FILE}"
[ -n "${RKE2_VERSION:-}" ] || { echo "ERROR: RKE2_VERSION 为空" >&2; exit 1; }

case "$(uname -m)" in
    x86_64|amd64)   ARCH_TAG="amd64" ;;
    aarch64|arm64)  ARCH_TAG="arm64" ;;
    *)              echo "ERROR: 不支持的架构 $(uname -m)" >&2; exit 1 ;;
esac

BIN_TARBALL="${SCRIPT_DIR}/rke2.linux-${ARCH_TAG}.tar.gz"
IMAGES_TARBALL="${SCRIPT_DIR}/rke2-images.linux-${ARCH_TAG}.tar.zst"
CONFIG_SRC="${SCRIPT_DIR}/config-agent.yaml"
REGISTRIES_SRC="${SCRIPT_DIR}/registries.yaml"

for f in "${BIN_TARBALL}" "${IMAGES_TARBALL}" "${CONFIG_SRC}" "${REGISTRIES_SRC}"; do
    [ -f "$f" ] || { echo "ERROR: 离线包缺少 $f" >&2; exit 1; }
done

########################################
# 2. 幂等检测
########################################
if systemctl is-active --quiet rke2-agent.service 2>/dev/null; then
    echo "==> rke2-agent.service is already active; skip (already joined)"
    echo "    如需重新加入，先运行: sudo systemctl stop rke2-agent && sudo /usr/local/bin/rke2-uninstall.sh"
    exit 0
fi

########################################
# 3. 解压 RKE2 二进制
########################################
echo "==> installing rke2 binary (${RKE2_VERSION}, ${ARCH_TAG}) to /usr/local"
tar -xzf "${BIN_TARBALL}" -C /usr/local

########################################
# 4. 离线镜像
########################################
mkdir -p /var/lib/rancher/rke2/agent/images
cp "${IMAGES_TARBALL}" "/var/lib/rancher/rke2/agent/images/$(basename "${IMAGES_TARBALL}")"

########################################
# 5. 写 config（注入 server / token）
########################################
mkdir -p /etc/rancher/rke2
CONFIG_DST="/etc/rancher/rke2/config.yaml"
cp "${CONFIG_SRC}" "${CONFIG_DST}"

# 删除可能存在的旧 server: / token: 行
sed -i.bak -e '/^server:/d' -e '/^token:/d' "${CONFIG_DST}"
rm -f "${CONFIG_DST}.bak"

# 追加新的 server / token
{
    echo ""
    echo "server: ${RKE2_URL}"
    echo "token: ${RKE2_TOKEN}"
} >> "${CONFIG_DST}"
chmod 0600 "${CONFIG_DST}"

cp "${REGISTRIES_SRC}" "/etc/rancher/rke2/registries.yaml"

########################################
# 6. 启动 rke2-agent
########################################
echo "==> systemctl enable --now rke2-agent.service"
systemctl enable --now rke2-agent.service

########################################
# 7. 等待 active
########################################
echo "==> waiting for rke2-agent.service to become active (up to 300s)"
i=0
while ! systemctl is-active --quiet rke2-agent.service; do
    i=$((i + 1))
    if [ "${i}" -ge 60 ]; then
        echo "ERROR: rke2-agent 等待 active 超时；请检查 'journalctl -u rke2-agent'" >&2
        exit 1
    fi
    sleep 5
done

cat <<EOF

==> rke2-agent is up. 在 server 节点上执行
      kubectl --kubeconfig /etc/rancher/rke2/rke2.yaml get nodes
    应能看到本节点状态为 Ready（首次加入可能需要 1-2 分钟拉镜像）。
EOF
