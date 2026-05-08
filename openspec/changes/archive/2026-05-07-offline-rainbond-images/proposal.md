## Why

`docker compose -f docker/docker-compose.yaml up -d` 首次启动 rainbond 容器时，rbd-api / rbd-app-ui / rbd-monitor / rainbond-operator / apisix / minio / registry 等业务镜像需从 `registry.cn-hangzhou.aliyuncs.com/goodrain/*` 在线拉取，单镜像耗时 4–6 分钟，整栈就绪需 10–15 分钟以上；在弱网或离线环境完全无法启动。现有 `k3s-images-<arch>.tar.zst` 离线包仅含 k3s 自带系统镜像（coredns / metrics-server / klipper-helm / pause），不覆盖 rainbond 业务镜像。

## What Changes

- 新增 `standalone/business-images-package.sh`：从 `reference/rainbond-chart` 解析镜像清单，按当前架构拉取并打包成 `rainbond-images-<arch>.tar.zst`（与 `k3s-images-<arch>.tar.zst` 平级、独立产物，不打入 docker 镜像）。
- 新增 `standalone/rainbond-images.env`：声明 `RAINBOND_VERSION` 与非 chart 占位的固定 tag（apisix、minio、registry 等），作为业务镜像清单的单一真相源。
- 修改 `docker/docker-compose.yaml`：通过 `volumes:` 将仓库根的 `rainbond-images-<arch>.tar.zst` 挂载到 rainbond 容器的 `/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst`，让 k3s 启动时 containerd 自动 import；当离线包不存在时 compose 启动 SHALL 仍可工作（退化为在线拉取，与现状一致）。
- 修改 `standalone/entrypoint.sh`：在 k3s 启动前检测挂载点 / `/tmp` 中的 `rainbond-images.tar.zst`，若存在 SHALL 软链或拷贝至 `/opt/rainbond/k3s/agent/images/`，由 k3s 自动加载；幂等。
- 修改 `standalone_build.sh`：新增 `build_business_images=<bool>` 开关，`force_rebuild=1` 时同样强制重建业务镜像离线包；与 k3s 离线包共享 `enable_proxy` 与缓存复用规则。
- 修改 `docker/README.md`：新增"准备业务镜像离线包"步骤说明。

## Capabilities

### New Capabilities

- `rainbond-business-image-bundle`: 定义 rainbond 业务镜像离线包的生成、挂载、版本钉死与运行期加载契约。

### Modified Capabilities

- `docker-compose-stack`: 在 rainbond 服务声明 `rainbond-images-<arch>.tar.zst` 的可选挂载，以让容器启动时直接 import 业务镜像离线包。
- `standalone-build-orchestration`: 在 `standalone_build.sh` 中新增对 `business-images-package.sh` 的调用与 `build_business_images` / 缓存复用开关。

## Impact

- 受影响代码：`standalone/`（新增脚本与 env 文件、修改 `entrypoint.sh`）、`docker/docker-compose.yaml`、`standalone_build.sh`、`docker/README.md`。
- 受影响系统：仅 standalone 单机镜像与 docker-compose 本地开发栈，不影响 RKE2 多节点离线部署、CI 流程、生产部署。
- 依赖：构建机需可访问 `registry.cn-hangzhou.aliyuncs.com` 与 docker daemon、`zstd`（已被现有 `k3s-images-package.sh` 要求）。
- 性能：首次启动 rainbond 栈耗时从 ~15 分钟降至 ~2 分钟（k3s 启动 + containerd import 的本地 IO 时间）；`rainbond-images-<arch>.tar.zst` 体积约 2–3 GB（不入库，`.gitignore` 已覆盖产物模式）。
- 兼容性：离线包缺失时退化为现状（在线拉取），不破坏既有用户工作流；不引入 BREAKING 变更。
