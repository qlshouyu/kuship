#!/bin/sh

########################################
# cgroup v2 委派预处理（k3s-in-docker 必需，尤以 macOS Docker Desktop 显著）
#
# 为什么需要：cgroup v2 规则下，向 cgroup.subtree_control 启用 controller 时
#   要求该 cgroup 内不直接挂进程。LinuxKit/Docker Desktop 拉起容器时 PID 1
#   默认就在根 cgroup，k3s 自身的 cgroup 初始化代码会撞 EBUSY race，导致
#   kubelet "kubepods missing controllers: cpu, cpuset, hugetlb, memory, pids"
#   致命退出，表现为容器反复重启 N 次直到 race 偶然命中才稳定。
# 做了什么：启动前把根 cgroup 内所有进程迁移到 /init 子 cgroup，再把所有可用
#   controller 一次性写入根 cgroup 的 subtree_control。幂等：cgroup v1 主机
#   或已委派环境直接跳过。
########################################
setup_cgroup_v2_delegation() {
  [ -f /sys/fs/cgroup/cgroup.controllers ] || return 0

  if grep -qw cpu /sys/fs/cgroup/cgroup.subtree_control 2>/dev/null; then
    return 0
  fi

  mkdir -p /sys/fs/cgroup/init 2>/dev/null || true

  pids="$(cat /sys/fs/cgroup/cgroup.procs 2>/dev/null)"
  for pid in $pids; do
    echo "$pid" > /sys/fs/cgroup/init/cgroup.procs 2>/dev/null || true
  done

  ctrls=""
  for c in $(cat /sys/fs/cgroup/cgroup.controllers 2>/dev/null); do
    ctrls="$ctrls +$c"
  done
  if [ -n "$ctrls" ]; then
    if echo "$ctrls" > /sys/fs/cgroup/cgroup.subtree_control 2>/dev/null; then
      echo "[cgroup-setup] delegated controllers:$ctrls" >&2
    else
      echo "[cgroup-setup] WARN: failed to write subtree_control ($ctrls); k3s will retry" >&2
    fi
  fi
}

setup_cgroup_v2_delegation

########################################
# Initialize configuration
########################################
init_configuration() {
  
  if ! mkdir -p /opt/rainbond/k3s/server/manifests /opt/rainbond/k3s/server/static /opt/rainbond/k3s/agent/images; then
    echo "ERROR: Failed to create directory"
    exit 1
  fi
  
  if ! cp /tmp/rainbond-cluster.tgz /opt/rainbond/k3s/server/static/rainbond-cluster.tgz; then
    echo "ERROR: Failed to copy rainbond-cluster.tgz"
    exit 1
  fi

  if [ ! -f "/opt/rainbond/k3s/agent/images/k3s-images.tar.zst" ]; then
    cp /tmp/k3s-images.tar.zst /opt/rainbond/k3s/agent/images/k3s-images.tar.zst
  fi

  rainbond_cluster_yaml
}

# 把 docker-compose 挂载的 /tmp/rainbond-images.tar.zst 就位到 k3s agent/images 目录，
# 由 k3s 启动时自动 import；幂等。挂载缺失或源是 docker 自动创建的空目录占位时退化为 warning。
stage_business_images_bundle() {
  src="/tmp/rainbond-images.tar.zst"
  dst="/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst"

  if [ ! -e "${src}" ] || [ -d "${src}" ]; then
    echo "[rainbond-images] rainbond-images.tar.zst missing or empty, falling back to online pull" >&2
    return 0
  fi

  size=$(stat -c %s "${src}" 2>/dev/null || stat -f %z "${src}" 2>/dev/null || echo 0)
  if [ "${size:-0}" -lt 1048576 ]; then
    echo "[rainbond-images] rainbond-images.tar.zst missing or empty, falling back to online pull" >&2
    return 0
  fi

  if [ -f "${dst}" ]; then
    dst_size=$(stat -c %s "${dst}" 2>/dev/null || stat -f %z "${dst}" 2>/dev/null || echo 0)
    if [ "${dst_size}" = "${size}" ]; then
      return 0
    fi
  fi

  cp "${src}" "${dst}"
  echo "[rainbond-images] staged ${size} bytes to ${dst}" >&2
}

rainbond_cluster_yaml() {

  GET_EIP=$(hostname -i | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/) print $i}')

cat > /opt/rainbond/k3s/server/manifests/rainbond-cluster.yaml << EOF
apiVersion: v1
kind: Namespace
metadata:
  name: rbd-system
---
apiVersion: helm.cattle.io/v1
kind: HelmChart
metadata:
  name: rainbond-cluster
  namespace: rbd-system
spec:
  chart: https://%{KUBERNETES_API}%/static/rainbond-cluster.tgz
  targetNamespace: rbd-system
  valuesContent: |-
    Cluster:
      gatewayIngressIPs: ${EIP:-$GET_EIP}
      nodesForChaos:
      - name: node
      nodesForGateway:
      - name: node
        internalIP: $GET_EIP
        externalIP: $GET_EIP
      installVersion: ${VERSION:-v6.7.1-dev}
      eid: ${UUID}
      regionDatabase:
        enable: ${DB_REGION_ENABLE:-true}
        host: ${DB_HOST:-127.0.0.1}
        port: ${DB_PORT:-3306}
        username: ${DB_USER:-root}
        password: ${DB_PASSWORD:-123456}
        name: region
      uiDatabase:
        enable: ${DB_UI_ENABLE:-true}
        host: ${UI_DB_HOST:-127.0.0.1}
        port: ${UI_DB_PORT:-3306}
        username: ${UI_DB_USER:-root}
        password: ${UI_DB_PASSWORD:-123456}
        name: console
EOF
}

if [ ! -f "/opt/rainbond/k3s/server/static/rainbond-cluster.tgz" ] || \
   [ ! -f "/opt/rainbond/k3s/server/manifests/rainbond-cluster.yaml" ] || \
   [ ! -f "/opt/rainbond/k3s/agent/images/k3s-images.tar.zst" ]; then
    init_configuration
else
  rainbond_cluster_yaml
fi

stage_business_images_bundle

if [ ! -f "/root/.bash_aliases" ]; then
  echo "alias kubectl='k3s kubectl'" >> /root/.bash_aliases
  echo "alias crictl='k3s crictl'" >> /root/.bash_aliases
fi

########################################
# Patch CoreDNS upstream to public DNS
#
# k3s 默认 Corefile `forward . /etc/resolv.conf` 把 DNS 上游交给 CoreDNS pod 自己的
# /etc/resolv.conf。在 k3s-in-docker（standalone 镜像）场景下，pod 网络看不到外层
# 容器的 docker 内嵌 DNS（127.0.0.11），导致 pod 内任何域名都无法解析（如外部应用市场
# api.goodrain.com）。把上游改为公共 DNS 可绕开这一断链。
#
# 后台异步执行：等 CoreDNS ConfigMap 出现后 patch 一次；幂等（已 patch 过则跳过）。
# 上游 DNS 可通过 env KUSHIP_COREDNS_UPSTREAM 覆写（空格分隔多个）。
########################################
patch_coredns_upstream() {
  KUBECONFIG=/etc/rancher/k3s/k3s.yaml
  export KUBECONFIG
  upstream="${KUSHIP_COREDNS_UPSTREAM:-223.5.5.5 8.8.8.8}"

  # 等 CoreDNS ConfigMap 出现（k3s 启动 + addon controller 应用 manifests 大约 1~3 分钟）
  i=0
  while [ "$i" -lt 60 ]; do
    if k3s kubectl get cm -n kube-system coredns >/dev/null 2>&1; then
      break
    fi
    i=$((i + 1))
    sleep 5
  done
  if [ "$i" -ge 60 ]; then
    echo "[coredns-dns-fix] timed out waiting for CoreDNS ConfigMap; skip" >&2
    return
  fi

  if k3s kubectl get cm -n kube-system coredns -o jsonpath='{.data.Corefile}' \
       | grep -q "forward \. /etc/resolv\.conf"; then
    echo "[coredns-dns-fix] patching CoreDNS forward upstream to: $upstream" >&2
    k3s kubectl get cm -n kube-system coredns -o yaml \
      | sed "s|forward \. /etc/resolv\.conf|forward . $upstream|" \
      | k3s kubectl apply -f - >/dev/null
    k3s kubectl rollout restart -n kube-system deployment/coredns >/dev/null
    k3s kubectl rollout status -n kube-system deployment/coredns --timeout=60s >/dev/null 2>&1
    echo "[coredns-dns-fix] done" >&2
  else
    echo "[coredns-dns-fix] CoreDNS already patched (or non-default upstream), skip" >&2
  fi
}

patch_coredns_upstream &

exec "$@"