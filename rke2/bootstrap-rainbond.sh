#!/bin/sh
#
# 在 RKE2 server 节点上幂等渲染 rainbond-cluster HelmChart manifest 到
# /var/lib/rancher/rke2/server/manifests/rainbond-cluster.yaml；
# 与 standalone/entrypoint.sh::rainbond_cluster_yaml() 共享相同环境变量集合：
#
#   EIP                 网关入口 IP；空值时回退到 hostname -I 第一个非回环 IPv4
#   UUID                rainbond eid；建议提前生成并固定
#   VERSION             rainbond installVersion，默认 v6.7.1-release
#   DB_REGION_ENABLE    region 库是否使用内置 mysql，默认 true
#   DB_HOST/PORT/USER/PASSWORD  region 库连接，默认 127.0.0.1:3306 root/123456
#   DB_UI_ENABLE        UI（console）库是否使用内置 mysql，默认 true
#   UI_DB_HOST/PORT/USER/PASSWORD  UI 库连接，默认 127.0.0.1:3306 root/123456

set -eu

MANIFEST_DIR="/var/lib/rancher/rke2/server/manifests"
mkdir -p "${MANIFEST_DIR}"

GET_EIP="$(hostname -I 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ && $i !~ /^127\./) { print $i; exit }}')"
if [ -z "${GET_EIP}" ]; then
    GET_EIP="$(hostname -i 2>/dev/null | awk '{print $1}')"
fi

cat > "${MANIFEST_DIR}/rainbond-cluster.yaml" <<EOF
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
      - name: $(hostname -s)
      nodesForGateway:
      - name: $(hostname -s)
        internalIP: ${EIP:-$GET_EIP}
        externalIP: ${EIP:-$GET_EIP}
      installVersion: ${VERSION:-v6.7.1-release}
      eid: ${UUID:-}
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

echo "==> rendered ${MANIFEST_DIR}/rainbond-cluster.yaml"
