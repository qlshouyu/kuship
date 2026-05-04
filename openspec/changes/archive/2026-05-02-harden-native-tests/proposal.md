## Why

Phase 13 (`enable-graalvm-native`) 完成了 GraalVM Native Image 构建管道，但只有一个 `NativeSmokeTest` 验证启动健康。kuship-console 现有 102/102 JVM 测试用例（17 个集成测试类，覆盖账户/集群/应用/组件/插件/市场/OpenAPI 等关键路径）在 native image 下大多数会因 Mockito 反射注入、ApplicationContext 重启、@MockBean 字节码生成、Spring `@SpringBootTest` random port 而无法直接运行。这意味着 native binary 一旦出现回归（错配 hint、漏注册资源、字段反射缺失），CI 的 JVM 测试不会捕获，问题只会在生产部署时浮现。

本次 hardening 为剩余 JVM 测试套件补齐 GraalVM 兼容能力：自动登记 Mockito proxy / @MockBean / @SpringBootTest 上下文 / Jackson DTO 反射 / Hibernate metamodel 必需的 RuntimeHints，让 `mvn -Pnative -DskipTests=false test` 在真实 GraalVM 21 community 中跑通绝大多数集成测试。

## What Changes

- 新增 `kuship.test.native` 子包（仅 test 作用域），包含 `NativeTestRuntimeHintsRegistrar`：自动扫描 src/main/java entity / DTO / Spring Bean，注册反射、序列化、资源 hint。
- 复用并扩展 main 的 `KuShipConsoleRuntimeHints`：将其拆分为 `EntityHintsContributor`（main） + `TestHintsContributor`（test），test 阶段自动加载两者；不影响 native binary 体积（test contributor 仅 test classpath）。
- 配置 native test 子 profile：`<profile id=native-test>` 继承 `native`，开启 `<test>true</test>` 与 `-J-XX:MaxMetaspaceSize=512m`，确保 native-image 时编译 test classes。
- 注册 Mockito `ByteBuddyAgent` 替代方案：在 native-image build 时显式 `--initialize-at-build-time=org.mockito.internal.creation.bytebuddy` 排除（无法在 native 中运行），改用 `mockito-inline-mock-maker` + `MOCKITO_NATIVE` 注解过滤策略；不能 native 化的测试明确标 `@DisabledInNativeImage`。
- 为 `@SpringBootTest`(webEnvironment=RANDOM_PORT) 集成测试在 native 下使用 `@SpringBootTest`(webEnvironment=DEFINED_PORT, port=0 -> 8081) 兜底；引入 `NativeTestPortConfiguration`。
- 新增 `scripts/native-test.sh`：一键执行 `mvn -Pnative,native-test test`，输出 pass/fail 统计与 hint 缺失诊断（解析 native-image stderr 的 `ClassNotFoundException` / `NoSuchMethodException`）。
- `kuship-console/CLAUDE.md` 增加 "Native Test 运行指南" 段落：JVM 测试 vs Native 测试矩阵、跳过策略、新增测试时的 hint 注册检查清单。
- CI 双分支：JVM 测试（已有）+ Native 测试（新增 hardening workflow stub），后者标 `continue-on-error: true` 直至所有用例稳定。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `kuship-console-app`: 在 GraalVM Native 段下追加 native 测试运行能力 + Mockito 兼容策略 + `@DisabledInNativeImage` 标记规则；明确 native test profile / port 配置 / hint 自动注册的边界。

## Impact

- **代码改动**：新增 4 个 test 工具类（NativeTestRuntimeHintsRegistrar / TestHintsContributor / NativeTestPortConfiguration / NativeTestSupport），现有 17 个 IT 测试类按需加 `@DisabledInNativeImage` 注解（约 3-5 个无法 native 化）。
- **构建管道**：`pom.xml` 新增 `native-test` profile；`scripts/native-test.sh` 新脚本；GraalVM CI 新增独立 job。
- **测试覆盖**：JVM 测试保持 102/102；Native 测试目标 ≥ 90% 用例（约 ≥ 92 用例）通过；不能跑的用例显式标记并文档化原因。
- **依赖**：新增 `mockito-inline-mock-maker`（test scope）与 `org.junit.jupiter:junit-jupiter-api`（@DisabledInNativeImage 来自 spring-test，但 inline mock maker 是 mockito 的）。
- **对生产 native binary 无任何影响**：所有改动局限于 `<scope>test</scope>` 与 `<profile>native-test</profile>`。
