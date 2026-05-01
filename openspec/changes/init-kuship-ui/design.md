## Context

`reference/rainbond-ui` 是以 git submodule 形式只读引入的 Rainbond 控制台前端（UMI 3.5 + DVA 2.4 + React 16.8 + Antd 3.19，npm 包名 `rainbond-ui`）。kuship 仓库目前没有自己的可编辑前端工程。后端正在以 `kuship-console`（Java Spring Boot 重写 rainbond-console）的形式分阶段落地，未来需要前端把 API 流量从 rainbond-console 切到 kuship-console。

当前 rainbond-console 的测试实例由本仓库另一变更 `add-docker-compose-stack` 提供的 docker-compose 启动，对宿主机暴露 `http://localhost:7070`。kuship-ui 在开发态联调时，默认就要指向这个地址。

rainbond-ui 现有的开发态 API 代理由 `config/config.js` 中的环境变量 `CONSOLE_PROXY_TARGET` 控制（其上游硬编码默认是 `http://127.0.0.1:7070`），覆盖 `/console`、`/data`、`/openapi/v1`、`/enterprise-server`、`/app-server` 五条路径，全部指向同一个目标。这是后续切换的天然抓手。

约束：
- submodule `reference/rainbond-ui` 不能被改动或删除（团队保留其作为对照实现）。
- 不引入新的运行时依赖；保持依赖锁定文件（`yarn.lock`）以保证构建可复现。
- 不在本变更里实施任何 API 字面迁移——这是后续 kuship-console 落地变更的工作范围。

## Goals / Non-Goals

**Goals:**
- 在仓库根目录建立 `kuship-ui/`，内容为 rainbond-ui 当前 submodule 提交的快照（去除 `.git` 元信息），可独立 `yarn install && yarn start && yarn build`。
- 完成最小限度的项目身份替换：`package.json` 的 `name` / `description` / `image` 脚本里的镜像名改为 kuship 标识。
- 保留并显式化「后端目标」开关：沿用既有 `CONSOLE_PROXY_TARGET` 环境变量，默认值改为 `http://localhost:7070`（与 `add-docker-compose-stack` 启动的 rainbond-console 测试实例对齐），并在 `kuship-ui/README.md` 中写明如何切到 kuship-console。
- 仓库根 `CLAUDE.md` 与 `README.md` 的目录结构章节加入 `kuship-ui/`。

**Non-Goals:**
- 不替换 UI 框架版本（保持 UMI 3.5 / Antd 3.19 / React 16.8），不升级依赖。
- 不重写或删除任何 rainbond 业务逻辑、品牌资源、文案、i18n 文案——本变更只做「拷贝 + 改名」。
- 不修改 `reference/rainbond-ui` submodule。
- 不为 `kuship-ui` 编写 Dockerfile / 镜像构建脚本 / CI（留给后续变更）。
- 不实施 rainbond-console → kuship-console 的 API 迁移；只保证开关到位。

## Decisions

### Decision 1: 用 `cp -R` 拷贝快照，而不是 `git mv` / submodule 重指 / 符号链接

- 选择：使用 shell 拷贝（`cp -R` 或 `rsync -a --exclude`），把 `reference/rainbond-ui/` 当前工作树内容复制到 `kuship-ui/`，剔除 `.git`、`node_modules`、`dist`、`.umi`、`.umi-production`、`.idea`、`.vscode/settings.json` 等本地/构建产物（参考 `reference/rainbond-ui/.gitignore`）。
- 替代方案：
  - **重指 submodule 为内部仓库**：放弃。我们要求 `reference/rainbond-ui` 继续作为只读对照保留。
  - **`git mv` 把 submodule 转换为常规目录**：放弃。会破坏 submodule 引用，且 git 不允许直接把 submodule 工作树原地转换为常规跟踪文件。
  - **符号链接**：放弃。无法独立演进，且在 Windows / Docker 构建中行为不一致。
- 理由：拷贝快照保证 `kuship-ui` 是一份**独立可演进**的工程，与 submodule 完全解耦；首次提交会很大但只会发生一次。

### Decision 2: 通过既有 `CONSOLE_PROXY_TARGET` 环境变量做后端切换，并把硬编码默认值对齐到 docker-compose 测试栈

- 选择：保持 `kuship-ui/config/config.js` 中通过 `CONSOLE_PROXY_TARGET` 控制代理目标的机制不变；唯一改动是把硬编码默认值由上游的 `http://127.0.0.1:7070` 改为 `http://localhost:7070`，与本仓库 `add-docker-compose-stack` 启动的 rainbond-console 测试实例对齐（开发者克隆仓库后无需设置任何环境变量即可联调）。并在 `kuship-ui/README.md` 增加一节「后端目标切换」说明：
  - 默认：`yarn start` → 走 docker-compose 启动的 rainbond-console（`http://localhost:7070`）。
  - 切到 kuship-console：`CONSOLE_PROXY_TARGET=http://localhost:<kuship-console-port> yarn start`（具体端口由 kuship-console 在后续变更中确定）。
- 替代方案：
  - **保留 `127.0.0.1` 默认值不动**：放弃。文档与代码对「测试地址」描述会出现两套写法（`127.0.0.1` vs `localhost`），后续易混淆；docker-compose 也是用 `localhost` 暴露端口的语义。
  - **新增 `KUSHIP_CONSOLE_TARGET` 等并行变量并加判定**：放弃。在没有确定 kuship-console 监听端口前，引入第二个变量只会带来不一致。
  - **直接把默认值改为 kuship-console**：放弃。kuship-console 尚未提供等价 API，会让前端开箱即坏。
- 理由：保持「最小变更」原则，把切换归一到 1 个已存在的环境变量上；唯一允许的代码改动是把默认主机名对齐到本仓库测试栈，避免开箱即坏或与 docker-compose 描述不一致。

### Decision 3: 项目身份只改最小集合，不动业务命名/品牌资源

- 选择：本变更里只修改：
  - `kuship-ui/package.json` 的 `name`（`rainbond-ui` → `kuship-ui`）、`description`、以及 `scripts.image` 中的镜像 tag。
  - `kuship-ui/README.md` 与 `kuship-ui/README.zh-CN.md` 顶部标题与「项目说明 / 启动方式」段落改为面向 kuship-ui 的描述（保留原有开发说明可作引用链接）。
  - 仓库根 `CLAUDE.md` 与 `README.md` 的目录章节加入 `kuship-ui/`。
- 不修改：`src/` 下的代码、品牌图标、i18n 文案、产品名称、HTML title、license 头等。
- 理由：避免在「拷贝起步」阶段做大面积重命名，造成审查噪音和 merge 冲突；这些后续可以由独立变更分批处理。

### Decision 4: 拷贝采用脚本化、可复核、幂等的方式

- 选择：在 `kuship-ui/` 不存在时执行一次性拷贝命令（手动执行或写到 `tasks.md` 中），不留持续同步脚本（kuship-ui 与 rainbond-ui 自此独立演进）。
- 排除项严格按 rainbond-ui 的 `.gitignore` 走：`node_modules`、`/dist`、`.umi`、`.umi-production`、`/coverage`、`.idea`、`/build/dist`、`.scannerwork`、`local_release.sh`、`.agents/`、`.claude/knowledge/`、`*bak`、`.DS_Store`、`yarn-error.log`、`npm-debug.log*`、`/src/utils/request-temp.js`、`_roadhog-api-doc`、`jsconfig.json`、`.vscode/settings.json`，并额外排除 submodule 自带的 `.git` 文件。
- 理由：保证首次提交干净、可复核；不留「同步脚本」是因为目标就是分叉而非镜像。

## Risks / Trade-offs

- **风险**：拷贝后 `kuship-ui` 与 rainbond-ui 不再随 submodule 升级同步。
  → **缓解**：在 `kuship-ui/README.md` 顶部明确写「本工程基于 rainbond-ui 当前提交 `<commit-hash>` 拷贝而来，后续独立演进；如需查阅原始实现请看 `reference/rainbond-ui/`」。

- **风险**：首次提交体积大，PR 难审查。
  → **缓解**：提交分层：(1) 一次纯拷贝提交（diff 几乎全是新增），(2) 单独提交项目身份与配置改动。审查者可只盯第二个提交。

- **风险**：包名 `rainbond-ui` 改为 `kuship-ui` 后，可能有内部脚本/工具依赖该名。
  → **缓解**：`grep -r "rainbond-ui"` 全仓搜索调用方，本仓库目前只有 `reference/rainbond-ui` submodule 引用此名（不动），standalone 镜像构建当前不依赖 kuship-ui，因此影响面是 0。

- **权衡**：保留 Antd 3.19 / UMI 3.5 旧栈意味着继承技术债。
  → **决定**：本变更不解决，留作后续 spec 升级。

- **风险**：`CONSOLE_PROXY_TARGET` 默认仍指 rainbond-console（`http://localhost:7070`），kuship-console 真正落地时易被忽略。
  → **缓解**：在后续「kuship-console 后端切换」变更里把默认值改掉，并通过 spec 验证。本变更不解决。

- **风险**：把默认主机名从 `127.0.0.1` 改为 `localhost` 后，在某些 IPv6-only 或 hosts 文件被改写的环境里，`localhost` 解析行为可能与 `127.0.0.1` 不一致。
  → **缓解**：项目目标环境（开发者本机 + docker-compose 暴露端口）均把 `localhost` 解析为 `127.0.0.1`；如遇异常仍可显式设置 `CONSOLE_PROXY_TARGET=http://127.0.0.1:7070` 覆盖，覆盖路径已在 README 中给出。

## Migration Plan

1. 在 `kuship-ui/` 不存在的前提下，从 `reference/rainbond-ui/` 拷贝快照（按 Decision 1 / Decision 4 的排除清单）。
2. 修改 `kuship-ui/package.json`、`kuship-ui/README.md`、`kuship-ui/README.zh-CN.md`（按 Decision 3）。
3. 在 `kuship-ui/README.md` 增补「后端目标切换」说明（按 Decision 2）。
4. 更新仓库根 `CLAUDE.md` 与 `README.md` 目录结构章节。
5. 在干净环境跑 `cd kuship-ui && yarn install && yarn build` 验证可构建。

回滚：直接 `rm -rf kuship-ui/` 并回滚 `CLAUDE.md` / `README.md` 的目录章节。本变更不影响 submodule 与现有 standalone 链路。

## Open Questions

- kuship-console 的开发态监听端口与 API 前缀（`/console`、`/openapi/v1` 等是否完整保留）暂未敲定——本变更不依赖该信息，留待 kuship-console 后端落地变更确认。
- 是否需要锁定本次拷贝对应的 rainbond-ui submodule commit 到 `kuship-ui/README.md`（推荐做，便于追溯）。
- `add-docker-compose-stack` 暴露端口的最终配置（`7070` 是否会因冲突而调整）尚在那边的变更里确认，本变更先按 `7070` 写默认；若该变更最终落地端口不同，需要由后续小变更对齐。
