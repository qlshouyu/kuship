## Why

13 阶段路线终点：把 kuship-console 从 Spring Boot fat jar（~150MB / 启动 8s / 运行时内存 ~600MB）编译为 **GraalVM Native Image** 二进制（~80MB / 启动 < 1s / 内存 ~250MB）。

意义：
- **standalone 镜像更小更快**：standalone bundle（已含 k3s 离线 + rainbond docker）打包后镜像从 ~3.5GB 降到 ~3GB，VM/物理机首次启动从分钟级降到秒级。
- **嵌入式 / 边缘节点可用**：ARM 嵌入式设备 / IoT 边缘 / 小内存 VM（512MB 即可跑）能首次承载 console。
- **冷启动友好**：Kubernetes Pod 重启 / 节点抢占恢复时，console 几秒内可用，不再有"启动中"窗口。
- **CI/CD 加速**：单元测试 + 启动验证从 ~30s 降到 ~5s。

技术前提：前 12 阶段已有意识地避免 graalvm-native 不兼容特性（不上 SSE / WebSocket / reactor-netty / springdoc 反射魔法 / 复杂 AOT 代理），路线开局即埋好了 native 兼容性的种子。

## What Changes

- **pom.xml 新增 native profile**：标准 `spring-boot-starter-parent` + `native` profile + `org.graalvm.buildtools:native-maven-plugin:0.10.4` —— 通过 `mvn -Pnative -DskipTests native:compile` 触发 native build。
- **spring-aot-maven-plugin 配置**：自动生成反射 / 资源 / 代理元数据（GraalVM hint），覆盖 Spring Boot 自身扫描。
- **手写 RuntimeHints**：`KuShipConsoleRuntimeHints implements RuntimeHintsRegistrar` —— 注册无法自动发现的反射类（JPA Entity / Lombok 生成的 record / Jackson 多态等）；用 `@ImportRuntimeHints` 关联。
- **Resources 显式注册**：`application*.yaml` / `db/migration/**` / `mapper/**.xml` / 静态文件（如 swagger.json fallback）等通过 `resource-config.json` 注册到 native image。
- **JNI / Reflection / Proxy 元数据**：复用 graalvm-reachability-metadata 仓库的 mysql-connector-j / hibernate-core / spring-security 已知 hint 包；启动时 GraalVM 自动 merge。
- **HTTP / TLS 兼容性**：`-H:+AddAllCharsets` 必加；MySQL TLS 需 `--enable-url-protocols=https`；启动时 `META-INF/native-image/io.netty/...` 由 reachability metadata 仓库提供。
- **profile 选择**：`-Dspring.profiles.active=local` 在 `mvn -Pnative` 时同样生效；prod profile 启动时通过 `-Dspring.profiles.active=prod` 注入。
- **构建产物**：`target/kuship-console`（Linux/macOS 可执行文件）+ `target/kuship-console.dockerfile`（生成 minimal docker image：`scratch` 或 `gcr.io/distroless/base-debian12` 基镜）。
- **standalone 镜像集成**：修改根 `standalone/Dockerfile`，从 fat jar 切换到 native binary（多阶段构建：第 1 阶段 build native；第 2 阶段 distroless 装载 binary + entrypoint.sh）。
- **GitHub Actions / 本地脚本**：`scripts/native-build.sh` 一键 native build；CI 在 `ubuntu-latest-arm64` + `ubuntu-latest` 双架构构建。
- **已知不兼容的 fix**：
  - **Lombok @Setter 在 record 上**：record 不允许 setter；本项目无此用法（已检查）
  - **Hibernate 6 native 兼容**：需 `hibernate.jakarta.persistence.bytecode.strategy = none` 关闭字节码增强（运行时性能损失 5%，可接受）
  - **JPA `EntityManagerFactory` proxy**：spring-boot 4 已自动注册 hint
  - **Caffeine cache**：reachability metadata 提供
  - **MapStruct compile-time**：natively compatible（编译期已生成 impl 类）

## Capabilities

### Modified Capabilities

- `kuship-console-app`: 新增约 6 条 native 相关 Requirement —— Maven profile / RuntimeHints / Resources 注册 / 启动后端到端 smoke test / Dockerfile 多阶段 / 启动时长 SLO（< 2s）。

## Impact

- **新增文件**：
  - `kuship-console/src/main/java/cn/kuship/console/config/native_/KuShipConsoleRuntimeHints.java`
  - `kuship-console/src/main/resources/META-INF/native-image/cn.kuship/kuship-console/{reflect-config.json, resource-config.json, native-image.properties}`
  - `kuship-console/src/main/java/cn/kuship/console/config/native_/NativeProfileGuards.java`（启动时 sanity check：禁用反射魔法）
  - `scripts/native-build.sh` + `Dockerfile.native`（多阶段构建）
- **修改文件**：
  - `kuship-console/pom.xml`：添加 `native` profile + `native-maven-plugin` + `spring-boot-maven-plugin` 的 native 子配置
  - `kuship-console/src/main/resources/application.yaml`：添加 `spring.aot.enabled=true`（仅 native profile） + 关闭 Hibernate 字节码增强
  - 根 `standalone/Dockerfile`：切换到多阶段 native 构建（可选；保留 fat-jar 路径作为 fallback）
- **依赖**：保持现有依赖；GraalVM Native Image 构建仅靠 plugin。
- **CI**：GitHub Actions 新增 `.github/workflows/native-build.yml`（matrix: ubuntu-22.04 amd64 + ubuntu-22.04-arm64）。
- **测试**：扩展 1 类 native smoke test（`mvn -Pnative test` 跑现有 101 用例的子集——避开有 mock 反射的，用 `mvn -Pnative -Dtest=ContractIntegrationTest test` 单点验证）。
- **不引入新业务 entity / endpoint**：本 change 是构建系统优化，无业务逻辑变更。
