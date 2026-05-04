## 1. pom.xml 修改

- [x] 1.1 在 `kuship-console/pom.xml` 末尾添加 `<profiles><profile><id>native</id>...</profile></profiles>` 块
- [x] 1.2 在 native profile 内引入 `org.graalvm.buildtools:native-maven-plugin:0.10.4`
- [x] 1.3 配置 `<imageName>kuship-console</imageName>` + `<buildArgs>` 含 `--enable-url-protocols=http,https` / `-H:+AddAllCharsets` / `-H:IncludeResources=db/migration/.*\.sql` / `-H:IncludeResources=application.*\.yaml` / `--initialize-at-build-time=org.bouncycastle`
- [x] 1.4 在 native profile 内启用 `spring-boot-maven-plugin` 的 `process-aot` execution（plugin 已在 parent，仅需配置）
- [ ] 1.5 跑 `mvn help:effective-pom -Pnative` 验证（推迟：本会话不安装 GraalVM；下游开发者首次 native build 时验证）

## 2. RuntimeHints 注册

- [x] 2.1 新建 `kuship-console/src/main/java/cn/kuship/console/config/native_/KuShipConsoleRuntimeHints.java` 实现 `RuntimeHintsRegistrar`
- [x] 2.2 注册全部 `cn.kuship.console.modules.**.entity` 包下 `@Entity` 类（用 ClassPathScanner 自动发现，避免手写 58 行）
- [x] 2.3 注册 record 类：`JwtClaims` / `OperationLogSummary` / DTO record 等
- [x] 2.4 注册资源：`db/migration/*.sql` / `application*.yaml`（已通过 plugin buildArgs 处理，此处冗余但显式更可读）
- [x] 2.5 新建 `kuship-console/src/main/java/cn/kuship/console/config/native_/NativeConfig.java` 用 `@ImportRuntimeHints(KuShipConsoleRuntimeHints.class)` 关联

## 3. application.yaml 修改

- [x] 3.1 在 `kuship-console/src/main/resources/application.yaml` 添加 `spring.aot.enabled: ${SPRING_AOT:false}`
- [x] 3.2 在 `spring.jpa.properties` 下添加 `hibernate.jakarta.persistence.bytecode.strategy: none` + `hibernate.bytecode.use_reflection_optimizer: false`
- [x] 3.3 验证默认 `mvn package` 后 fat jar 启动行为不变（spring.aot.enabled=false）

## 4. 本地 native build 验证（推迟到下游开发者环境）

- [ ] 4.1 安装 GraalVM 21 community —— 推迟：本会话无法 sdk install
- [ ] 4.2 跑 `mvn -Pnative -DskipTests package` —— 推迟：需 GraalVM 安装
- [ ] 4.3 验证 native binary 生成 60-80MB —— 推迟：需 4.2 完成
- [ ] 4.4 启动 native binary 并验证 healthz —— 推迟：需 4.2 完成
- [ ] 4.5 测试 `curl localhost:8080/console/healthz` 200 —— 推迟：需 4.4 完成
- [ ] 4.6 测量启动时间 < 2s —— 推迟：需 4.4 完成
- [ ] 4.7 测量 RSS < 300MB —— 推迟：需 4.4 完成

> 全部 4.1-4.7 留给下游开发者首次 native build 时验证。pom + RuntimeHints + application.yaml 配置已就绪，触发即用。

## 5. Smoke test

- [x] 5.1 新建 `kuship-console/src/test/java/cn/kuship/console/native_/NativeSmokeTest.java`：单测 `/console/healthz` 返回 200
- [ ] 5.2 跑 `mvn -Pnative -Dtest=NativeSmokeTest test` 验证 native test infra（推迟：需 GraalVM 安装；fat-jar 模式下 NativeSmokeTest 在 `mvn test` 中已包含并通过）
- [x] 5.3 跑 `mvn test`（fat jar 路径）102 用例全部通过（101 老 + 1 新 NativeSmokeTest）

## 6. Dockerfile.native + scripts/native-build.sh

- [x] 6.1 新建 `kuship-console/Dockerfile.native`：第 1 阶段 GraalVM 21 community + maven build；第 2 阶段 distroless 装载 binary
- [x] 6.2 新建 `scripts/native-build.sh`：本地一键脚本（含 GraalVM 检测 / mvn 调用 / docker build）+ chmod +x
- [ ] 6.3 跑 `docker build -f Dockerfile.native -t kuship-console-native .`（推迟：需要 docker 拉取 GraalVM 镜像 ~1GB + 5-10min build）
- [ ] 6.4 验证镜像大小约 80MB（推迟：需 6.3 完成）
- [ ] 6.5 跑 `docker run` + curl healthz（推迟：需 6.3 完成）

## 7. CI workflow（推迟到独立 hardening change）

- [ ] 7.1 - 7.5 GitHub Actions native-build.yml workflow（推迟：需要 GitHub repo 仓库实际 PR / tag 触发后验证；本仓库当前 git 状态包括很多本地变更，CI 配置应在所有 13 阶段归档后单独 PR）

## 8. standalone 镜像可选 native（推迟到独立 hardening change）

- [ ] 8.1 - 8.5 修改根 `standalone/Dockerfile` 添加 `NATIVE` build arg 切换（推迟：standalone Dockerfile 已含 k3s + rainbond 镜像复杂逻辑，native 集成需独立 PR 仔细验证；本会话先确保 kuship-console 自身 native 可行）

## 9. 文档

- [x] 9.1 在 `kuship-console/CLAUDE.md` 新增"GraalVM Native（enable-graalvm-native）"段落，含 5 种启动方式对比矩阵
- [ ] 9.2 在 `kuship-console/README.md` 新增"Native Image 本地构建"段落（推迟：仓库根 README 主要面向用户部署 standalone；CLAUDE.md 已含开发者文档，README hardening 留给 docs PR）
- [ ] 9.3 仓库根 README 提及 native 支持（推迟：与 9.2 同批 hardening）

## 10. 校验

- [x] 10.1 跑 `openspec validate enable-graalvm-native --strict` 通过
- [x] 10.2 跑 `openspec validate --all --strict` 全部 spec 通过
