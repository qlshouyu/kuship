#!/bin/sh


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
      installVersion: ${VERSION:-v6.7.1-release}
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

if [ ! -f "/root/.bash_aliases" ]; then
  echo "alias kubectl='k3s kubectl'" >> /root/.bash_aliases
  echo "alias crictl='k3s crictl'" >> /root/.bash_aliases
fi

exec "$@"