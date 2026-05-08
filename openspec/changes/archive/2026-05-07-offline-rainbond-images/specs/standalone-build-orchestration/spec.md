## ADDED Requirements

### Requirement: build_business_images 控制业务镜像离线包构建

`standalone_build.sh` SHALL 识别新参数 `build_business_images=<bool>`，`<bool>` 中 `1`/`true`/`yes`（不区分大小写）视为开启，其它视为关闭；缺省为 `0`。当 `build_business_images=1` 时 SHALL 在调用 `./standalone/images-package.sh` 之后、`docker buildx build` 之前调用 `./standalone/business-images-package.sh`；当 `build_business_images=0` 时 SHALL 不调用 `business-images-package.sh`，行为与本次变更前一致。`-h`/`--help` 输出的 usage SHALL 同时列出 `build_business_images` 的说明。

#### Scenario: 默认不构建业务镜像离线包

- **WHEN** 在仓库根直接执行 `./standalone_build.sh`
- **THEN** 脚本 SHALL 不调用 `./standalone/business-images-package.sh`，整体行为与本次变更前一致

#### Scenario: 显式开启业务镜像构建

- **WHEN** 执行 `./standalone_build.sh build_business_images=1`
- **THEN** 脚本 SHALL 在 `./standalone/images-package.sh` 完成或被跳过后调用 `./standalone/business-images-package.sh`，并在其成功后才执行 `docker buildx build`

#### Scenario: 未识别参数仍报错

- **WHEN** 执行 `./standalone_build.sh build_business=1`（参数名拼写错误）
- **THEN** 脚本 SHALL 打印包含 `unknown` 字样的错误信息与 usage，并以非零退出码终止

### Requirement: 复用已有业务镜像离线包

`standalone_build.sh` SHALL 在调用 `./standalone/business-images-package.sh` 前，按当前架构（自动识别或读取 `ARCH` 环境变量，规则与 `standalone/images-package.sh` 完全一致）推断目标离线包路径 `<repo-root>/rainbond-images-<arch-tag>.tar.zst`；若该文件存在且 `force_rebuild` 为假值，SHALL 跳过 `business-images-package.sh` 并打印一行说明（包含被跳过的文件路径与提示 `force_rebuild=1` 可强制重建）；若文件不存在或 `force_rebuild=1`，SHALL 正常调用 `business-images-package.sh`。本规则仅在 `build_business_images=1` 时生效。

#### Scenario: 已有业务离线包被跳过

- **WHEN** 仓库根存在 `rainbond-images-arm64.tar.zst`，且执行 `./standalone_build.sh build_business_images=1`
- **THEN** 脚本 SHALL 打印一行包含被跳过文件路径与 `force_rebuild=1` 提示的说明，且 SHALL 不执行 `business-images-package.sh`

#### Scenario: force_rebuild 强制重建业务离线包

- **WHEN** 仓库根存在 `rainbond-images-arm64.tar.zst`，且执行 `./standalone_build.sh build_business_images=1 force_rebuild=1`
- **THEN** 脚本 SHALL 调用 `./standalone/business-images-package.sh` 重新生成离线包

#### Scenario: build_business_images=0 时跳过判断

- **WHEN** 执行 `./standalone_build.sh force_rebuild=1`（未传 `build_business_images`）
- **THEN** 脚本 SHALL 不对 `rainbond-images-<arch>.tar.zst` 做任何检查或重建动作

### Requirement: enable_proxy 同样作用于业务镜像构建

当 `enable_proxy=1` 与 `build_business_images=1` 同时开启时，`standalone_build.sh` SHALL 在调用 `./standalone/business-images-package.sh` 时同样导出 `HTTP_PROXY`/`HTTPS_PROXY`/`http_proxy`/`https_proxy`/`ALL_PROXY` 与 `NO_PROXY`，规则与 `enable_proxy` 现有的代理范围完全一致。

#### Scenario: 业务镜像拉取走代理

- **WHEN** 执行 `./standalone_build.sh enable_proxy=1 build_business_images=1`
- **THEN** 在 `./standalone/business-images-package.sh` 子进程环境中 SHALL 同时存在 `HTTPS_PROXY=http://127.0.0.1:7897` 与 `https_proxy=http://127.0.0.1:7897`，且 `NO_PROXY` 包含 `localhost,127.0.0.1`
