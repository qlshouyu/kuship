# docker-compose启动rainbond
```bash
docker-compose up -d
```
## 查看pod状态
```bash
docker exec kuship-rainbond k3s kubectl get pods -A -w
```

## 准备业务镜像离线包（可选，加速首次启动）
首次 `docker compose up -d` 时 rainbond 业务镜像（rbd-api / rbd-app-ui / rbd-monitor / apisix / minio / registry / rainbond-operator 等）需在线从 `registry.cn-hangzhou.aliyuncs.com/goodrain/*` 拉取，单镜像耗时 4–6 分钟，整栈就绪需 10–15 分钟以上。生成业务镜像离线包后，`docker-compose.yaml` 会把它挂载到容器内，由 k3s 启动时自动 import，首次启动可缩短至 ~2 分钟。

两种方式任选：

```bash
# 方式 1：与 standalone 镜像构建一并完成
./standalone_build.sh build_business_images=1
# 也可叠加代理：./standalone_build.sh build_business_images=1 enable_proxy=1
# 强制重建：./standalone_build.sh build_business_images=1 force_rebuild=1

# 方式 2：单独执行
./standalone/business-images-package.sh
```

产出文件位于仓库根：`rainbond-images-<arch>.tar.zst`（约 2–3 GB，已被 `.gitignore` 排除，不入库）。

> **架构切换**：`docker/docker-compose.yaml` 通过 `${ARCH:-arm64}` 决定挂载源后缀，未导出 `ARCH` 时缺省为 `arm64`（与 macOS Apple Silicon 主流开发机一致）。amd64 机器需在 compose 命令前导出 `ARCH=amd64`，例如：
>
> ```bash
> ARCH=amd64 docker compose -f docker/docker-compose.yaml up -d
> ```
>
> 业务镜像离线包同样按当前架构生成，`ARCH=linux/amd64 ./standalone/business-images-package.sh` 可强制按目标架构拉取。

> **离线包缺失时如何**：`docker-compose.yaml` 的挂载是可选的——文件不存在时 docker compose 不会阻塞，rainbond 容器会通过在线拉取启动；entrypoint 内会打印 `rainbond-images.tar.zst missing or empty, falling back to online pull` 一行提示。

> **chart 升级同步**：当 `reference/rainbond-chart` 引入新镜像或修改 tag 时，需同步更新 `standalone/rainbond-images.env` 的 `RAINBOND_IMAGES` 列表；`business-images-package.sh` 在打包前会执行 `helm template` 校对，漂移时立即报错并打印两边差集。