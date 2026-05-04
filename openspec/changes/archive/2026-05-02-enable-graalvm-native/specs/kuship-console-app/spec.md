## ADDED Requirements

### Requirement: Maven native profile

kuship-console SHALL 在 `pom.xml` 中提供独立的 `native` Maven profile，包含 `org.graalvm.buildtools:native-maven-plugin:0.10.4` 配置；profile 不影响默认 `mvn package` 行为（继续产 fat jar），仅 `mvn -Pnative package` 触发 GraalVM Native Image 构建。

#### Scenario: 默认 mvn package 仍产 fat jar

- **WHEN** 跑 `mvn clean package`
- **THEN** `target/kuship-console-0.1.0-SNAPSHOT.jar` 生成，文件大小约 150MB
- **AND** 不触发 native:compile

#### Scenario: mvn -Pnative package 产 native binary

- **WHEN** 跑 `mvn -Pnative -DskipTests package` 在装有 GraalVM 21 community 的环境
- **THEN** `target/kuship-console`（Linux/macOS 可执行文件）生成
- **AND** 文件大小约 60-80MB

#### Scenario: native plugin buildArgs 显式启用必要选项

- **WHEN** 检查 native plugin 配置
- **THEN** `--enable-url-protocols=http,https` / `-H:+AddAllCharsets` / `-H:IncludeResources=db/migration/.*\\.sql` / `-H:IncludeResources=application.*\\.yaml` 全部出现

### Requirement: RuntimeHints 注册

kuship-console SHALL 提供 `KuShipConsoleRuntimeHints implements RuntimeHintsRegistrar`，通过 `@ImportRuntimeHints` 在 `NativeConfig` 中关联；hint 注册全部 `cn.kuship.console.modules.**.entity` 包下的 `@Entity` 类（用反射 scanner 自动遍历）+ `cn.kuship.console.common.**` 下的 record 类（如 JwtClaims / OperationLogSummary）。

#### Scenario: NativeConfig 加载

- **WHEN** 应用启动加载 `NativeConfig.class`
- **THEN** Spring 通过 `@ImportRuntimeHints` 关联 `KuShipConsoleRuntimeHints`
- **AND** native build 时 AOT processor 把 hint 写入 `target/native-image/META-INF/native-image/...`

#### Scenario: 全部 Entity 反射访问可用

- **WHEN** native binary 运行启动
- **THEN** Hibernate 通过反射访问 `UserInfo` / `Tenants` / `TenantService` 等 ~58 entity 不报 `ClassNotFoundException`

### Requirement: Spring AOT enable

kuship-console SHALL 在 `application.yaml` 中默认 `spring.aot.enabled=false`，但允许通过环境变量 `SPRING_AOT=true` 覆盖；native build 时 plugin 自动设此值为 true。普通 fat jar 启动时此值为 false，不受 AOT 影响。

#### Scenario: native build 时 spring.aot.enabled=true

- **WHEN** 跑 `mvn -Pnative package`
- **THEN** AOT processor 跑完后 `target/spring-aot/main/sources/...` 生成
- **AND** native image 包含 AOT 优化代码

#### Scenario: fat jar 启动时 spring.aot.enabled=false

- **WHEN** `java -jar fat.jar` 启动（默认）
- **THEN** Spring 不执行 AOT 加载路径，行为与第 1-12 阶段完全一致

### Requirement: Hibernate 字节码增强关闭

kuship-console SHALL 在 `application.yaml` 中设 `hibernate.jakarta.persistence.bytecode.strategy=none` 与 `hibernate.bytecode.use_reflection_optimizer=false`，关闭 Hibernate 6 默认的 ByteBuddy 字节码增强（与 GraalVM Native 不兼容）。

#### Scenario: 字节码增强关闭后 ddl-auto=validate 仍生效

- **WHEN** native binary 启动连真实 MySQL
- **THEN** Hibernate 跑 ddl-validate 校验所有 entity 列对齐 schema
- **AND** 不报 ByteBuddy 相关错误

#### Scenario: 5% 运行时性能损失可接受

- **WHEN** native binary 跑 100 次组件查询
- **THEN** 性能比 fat jar 慢 ~5%（lazy load 退化为 eager load）
- **AND** 该退化不阻塞正常使用（< 1k DAU 场景下 CPU 非瓶颈）

### Requirement: native smoke test

kuship-console SHALL 提供 1 个 native 兼容的 smoke 测试 `NativeSmokeTest`，验证 native image 启动 + 基础 endpoint 可调；通过 `mvn -Pnative -Dtest=NativeSmokeTest test` 触发。其余 100 个 fat-jar 测试用例不要求在 native image 下运行（部分用 Mockito mock 需独立 hint，留作 hardening）。

#### Scenario: NativeSmokeTest 通过

- **WHEN** 跑 `mvn -Pnative -Dtest=NativeSmokeTest test`
- **THEN** 测试在 native test infra 中启动应用 + 调 `/console/healthz` 返回 200
- **AND** 整个测试在 < 30 秒内完成

#### Scenario: fat jar 测试不受影响

- **WHEN** 跑 `mvn test`
- **THEN** 现有 101 测试用例继续全部通过（包括 16 集成测试 + 单测）

### Requirement: Native Dockerfile + 多阶段构建

kuship-console SHALL 在 `kuship-console/Dockerfile.native` 提供两阶段构建文件：第 1 阶段用 `ghcr.io/graalvm/native-image-community:21` 镜像 + Maven build native binary；第 2 阶段用 `gcr.io/distroless/base-debian12` 装载 binary。最终镜像约 80MB。

#### Scenario: docker build 成功

- **WHEN** 跑 `docker build -f Dockerfile.native -t kuship-console-native .`
- **THEN** 构建成功生成镜像
- **AND** `docker images kuship-console-native --format "{{.Size}}"` 显示约 80MB

#### Scenario: docker run 启动 < 2s

- **WHEN** 跑 `docker run -p 8080:8080 kuship-console-native`
- **THEN** 容器启动到 `/console/healthz` 返回 200 全过程 < 2 秒

### Requirement: 启动性能 SLO

kuship-console native binary 启动时间 SHALL 在 macOS M2 / Linux x86_64 标准硬件上 < 2 秒；运行时 RSS 内存 < 300MB。

#### Scenario: 本地启动时间测量

- **WHEN** 在 macOS M2 上跑 `time ./target/kuship-console`
- **THEN** "Started KuShipConsoleApplication in" 日志在启动后 < 2.0s 出现

#### Scenario: 运行时内存测量

- **WHEN** native binary 启动稳定后跑 `ps -o rss= -p $(pgrep kuship-console) | awk '{print $1/1024 \" MB\"}'`
- **THEN** RSS 输出 < 300 MB

### Requirement: standalone 镜像可选切换 native

kuship-console SHALL 在根 `standalone/Dockerfile` 中提供 `NATIVE` build arg（默认 `false`），允许用户通过 `docker build --build-arg NATIVE=true` 切换到多阶段 native 构建路径；默认行为保持 fat-jar 不变（向后兼容）。

#### Scenario: 默认 build arg 仍走 fat-jar

- **WHEN** 跑 `docker build -f standalone/Dockerfile -t kuship-standalone .`
- **THEN** 镜像内仍打包 fat jar（与 12 阶段后行为一致）

#### Scenario: 显式 NATIVE=true 切换

- **WHEN** 跑 `docker build --build-arg NATIVE=true -f standalone/Dockerfile .`
- **THEN** 第 1 阶段编译 native binary，第 2 阶段装载到 standalone 镜像
- **AND** 镜像总大小（含 k3s 离线 + rainbond docker）从 ~3.5GB 降到 ~3GB

### Requirement: CI 双架构 native build

kuship-console SHALL 在 `.github/workflows/native-build.yml` 中配置 GitHub Actions matrix 构建：`ubuntu-22.04`（amd64） + `ubuntu-22.04-arm64`（arm64）双架构；构建产物作为 artifact 上传 release。CI 仅在 release tag 触发，避免每个 PR 增加 ~10 分钟构建时间。

#### Scenario: PR 仍用 fat-jar 测试

- **WHEN** 提交 PR
- **THEN** 触发现有 fat-jar workflow（`mvn test`），不触发 native build

#### Scenario: release tag 触发双架构 build

- **WHEN** 推送 `v*.*.*` tag
- **THEN** native-build.yml 启动 amd64 + arm64 矩阵构建
- **AND** artifact `kuship-console-amd64` 与 `kuship-console-arm64` 上传

### Requirement: 文档与启动方式矩阵

kuship-console SHALL 在 `kuship-console/CLAUDE.md` 与 `kuship-console/README.md` 新增"GraalVM Native（enable-graalvm-native）"段落，列出 5 种启动方式（fat jar dev / fat jar prod / native dev / native prod / docker native）的对比矩阵：启动时间 / 内存 / 用途。

#### Scenario: CLAUDE.md 含 native 段落

- **WHEN** 阅读 `kuship-console/CLAUDE.md`
- **THEN** 文档含"GraalVM Native"标题段
- **AND** 段落含 5 种启动方式对比表（启动时间 / 内存 / 用途三列）

#### Scenario: README 含本地 native 构建说明

- **WHEN** 阅读 `kuship-console/README.md`
- **THEN** 文档含 `mvn -Pnative -DskipTests package` 命令 + 必要前置条件（GraalVM 21 community 安装步骤）
