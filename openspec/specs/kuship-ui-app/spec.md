# kuship-ui-app Specification

## Purpose
TBD - created by archiving change init-kuship-ui. Update Purpose after archive.
## Requirements
### Requirement: kuship-ui 工程在仓库根目录独立存在

仓库根目录 SHALL 存在 `kuship-ui/` 目录作为可独立演进的控制台前端工程，其内容来源于本变更对 `reference/rainbond-ui` 当前 submodule 提交的一次性拷贝，并去除拷贝源中的 git 元数据与构建/IDE 临时产物。`kuship-ui/` 与 `reference/rainbond-ui/` 之间 MUST NOT 存在符号链接、submodule 或同步脚本关系。

#### Scenario: kuship-ui 目录存在且无 git 元数据

- **WHEN** 在仓库根目录执行 `ls kuship-ui/.git` 与 `ls kuship-ui/package.json`
- **THEN** `.git` 路径不存在（不是文件也不是目录），且 `package.json` 存在

#### Scenario: 不破坏 reference/rainbond-ui submodule

- **WHEN** 在仓库根目录执行 `git submodule status reference/rainbond-ui`
- **THEN** 命令成功且 submodule 状态没有被本变更修改（既不是删除、也不是 commit 漂移到非上游提交）

#### Scenario: kuship-ui 不通过符号链接指向 reference

- **WHEN** 对 `kuship-ui` 执行 `test -L kuship-ui`
- **THEN** 返回非零（即 `kuship-ui` 是真实目录，不是符号链接）

### Requirement: kuship-ui 可独立完成依赖安装与生产构建

`kuship-ui/` 工程 SHALL 在不依赖 `reference/rainbond-ui` 的前提下，通过其自带的 `package.json` 与 `yarn.lock` 完成 `yarn install` 与 `yarn build`，构建结果与拷贝来源等价（无功能性回退）。

#### Scenario: yarn install 成功

- **WHEN** 在 `kuship-ui/` 目录中执行 `yarn install --frozen-lockfile`
- **THEN** 命令以退出码 0 结束，且生成 `node_modules/`

#### Scenario: yarn build 成功

- **WHEN** 在 `kuship-ui/` 目录中执行 `yarn build`
- **THEN** 命令以退出码 0 结束，生成生产构建产物（如 `dist/` 目录）

### Requirement: kuship-ui 项目身份反映 kuship 而非 rainbond

`kuship-ui/package.json` 的 `name` 字段 MUST 等于 `kuship-ui`，`description` MUST 描述本工程为 kuship 控制台前端；`scripts` 中涉及镜像构建的命令（如 `image`）MUST NOT 仍然使用 `rainbond-ui` 字面值作为镜像名。`kuship-ui/README.md` 与 `kuship-ui/README.zh-CN.md` 的标题与项目简介 MUST 介绍 kuship-ui 而非 rainbond-ui。

#### Scenario: package.json name 字段已更新

- **WHEN** 读取 `kuship-ui/package.json`
- **THEN** `name` 字段值为 `kuship-ui`

#### Scenario: README 标题已更新

- **WHEN** 读取 `kuship-ui/README.md` 的首个标题行
- **THEN** 该标题包含 `kuship-ui`（不区分大小写），不再以 `rainbond-ui` 为唯一标题

#### Scenario: image 脚本的镜像名不再使用 rainbond-ui 字面值

- **WHEN** 读取 `kuship-ui/package.json` 中 `scripts.image` 的内容
- **THEN** 该字符串中不出现 `rainbond-ui`（用于镜像名）

### Requirement: kuship-ui 的开发态后端目标可通过单一环境变量切换

`kuship-ui/` 在开发态（`yarn start`）下 SHALL 把 `/console`、`/data`、`/openapi/v1`、`/enterprise-server`、`/app-server` 五条路径的代理目标统一通过环境变量 `CONSOLE_PROXY_TARGET` 控制；该变量未设置时 MUST 默认指向 `http://localhost:7070`（即由本仓库 `add-docker-compose-stack` 启动的 rainbond-console 测试实例），以便在 kuship-console 落地前保持开箱即用的联调能力。`kuship-ui/README.md` MUST 提供一节说明该默认值的来源（docker-compose 启动的 rainbond-console），以及如何通过该变量切换到 kuship-console。

#### Scenario: 默认代理目标指向 docker-compose 启动的 rainbond-console

- **WHEN** 在不设置 `CONSOLE_PROXY_TARGET` 的环境下读取 `kuship-ui/config/config.js` 的代理目标解析结果
- **THEN** 五条代理路径的目标地址都解析为 `http://localhost:7070`

#### Scenario: 通过环境变量切换代理目标

- **WHEN** 设置 `CONSOLE_PROXY_TARGET=http://example.kuship-console:9090` 后启动 `yarn start`
- **THEN** 五条代理路径的目标地址都解析为 `http://example.kuship-console:9090`

#### Scenario: README 说明默认值来源与切换方式

- **WHEN** 在 `kuship-ui/README.md` 中搜索 `CONSOLE_PROXY_TARGET`
- **THEN** 至少出现一次，且其上下文同时说明：(a) 默认值 `http://localhost:7070` 对应 docker-compose 启动的 rainbond-console 测试实例；(b) 如何把后端切换到 kuship-console

### Requirement: 仓库根目录文档反映 kuship-ui 的存在

仓库根 `CLAUDE.md` 与 `README.md` 的目录结构章节 MUST 列出 `kuship-ui/` 并标注其用途为 kuship 控制台前端，与既有 `kuship-console/` 条目并列描述。

#### Scenario: CLAUDE.md 目录结构包含 kuship-ui

- **WHEN** 在仓库根 `CLAUDE.md` 的目录结构代码块中查找 `kuship-ui`
- **THEN** 至少出现一次，且伴随说明性注释（一句话即可）

#### Scenario: README.md 目录结构包含 kuship-ui

- **WHEN** 在仓库根 `README.md` 中查找 `kuship-ui`
- **THEN** 至少在描述项目结构的章节中出现一次

