FROM alpine/helm:3 AS base
ARG TARGETARCH
ARG K3S_VERSION
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG http_proxy
ARG https_proxy
ARG NO_PROXY
ARG no_proxy
WORKDIR /tmp

RUN apk add --no-cache wget && \
    if [ -z "${K3S_VERSION}" ]; then echo "ERROR: K3S_VERSION build-arg not set"; exit 1; fi && \
    encoded_version="$(echo "${K3S_VERSION}" | sed 's/+/%2B/g')" && \
    if [ "$TARGETARCH" = "amd64" ]; then \
      wget "https://github.com/k3s-io/k3s/releases/download/${encoded_version}/k3s" -O /tmp/k3s && chmod +x /tmp/k3s; \
    else \
      wget "https://github.com/k3s-io/k3s/releases/download/${encoded_version}/k3s-arm64" -O /tmp/k3s && chmod +x /tmp/k3s; \
    fi
COPY reference/rainbond-chart /tmp/rainbond-chart
RUN  helm package rainbond-chart && \
    mv rainbond-*.tgz rainbond-cluster.tgz

FROM ubuntu:24.04
ARG VERSION
ARG TARGETARCH
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG http_proxy
ARG https_proxy
ARG NO_PROXY
ARG no_proxy
ARG APT_MIRROR=http://mirrors.aliyun.com/ubuntu-ports

COPY standalone/entrypoint.sh /entrypoint.sh
COPY standalone/config.yaml /etc/rancher/k3s/config.yaml
COPY standalone/registries.yaml /etc/rancher/k3s/registries.yaml
COPY k3s-images-$TARGETARCH.tar.zst /tmp/k3s-images.tar.zst

COPY --from=base /tmp/rainbond-cluster.tgz /tmp/rainbond-cluster.tgz
COPY --from=base /tmp/k3s /bin/k3s

# apt 走国内 mirror 直连（不走代理），其它阶段的 wget 仍可通过 ARG HTTP_PROXY 走代理
# ca-certificates 只在 main 仓库，关掉 universe/multiverse/restricted 可显著降低
# apt-get update 在弱网下因大索引失败的概率
RUN sed -i "s|http://ports.ubuntu.com/ubuntu-ports|${APT_MIRROR}|g" /etc/apt/sources.list.d/ubuntu.sources && \
    sed -i "s|^Components:.*|Components: main|" /etc/apt/sources.list.d/ubuntu.sources && \
    unset HTTP_PROXY HTTPS_PROXY http_proxy https_proxy && \
    apt-get update && apt-get -y install --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

ENV VERSION=${VERSION}
ENV KUBECONFIG=/etc/rancher/k3s/k3s.yaml
ENV CRI_CONFIG_FILE="/opt/rainbond/k3s/agent/etc/crictl.yaml"

VOLUME /opt/rainbond

ENTRYPOINT [ "/entrypoint.sh" ]

CMD [ "/bin/k3s", "server"]