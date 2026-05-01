## 1. 拷贝快照

- [x] 1.1 在仓库根目录确认 `kuship-ui/` 不存在；若存在则停止并与发起者确认（避免覆盖）
- [x] 1.2 记录当前 `reference/rainbond-ui` 的 submodule 提交哈希（`git -C reference/rainbond-ui rev-parse HEAD`），写入步骤 3 的 README 顶部说明
- [x] 1.3 使用 `rsync -a` 把 `reference/rainbond-ui/` 内容拷贝到 `kuship-ui/`，排除：`.git`、`.gitignore` 中的所有路径（`node_modules`、`/dist`、`.umi`、`.umi-production`、`/coverage`、`.idea`、`/build/dist`、`.scannerwork`、`local_release.sh`、`.agents/`、`.claude/knowledge/`、`*bak`、`.DS_Store`、`yarn-error.log`、`npm-debug.log*`、`/src/utils/request-temp.js`、`_roadhog-api-doc`、`jsconfig.json`、`.vscode/settings.json`），同时保留原仓库的 `.gitignore` 文件本体
- [x] 1.4 验证 `kuship-ui/.git` 不存在、`kuship-ui` 不是符号链接（`test ! -e kuship-ui/.git && test ! -L kuship-ui`）
- [x] 1.5 验证 `reference/rainbond-ui` submodule 状态未被本步骤改变（`git submodule status reference/rainbond-ui`）

## 2. 项目身份替换

- [x] 2.1 修改 `kuship-ui/package.json`：`name` 改为 `kuship-ui`、`description` 改为 kuship 控制台前端的简短说明、`scripts.image` 中的镜像 tag 不再包含 `rainbond-ui` 字面值（替换为 kuship 对应的占位 tag，无需立即可推送）
- [x] 2.2 修改 `kuship-ui/README.md` 顶部标题与项目说明段落，使其面向 kuship-ui；保留原 rainbond-ui 链接以便追溯
- [x] 2.3 修改 `kuship-ui/README.zh-CN.md` 顶部标题与项目说明段落，与英文 README 同步
- [x] 2.4 在 `kuship-ui/README.md` 顶部追加一句说明：「本工程基于 rainbond-ui 提交 `<commit-hash>` 拷贝而来，自此独立演进」（commit-hash 取自 1.2）

## 3. 后端目标切换开关

- [x] 3.1 确认 `kuship-ui/config/config.js` 中 `CONSOLE_PROXY_TARGET` 仍是 5 条代理路径（`/console`、`/data`、`/openapi/v1`、`/enterprise-server`、`/app-server`）的唯一目标来源；若被本变更其他步骤误改则恢复
- [x] 3.2 把 `kuship-ui/config/config.js` 中的硬编码默认值由 `http://127.0.0.1:7070` 改为 `http://localhost:7070`，与本仓库 `add-docker-compose-stack` 启动的 rainbond-console 测试实例对齐
- [x] 3.3 在 `kuship-ui/README.md` 增加一节「后端目标切换 / Backend target」，说明：默认 `CONSOLE_PROXY_TARGET=http://localhost:7070`（对应 docker-compose 启动的 rainbond-console 测试实例，端口由 `add-docker-compose-stack` 提供）；切换到 kuship-console 时通过 `CONSOLE_PROXY_TARGET=<kuship-console-url> yarn start` 覆盖；并注明 kuship-console 端口待其落地后由独立变更确定
- [x] 3.4 在 `kuship-ui/README.zh-CN.md` 同步增加同名章节

## 4. 仓库根目录文档更新

- [x] 4.1 修改仓库根 `CLAUDE.md` 的目录结构代码块，加入 `kuship-ui/` 条目（与既有 `kuship-console/` 并列），附一句中文说明（已由先前 CLAUDE.md 编辑包含）
- [x] 4.2 修改仓库根 `README.md` 中描述项目结构的章节，加入 `kuship-ui/` 条目（如该 README 没有结构章节则补一个最小段落）

## 5. 构建验证（由开发者本地完成）

> 备注：当前实施环境只有 Node v24，且未安装 yarn；UMI 3.5 + 老依赖在 Node 24 上风险高。本节验证由开发者在合适 Node（建议 Node 16/18 + corepack enable yarn）下完成，不在本变更里勾选。

- [ ] 5.1 `cd kuship-ui && yarn install --frozen-lockfile` 成功 *（由开发者本地验证）*
- [ ] 5.2 `cd kuship-ui && yarn build` 成功，产物目录存在 *（由开发者本地验证）*
- [ ] 5.3 抽查启动：`cd kuship-ui && yarn start` 能起服（不要求后端在线，只验证前端 dev server 启动不报错），随后立即停止 *（由开发者本地验证）*

## 6. 规范校验

- [x] 6.1 `openspec status --change init-kuship-ui` 显示 `tasks` 状态为 `done`
- [x] 6.2 对照 `specs/kuship-ui-app/spec.md` 的全部 Scenario 逐条人工核验通过（目录存在、submodule 未动、不是符号链接、name 字段、README 标题、image 脚本字面值、默认代理目标解析为 `http://localhost:7070`、覆盖代理目标、README 同时说明 docker-compose 默认与 kuship-console 切换、根 CLAUDE.md / README.md 已包含 kuship-ui）；R2 的 yarn install / yarn build 两个 Scenario 由开发者本地验证（见第 5 节）

## 7. 提交分层

- [x] 7.1 第一次提交：仅包含 `kuship-ui/` 的纯拷贝（步骤 1 的产物），diff 几乎全为新增；提交信息：`feat(kuship-ui): copy rainbond-ui snapshot as kuship-ui base`
- [x] 7.2 第二次提交：包含步骤 2 / 3 / 4 的所有改动；提交信息：`feat(kuship-ui): rebrand identity and document backend target switch`
- [x] 7.3 不在任何提交中改动 `reference/rainbond-ui` submodule
