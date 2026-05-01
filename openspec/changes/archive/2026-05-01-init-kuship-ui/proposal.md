## Why

kuship 项目目前只规划了后端 `kuship-console`（Java Spring Boot 重写 rainbond-console），缺少与之配套的可独立演进的前端代码库；而参考实现 `reference/rainbond-ui` 是只读的 git submodule，无法在其上做项目化定制。我们需要在仓库根目录建立 `kuship-ui` 作为可编辑的控制台前端起点，先复用 rainbond-ui 的页面与交互保证功能完整，再在后续 kuship-console 接口逐步落地时将 API 切换到 kuship-console。

## What Changes

- 在仓库根目录新增 `kuship-ui/`，内容来自 `reference/rainbond-ui` 的当前提交（去除 git 元信息），作为可独立演进的前端工程。
- 调整 `kuship-ui` 的项目身份：`package.json` 的 `name`、`description`、构建产物镜像名等改为 kuship 标识；保留原 UMI/dva/React 技术栈、依赖版本与构建脚本不变。
- 默认仍以 rainbond-console 作为后端联调目标（其测试实例由本仓库 `add-docker-compose-stack` 提供的 docker-compose 启动，监听 `http://localhost:7070`），同时在配置层（环境变量 / proxy / 配置文件）预留一处显式的「后端目标」开关，以便后续切换到 kuship-console 时只改一处。
- 更新仓库根 `CLAUDE.md` 与 `README.md` 中的目录说明，将 `kuship-ui/` 与 `kuship-console/` 并列描述。
- 不修改 `reference/rainbond-ui` submodule，本变更不涉及 rainbond-ui 的上游改动，也不删除 submodule。
- 不在本变更内执行从 rainbond-console 到 kuship-console 的 API 迁移；仅保证「后端目标可切换」的开关已就位。

## Capabilities

### New Capabilities
- `kuship-ui-app`: 控制台前端工程的存在性、构建启动方式、与后端通信目标的可配置切换（rainbond-console / kuship-console）。

### Modified Capabilities
<!-- 无：本仓库目前没有针对前端工程的既有 spec -->

## Impact

- 新增代码：仓库根目录 `kuship-ui/`（从 rainbond-ui 拷贝而来），首次提交体量较大。
- 配置：`kuship-ui` 中 `config/` 下的代理 / 环境配置新增「后端目标」开关；不影响 rainbond-ui submodule 与现有 standalone 镜像构建。
- 文档：根 `CLAUDE.md`、`README.md` 目录结构章节需要更新。
- 依赖：沿用 rainbond-ui 的 `package.json` 锁定依赖，不引入新依赖。
- 后端 API：本变更不动 API；后续 kuship-console 落地时会基于本变更预留的开关切换。
- 构建/部署：standalone 镜像与现有 rainbond-ui 镜像构建链路不受影响；`kuship-ui` 的镜像化在后续变更中处理。
