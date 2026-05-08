## Context

当前 standalone 镜像离线包仅含 k3s 系统镜像（`standalone-image-bundle` 能力定义），rainbond 业务镜像在 k3s 启动后由 helm-install-rainbond-cluster job 在线拉取，单镜像 4–6 分钟（已实测）。挂载式离线包是 k3s 既有约定：k3s 启动时会自动 import `/var/lib/rancher/k3s/agent/images/*.tar.zst`，本仓库通过 `K3S_DATA_DIR=/opt/rainbond/k3s` 改写为 `/opt/rainbond/k3s/agent/images/*`。

业务镜像 tag 来自 `reference/rainbond-chart`：12 个镜像，其中 7 个 tag 由 `Cluster.installVersion` 注入（rbd-api / rbd-app-ui / rbd-chaos / rbd-mq / rbd-worker / rainbond / rainbond-operator），5 个 tag 在 chart 模板里硬编码（local-path-provisioner / minio / apisix-ingress-controller / apisix / registry / rbd-monitor / rbd-db）。`alpine:3` 用于 hosts-job init container，需一并打包。

## Goals / Non-Goals

**Goals:**
- 首次启动 rainbond docker-compose 栈耗时从 ~15 分钟降至 ~2 分钟。
- 离线（断网）环境下 rainbond 业务镜像 SHALL 不再出现 ImagePullBackOff。
- 业务镜像 tag 与 `reference/rainbond-chart` 严格同步，单一真相源（`standalone/rainbond-images.env` + chart 解析）。
- 退化兼容：离线包缺失时 compose 启动 SHALL 仍可工作（在线拉取，与现状一致）。
- 与 k3s 离线包构建工具链复用：`enable_proxy` / `force_rebuild` / 缓存复用 / 架构识别等开关行为一致。

**Non-Goals:**
- 不打包用户应用镜像（用户在 rainbond 上构建 / 拉取的应用镜像）。
- 不修改 RKE2 多节点离线包流程（`rke2/images-package.sh` 已有自己的清单）。
- 不改 chart 内的镜像版本，仅做"按 chart 拉取并打包"的旁路工具。
- 不内置自动从 chart 解析 tag 的复杂依赖（helm template + yq），首版采用 `rainbond-images.env` 显式枚举 + 自动校对脚本（CI 友好）。

## Decisions

### 决策 1：业务镜像离线包独立产物，不打入 docker 镜像

**选择**：业务镜像打成独立 `rainbond-images-<arch>.tar.zst`，通过 `docker-compose.yaml` 的 `volumes:` 挂载，不通过 Dockerfile `COPY` 进 docker image。

**理由**：
- 业务镜像总体积 2–3 GB，远大于 k3s 系统包（~200 MB）；打入 docker image 会让 rainbond-dev 镜像膨胀到 3 GB+，docker pull / 推送成本高。
- 升级 rainbond 业务版本时，只需替换 tar 包，不需要重新构建 docker image。
- 与 rainbond 升级周期解耦：用户可保留 docker image，仅刷新业务镜像离线包。

**替代方案**：Dockerfile `COPY` 进镜像 — 简单但镜像过大，pass。

### 决策 2：通过 `/tmp/rainbond-images.tar.zst` staging，由 entrypoint 就位到 k3s images 目录

**选择**：docker-compose 把宿主机 `rainbond-images-<arch>.tar.zst` 挂载到容器内 `/tmp/rainbond-images.tar.zst`（只读），entrypoint 在 k3s 启动前检测并拷贝到 `/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst`，再由 k3s agent 启动时自动 import。

**理由**：
- 与现有 `/tmp/k3s-images.tar.zst → /opt/rainbond/k3s/agent/images/k3s-images.tar.zst` 的 staging 模式严格对称，entrypoint 一处幂等检测既覆盖现有 k3s 系统包又覆盖业务包。
- docker compose 短挂载语法在源文件缺失时会自动在容器内创建空目录占位；让 entrypoint 在 `/tmp` 下识别空目录 / 小文件 / 缺失三种异常状态并退化为 warning，避免 k3s images 目录被错误的"目录占位"污染导致 k3s import 失败。
- `/opt/rainbond/k3s/agent/images/` 是 k3s 内部数据目录（`/opt/rainbond` 由 named volume 持有），由 entrypoint 显式管理写入更安全。
- 仍由 k3s 自动 import，无需 entrypoint 显式调用 `k3s ctr images import`，避免与 containerd 启动时序耦合。

**替代方案**：直接挂载到 `/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst` — 简单，但宿主机文件缺失时会在 named volume 上留下目录占位，且会与 k3s 内部 images 目录的写入路径冲突，pass。

### 决策 3：Compose 挂载使用相对路径 + `.tar.zst` 文件挂载（非目录）

**选择**：
```yaml
volumes:
  - ../rainbond-images-${ARCH:-arm64}.tar.zst:/opt/rainbond/k3s/agent/images/rainbond-images.tar.zst:ro
```

**理由**：
- 单文件挂载语义清晰；目录挂载会覆盖 k3s 容器内可能预留的其他离线包。
- `${ARCH:-arm64}` 让开发者通过 shell 环境变量切换；缺省 arm64 与 macOS Apple Silicon 主流开发机一致。
- `:ro` 防止容器内意外覆写。

**退化处理**：当 `rainbond-images-<arch>.tar.zst` 不存在时，docker compose 不会因缺文件失败 — Docker 会创建一个空目录代替缺失的源；为避免此 footgun，entrypoint SHALL 在 import 前检测目标文件大小（< 1 MB 视为占位），不存在或太小则跳过、打印一行 warning 并继续走在线拉取。

### 决策 4：业务镜像清单的单一真相源采用 `rainbond-images.env`

**选择**：在 `standalone/rainbond-images.env` 中以 `RAINBOND_VERSION=<tag>` + 完整 image 列表（含固定 tag）声明清单；脚本读 env 文件即得镜像列表，不解析 helm template。

**理由**：
- 当前 chart 的 tag 一部分由 `installVersion` 注入、一部分硬编码，要"完美自动解析"需 `helm template` + `yq` 提取 image 字段，依赖工具链复杂；首版人工列出更可控。
- env 文件 SHALL 包含与 chart 同步的 CI 校验脚本（diff 实际 chart 渲染结果与 env 列表），漂移时 CI 报错；防止 chart 升级遗忘更新 env。
- 与 `k3s-version.env` 同构，开发者熟悉。

**替代方案**：动态 `helm template ./reference/rainbond-chart --set Cluster.installVersion=...` + `yq '..|.image? | select(.)' | sort -u` — 后续可演进为该方案，首版降复杂度。

### 决策 5：`standalone_build.sh` 默认不构建业务镜像离线包

**选择**：默认 `build_business_images=0`；显式 `build_business_images=1` 时才调用 `business-images-package.sh`；`force_rebuild=1` 时即使 `build_business_images=0` 也不强制构建（两个开关正交）。

**理由**：
- 业务镜像离线包大、拉取耗时 5+ 分钟，不应是默认路径；CI 默认构建 standalone docker image 时不需要。
- 开发者本地首次启动 compose 栈才需要：执行一次 `./standalone_build.sh build_business_images=1`，后续 `force_rebuild=0` 缓存复用。
- 与 `enable_proxy`/`force_rebuild` 开关组合可拼出"开代理 + 强制重建业务镜像"的常见场景。

### 决策 6：架构后缀与 k3s 离线包一致

**选择**：复用 `images-package.sh` 现有的 `<arch-tag>` 计算（`linux/arm64` → `arm64`），输出 `rainbond-images-arm64.tar.zst` / `rainbond-images-amd64.tar.zst`。

**理由**：保持 `.gitignore` / 文档 / 用户认知一致。

## Risks / Trade-offs

- **离线包体积膨胀（2–3 GB）阻塞 git 误提交** → `.gitignore` 加 `rainbond-images-*.tar.zst` 显式拒收；`README.md` 明确声明该文件不入库。
- **chart 升级 tag 漂移导致离线包失效** → `business-images-package.sh` 在打包前执行 `helm template` 并将渲染结果中所有 `image:` 与 `rainbond-images.env` 的列表 diff，不一致时立即报错并打印两边差集；CI 集成时同样校验。
- **首次 compose up 时 `rainbond-images-<arch>.tar.zst` 不存在** → entrypoint 检测文件大小 < 1 MB 时跳过 import 并打印 warning，退化为在线拉取（与现状一致）；不报错阻塞启动。
- **并发 import 与 k3s 镜像加载冲突** → 不存在：k3s 在 agent 启动时一次性扫描 `agent/images/`，扫描完才进入 kubelet 启动，时序由 k3s 内部保证。
- **macOS Docker Desktop 大文件挂载性能** → 仅启动期一次性 IO，约 30–60 秒导入时间；可接受，远好于在线拉取的 4–6 分钟单镜像。
- **不同 rainbond 版本切换** → `RAINBOND_VERSION` 改动后 `standalone_build.sh build_business_images=1 force_rebuild=1` 重建；离线包名按 `<arch>` 不带 version 后缀，旧版本会被覆盖（避免目录里堆积无用包）；如需保留多版本，开发者自己改名。

## Migration Plan

无破坏性变更；新增能力，老用户不变。

- **新用户**：按 `docker/README.md` 新增章节执行 `./standalone_build.sh build_business_images=1` 一次，然后 `docker compose up -d`。
- **老用户（已有 docker image）**：执行 `./standalone/business-images-package.sh` 即可，无需重建 docker image。
- **回滚**：删除 `rainbond-images-<arch>.tar.zst` 文件即可退回在线拉取行为；`docker-compose.yaml` 的可选挂载块在文件缺失时不阻断启动。

## Open Questions

- 是否需要在 `business-images-package.sh` 中支持镜像清单的 `--platform=linux/all` 多架构打包？首版采用单架构（与 k3s 离线包一致），跨架构需要分别构建。
- 是否需要把 metrics-server（k3s 系统包内已有 v0.8.1）相关业务依赖也写入业务包？暂不，k3s 系统包已包含；`alpine:3`（hosts-job 用）SHALL 列入业务包列表。
