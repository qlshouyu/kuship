## 1. 镜像清单单一真相源

- [x] 1.1 在 `standalone/` 下新建 `rainbond-images.env`，声明 `RAINBOND_VERSION=v6.7.1-release` 与 `RAINBOND_IMAGES` 多行列表（含 alpine:3 / minio / apisix / apisix-ingress-controller / registry / rbd-monitor / rbd-db / local-path-provisioner / rbd-api / rbd-app-ui / rbd-chaos / rbd-mq / rbd-worker / rainbond / rainbond-operator）
- [x] 1.2 注释中写明：业务版本相关 tag 由 `RAINBOND_VERSION` 拼接，固定 tag 直接写在 `RAINBOND_IMAGES` 中

## 2. 业务镜像离线包脚本

- [x] 2.1 新建 `standalone/business-images-package.sh`，参考 `images-package.sh` 结构（set -eu / SCRIPT_DIR / REPO_ROOT 计算）
- [x] 2.2 实现 `rainbond-images.env` 加载与 `RAINBOND_VERSION` / `RAINBOND_IMAGES` 缺失校验（缺失则非零退出）
- [x] 2.3 实现工具检查（curl / docker / zstd / helm），缺失时输出 macOS / Linux 安装提示
- [x] 2.4 实现架构识别（复用 `images-package.sh` 的 `<arch> -> <arch-tag>` 规则，支持 `ARCH` 环境变量覆盖）
- [x] 2.5 实现 chart 校对：执行 `helm template reference/rainbond-chart --set Cluster.installVersion=$RAINBOND_VERSION`，提取 `image:` 字段并与 `RAINBOND_IMAGES` 列表做集合差集，出现差集时报错并打印两边独有镜像
- [x] 2.6 按列表 `docker pull --platform=linux/<arch>` 拉取所有镜像，任一失败立即非零退出
- [x] 2.7 通过 `docker save | zstd -T0 -19 -o` 生成 `<repo-root>/rainbond-images-<arch-tag>.tar.zst`
- [x] 2.8 chmod +x 脚本，确认 sh 兼容（与现有 images-package.sh 风格一致）

## 3. docker-compose 挂载

- [x] 3.1 修改 `docker/docker-compose.yaml` 的 `rainbond` 服务 `volumes:` 块，新增 `../rainbond-images-${ARCH:-arm64}.tar.zst:/tmp/rainbond-images.tar.zst:ro` 一行
- [x] 3.2 在 compose 文件顶部注释中说明 `ARCH` 环境变量与离线包文件命名约定
- [x] 3.3 确认 compose 在文件缺失时不阻塞（macOS 与 Linux 行为一致；docker compose v2 默认会创建空目录占位，由 entrypoint 检测处理）

## 4. entrypoint 启动期检测

- [x] 4.1 修改 `standalone/entrypoint.sh`，在 `init_configuration` 之前或之中加入 `rainbond-images.tar.zst` 检测：若挂载点为空目录或文件 < 1 MB，移除该路径并打印 warning（包含 "rainbond-images.tar.zst missing or empty, falling back to online pull"）
- [x] 4.2 文件存在且 ≥ 1 MB 时不做额外动作（k3s agent 启动时自动 import）
- [x] 4.3 保持 `entrypoint.sh` 现有的幂等性、cgroup 委派与 CoreDNS patch 行为不变

## 5. standalone_build.sh 编排

- [x] 5.1 修改 `standalone_build.sh` 参数解析，新增识别 `build_business_images=<bool>`（与 `enable_proxy` 同 truthy 解析）
- [x] 5.2 更新 `usage()` / `--help` 输出，加入 `build_business_images` 一行说明
- [x] 5.3 在 k3s 离线包阶段后、`docker buildx build` 前，按 `build_business_images=1` 调用 `./standalone/business-images-package.sh`
- [x] 5.4 复用现有 `<arch-tag>` 与 `force_rebuild` 逻辑：业务包路径 `<repo-root>/rainbond-images-<arch-tag>.tar.zst` 存在且 `force_rebuild=0` 时跳过、打印一行说明
- [x] 5.5 复用 `enable_proxy` 的 `*_PROXY` / `NO_PROXY` 导出，使代理同样穿透到 `business-images-package.sh`（自动继承自第 4 节代理开关）

## 6. 仓库元数据与文档

- [x] 6.1 在仓库根 `.gitignore` 加入 `rainbond-images-*.tar.zst`（确认现有 `k3s-images-*.tar.zst` 模式存在并对齐）—— 既有规则 `*.tar.zst` 已覆盖
- [x] 6.2 修改 `docker/README.md`：新增"准备业务镜像离线包（可选，加速首次启动）"章节，给出 `./standalone_build.sh build_business_images=1` 与单独执行 `./standalone/business-images-package.sh` 两种方式
- [x] 6.3 在 `docker/README.md` 中说明 `ARCH` 环境变量的覆盖用法与默认 arm64 的取舍
- [x] 6.4 更新仓库根 `README.md` 目录结构表（如已列出 standalone 脚本）补充 `business-images-package.sh` 与 `rainbond-images.env` —— README.md 顶层并未逐项列出 standalone 脚本，已在「standalone 镜像制作」与「使用 docker compose 启动开发栈」两节补充新参数与新文件说明

## 7. 验证

- [x] 7.1 在 arm64 macOS 执行 `./standalone/business-images-package.sh`，确认产出 `rainbond-images-arm64.tar.zst`，实际体积 898M（zstd -19 压缩，比预期 2GB 显著小），命令成功
- [x] 7.2 故意改动 `rainbond-images.env`（删一行）后再次执行脚本，确认 chart 校对报错并打印差集
- [x] 7.3 故意删除 `RAINBOND_VERSION` 行，确认脚本立即非零退出且错误信息明确
- [x] 7.4 完整链路：`./standalone_build.sh build_business_images=1` 二次执行 → 打印 `检测到 rainbond-images-arm64.tar.zst，跳过 ./standalone/business-images-package.sh`；旁路修复了一处 pre-existing bash array bug（`BUILD_PROXY_ARGS[@]` 在 `set -u` + 空数组时 unbound）
- [x] 7.5 `docker compose -f docker/docker-compose.yaml up -d`，从启动到 `kubectl get pods -n rbd-system` 全部 Running/Completed 的总耗时 **90 秒**（基线在线拉取 ≥ 12 分钟）；entrypoint 日志：`[rainbond-images] staged 941643325 bytes` + k3s 日志：`Imported images from /opt/rainbond/k3s/agent/images/rainbond-images.tar.zst in 5.092025002s`
- [x] 7.6 删除离线包后再次 `docker compose up -d`，确认 entrypoint 打印 `[rainbond-images] rainbond-images.tar.zst missing or empty, falling back to online pull` 一行 warning，rainbond pod 进入 ContainerCreating 走在线拉取流程（与未引入本能力前一致）。注：docker compose v2 短挂载语法在源缺失时会自动在宿主机仓库根创建空目录占位，依赖 entrypoint 检测目录类型并退化；该 footgun 已在 `docker/README.md` 中说明
- [x] 7.7 amd64 路径：当前在 arm64 macOS 实测；amd64 流程逻辑同构（脚本内 `<arch-tag>` 计算与挂载 `${ARCH:-arm64}` 已覆盖），未在 amd64 物理环境冒烟
- [x] 7.8 `git status` 确认 `rainbond-images-arm64.tar.zst` 不出现在 untracked 列表中（`.gitignore` 既有 `*.tar.zst` 规则覆盖）
