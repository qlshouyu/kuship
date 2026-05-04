## ADDED Requirements

### Requirement: Native test profile

构建系统 SHALL 提供 `native-test` Maven profile，继承 `native` profile 的 AOT 与 native-image 配置，但额外开启测试编译与 surefire 执行；该 profile 与默认 JVM 测试 profile 互斥。

#### Scenario: native-test profile 触发 surefire
- **WHEN** 开发者执行 `mvn -Pnative,native-test test`
- **THEN** Maven SHALL 调用 native-image 编译 test classes
- **AND** surefire SHALL 在 native binary 模式下运行 JUnit 5 测试

#### Scenario: 默认 native profile 跳过测试
- **WHEN** 开发者执行 `mvn -Pnative package`
- **THEN** Maven SHALL 跳过测试以加速 binary 构建
- **AND** 仅产出 `target/kuship-console` native 二进制

#### Scenario: JVM 测试不受影响
- **WHEN** 开发者执行 `mvn test`（不带 `-Pnative-test`）
- **THEN** Maven SHALL 使用 JVM 模式跑 surefire
- **AND** 102/102 既有 JVM 测试 SHALL 全部通过

### Requirement: NativeTestRuntimeHintsRegistrar

测试 classpath SHALL 提供 `NativeTestRuntimeHintsRegistrar`（位于 `src/test/java/cn/kuship/console/native_/`），自动扫描 `cn.kuship.console.modules.**.dto` / `cn.kuship.console.modules.**.controller` / `cn.kuship.console.common.response` 包下所有类，注册反射 hint（`MemberCategory.values()`）；该 registrar SHALL 通过 Spring Boot 4 的 `RuntimeHintsRegistrar` SPI 仅在测试运行时加载，不进入生产 native binary。

#### Scenario: registrar 自动扫描 DTO 与 controller
- **WHEN** native test 启动 ApplicationContext
- **THEN** registrar SHALL 通过 ClassPathScanningCandidateComponentProvider 扫描所述包
- **AND** 为每个发现的类调用 `hints.reflection().registerType(<class>, MemberCategory.values())`

#### Scenario: registrar 不污染生产 binary
- **WHEN** 执行 `mvn -Pnative package`（无 native-test）
- **THEN** test 工具类 SHALL 不被打包进 native binary
- **AND** binary 体积保持 ≤ 100MB（与现有 enable-graalvm-native 一致）

#### Scenario: 漏注册 hint 时给出诊断
- **WHEN** native test 因反射缺失抛 `ClassNotFoundException` 或 `NoSuchMethodException`
- **THEN** 失败堆栈 SHALL 完整保留触发的 FQCN
- **AND** `scripts/native-test.sh` SHALL grep 该堆栈并以 `[HINT-MISSING] <class>` 行汇总

### Requirement: Mockito native 兼容策略

测试依赖 SHALL 包含 `mockito-inline-mock-maker`（test scope），并在 native-image build args 中显式 `--initialize-at-run-time=org.mockito.internal.creation.bytebuddy.MockMethodAdvice`；不能 native 化的测试用例 SHALL 使用 `@DisabledInNativeImage(value="<reason>")` 显式标记。

#### Scenario: 默认 @MockBean 测试可在 native 下运行
- **WHEN** 测试类使用 `@MockBean SomeService svc` 替换 bean
- **AND** 在 native test profile 下运行
- **THEN** Mockito SHALL 通过 `mockito-inline-mock-maker` 创建 proxy
- **AND** 测试方法 SHALL 正常执行

#### Scenario: 不兼容用例显式禁用
- **WHEN** 某测试用例需要 mock final class 或 static method
- **THEN** 该用例 SHALL 标 `@DisabledInNativeImage(value="<具体不兼容原因>")`
- **AND** JVM 测试 SHALL 仍照常运行该用例

#### Scenario: 标记规则文档化
- **WHEN** 开发者新增测试时
- **THEN** `kuship-console/CLAUDE.md` SHALL 提供"何时加 `@DisabledInNativeImage` 注解"的 checklist
- **AND** checklist SHALL 至少覆盖 final class mock / static mock / 反射访问私有字段三种场景

### Requirement: AbstractIntegrationTest native 端口兜底

测试基类 SHALL 在 native image 模式（通过 `org.graalvm.nativeimage.imagecode` 系统属性检测）下使用 `webEnvironment=DEFINED_PORT` + `server.port=0`，JVM 模式下保持 `RANDOM_PORT`。

#### Scenario: native 下走 DEFINED_PORT
- **WHEN** native test 启动 `@SpringBootTest` 基类
- **AND** 系统属性 `org.graalvm.nativeimage.imagecode` 存在
- **THEN** 基类 SHALL 注入 `webEnvironment=DEFINED_PORT` 与 `server.port=0`
- **AND** 测试可通过 `@LocalServerPort` 注入 OS 分配的实际端口

#### Scenario: JVM 下保持原状
- **WHEN** 现有 102 个 JVM 测试用例运行
- **THEN** 基类 SHALL 维持 `RANDOM_PORT` 行为
- **AND** 测试结果 SHALL 与 hardening 前完全一致

### Requirement: native-test.sh 一键脚本

`scripts/native-test.sh` SHALL 提供一键命令执行 `mvn -Pnative,native-test test`，并在退出前打印 pass / fail / skipped 统计与 hint 缺失诊断；脚本检测到本地无 GraalVM 21 时 SHALL 立即报错并指引用户安装路径。

#### Scenario: 脚本检测 GraalVM 安装
- **WHEN** 用户在没有 GraalVM 的环境运行 `bash scripts/native-test.sh`
- **THEN** 脚本 SHALL 检查 `native-image --version` 返回非零
- **AND** 立即报错 `GraalVM 21 community not found, install via 'sdk install java 21.0.2-graalce'`
- **AND** 退出码非零

#### Scenario: 脚本输出统计与诊断
- **WHEN** 脚本完成 mvn 执行
- **THEN** 末尾 SHALL 打印 `[SUMMARY] passed=X failed=Y skipped=Z`
- **AND** 如有 ClassNotFoundException / NoSuchMethodException 堆栈 SHALL grep 出 `[HINT-MISSING] <class>` 行
- **AND** 退出码 SHALL 与 mvn 一致（保留 CI 失败传播）

### Requirement: native-test CI 工作流

CI workflow SHALL 提供独立的 `native-test` job，使用 `org.graalvm.buildtools/setup-graalvm@v1` action 安装 GraalVM 21 community，运行 `bash scripts/native-test.sh`；初版 SHALL 标 `continue-on-error: true`，pass rate 持续 ≥ 90% 满 2 周后移除。

#### Scenario: native-test job 不阻塞主线
- **WHEN** PR push 触发 CI
- **THEN** native-test job SHALL 标 `continue-on-error: true`
- **AND** 即使 native-test 失败 PR 仍可合并（依赖 JVM test job 必过）

#### Scenario: pass rate 监控
- **WHEN** native-test job 完成
- **THEN** job 输出 SHALL 包含 `pass_rate=<percent>` 行供下游观测
- **AND** 当 pass rate ≥ 90% 持续 2 周时 SHALL 提交 PR 移除 `continue-on-error: true`

### Requirement: native test 用例覆盖率目标

测试套件 SHALL 在 native test profile 下达到 ≥ 90% pass rate（基线 92/102），不能 native 化的用例 SHALL ≤ 10 个并集中标 `@DisabledInNativeImage`，每个均提供 `value` 字段说明原因。

#### Scenario: 92/102 通过下限
- **WHEN** 执行 `bash scripts/native-test.sh`
- **THEN** 至少 92 个用例 SHALL 通过
- **AND** 不超过 10 个用例标 `@DisabledInNativeImage`

#### Scenario: 禁用用例可审计
- **WHEN** 检视所有 `@DisabledInNativeImage` 注解
- **THEN** 每个 SHALL 包含 `value="<具体原因>"`，例如 `"final class mock not supported"` 或 `"@MockitoSettings reflection limitation"`
- **AND** 原因 SHALL 不超过 3 类（final/static/private-field）
