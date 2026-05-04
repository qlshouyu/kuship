## Context

Phase 13 (`enable-graalvm-native`) 已交付：
- `pom.xml` `native` profile（`spring.aot.enabled=true` + native-maven-plugin）
- `KuShipConsoleRuntimeHints` 自动扫描 `cn.kuship.console.modules.**.entity` 注册 `MemberCategory.values()` 反射
- `application.yaml` 顶级开 `spring.aot.enabled` + Hibernate 关字节码增强
- `Dockerfile.native` 多阶段 distroless build
- 1 个 `NativeSmokeTest`（仅断言 `/console/healthz` 返回 200）

剩余 102 测试用例中：
- ~85 为 `@SpringBootTest` 集成测试（账户/集群/应用/组件/插件/市场/OpenAPI）
- ~17 为 `@WebMvcTest` 切片测试（Controller validation / 异常映射）

GraalVM Native Image 与 Mockito、@SpringBootTest random port、Spring AOT 上下文重启的相互作用复杂：
- **Mockito** 通过 ByteBuddy 在运行时生成 proxy 类，native image 关闭运行时类生成
- **@SpringBootTest(webEnvironment=RANDOM_PORT)** 启动多 ApplicationContext，native image 仅一次性 AOT 上下文
- **@MockBean** 替换 bean 时反射访问 BeanFactory 内部，需注册细粒度 hint
- **Hibernate metamodel** 在 test 时通过 `EntityManagerFactory` 反射所有 entity，需为每个 entity 注册 `MemberCategory.DECLARED_FIELDS`

业界现状（2026-05）：
- Spring Boot 4.0.6 + GraalVM 21 community 对 `@SpringBootTest` 在 native 下提供 **partial support**（DEFINED_PORT 工作良好，RANDOM_PORT 在 PR-44982 后基本可用但偶发）
- Mockito 5.x + `mockito-inline-mock-maker` + native image 自 Mockito 5.13 起官方支持但 `@MockBean` proxy 仍要求显式 `--initialize-at-run-time=ByteBuddyMockMaker`

## Goals / Non-Goals

**Goals:**
- 让 `mvn -Pnative,native-test test` 在 GraalVM 21 community + JDK 21 下跑通 ≥ 90% 现有测试（92/102 用例最低）
- 自动注册所有 entity / DTO / Spring controller bean 的反射 hint，无需手写 entity 列表
- 不能 native 化的测试用 `@DisabledInNativeImage` 显式标记，写明原因
- 提供 `scripts/native-test.sh` 一键脚本，输出 pass/fail + hint 缺失诊断
- 不影响生产 native binary 体积（test 工具类与 hint 仅在 test classpath）
- 不影响现有 JVM 测试（`mvn test` 仍 102/102 通过）

**Non-Goals:**
- 重写测试用例为 native-friendly（保持现有 JUnit 5 + Spring 风格）
- 解决 `@MockBean` 在 native 中的所有边界（mockito-inline-mock-maker 是上限）
- CI Native job 立即必须通过（首版 `continue-on-error: true`，后续逐步收紧）
- 与 Mockito 4.x 兼容（已用 5.x，mockito-inline 是 default）
- 在没有 GraalVM 安装时本地跑 native test（脚本应明确报错）

## Decisions

### 1. Mockito 策略：mockito-inline-mock-maker + 显式 disable 不兼容用例

**选择**：升级到 `mockito-inline-mock-maker`（Mockito 5.x 默认），`@MockBean`/`@SpyBean` 测试用例预期能在 native 下工作；不能跑的（如 mock final class、static mock）显式标 `@DisabledInNativeImage` 并附 `value="<reason>"`。

**为什么**：
- `mockito-inline-mock-maker` 通过 Mockito 自带的 `MockMethodAdvice` + agent attach，相比 default `byte-buddy` mock maker 在 native 下更稳定
- Mockito 5.13+ 官方支持 native image（要求显式 `--initialize-at-run-time` 配置）
- 通过 `@DisabledInNativeImage` 而非删除/重写：保持 JVM 与 Native 测试的源代码一致

**替代方案**：
- 全删 Mockito 改 `MockMvc` raw → 重写工作量太大
- Spring Boot 4 内置 `org.springframework.test.context.bean.override.mockito.MockitoBean`（替代 @MockBean）→ 已是新 API，但本项目暂未全面迁移；本次不强制（hardening 范围）

### 2. RuntimeHints 自动注册：扫描而非手写

**选择**：扩展现有 `KuShipConsoleRuntimeHints`，新增 `cn.kuship.console.modules.**.dto`（所有 DTO）和 `cn.kuship.console.common.response`（ApiResult / GeneralMessage）的反射 hint 自动扫描。test 阶段额外加载 `NativeTestRuntimeHintsRegistrar`（位于 `src/test/java/.../native_/`）扫描 controller bean 反射 + Mockito proxy 接口。

**为什么**：
- 手写白名单会随 entity / DTO 增长而漂移（已 58 entity / 65 DTO）
- ClassPathScanningCandidateComponentProvider 可在 build-time（AOT）和 test 启动时一次扫描全部
- test 工具类隔离在 `<scope>test</scope>` 与 `src/test/java/`，不进 native binary

**替代方案**：
- 用 `META-INF/native-image/<group>/<artifact>/reflect-config.json` 静态文件 → 维护成本高
- GraalVM tracing agent (`-agentlib:native-image-agent`) → 需要先跑一遍 JVM 测试录制，工作流复杂；可作为补充验证手段

### 3. @SpringBootTest 端口策略：DEFINED_PORT 兜底

**选择**：测试基类 `AbstractIntegrationTest` 检测 `org.graalvm.nativeimage.imagecode` 系统属性，native 下注入 `@SpringBootTest(webEnvironment=DEFINED_PORT)` + `server.port=0`（OS 动态端口），JVM 下保持现有 `RANDOM_PORT`。

**为什么**：
- Spring Boot 4 在 native 下 RANDOM_PORT 仍偶发 ApplicationContext 启动失败（GH-44982 / GH-46201 跟踪中）
- `server.port=0` 配合 `@LocalServerPort` 注入仍能拿到分配的端口
- 兜底不破坏 JVM 测试

**替代方案**：
- 锁定固定端口（如 8081）→ 并行测试冲突
- 等 Spring 修 RANDOM_PORT bug → 不可控

### 4. Native test profile 与 JVM test 完全分离

**选择**：`<profile id=native-test>` 继承 `native` 但额外开 `<test>true</test>`（默认 native profile 跳过 test 加快 build），并通过 `<surefireArgLine>--enable-native-access=ALL-UNNAMED</surefireArgLine>` 启用 native access 警告抑制。

**为什么**：
- `mvn -Pnative package` 用户期望快速产 binary，不应跑测试
- `mvn -Pnative,native-test test` 显式分离 CI 任务

### 5. Hint 缺失自动诊断

**选择**：`scripts/native-test.sh` 在 `mvn` 输出末尾 grep `ClassNotFoundException|NoSuchMethodException|MissingResourceException`，将抛错的 FQCN 列表打印为 `[HINT-MISSING] <class>` 行。

**为什么**：
- GraalVM 错误堆栈深，开发者难定位是哪个 hint 漏了
- 自动化 grep 在 CI 中也能直接 fail 时给出补 hint 的精确指引

**替代方案**：
- 改写测试 reporter → 工作量大且侵入

## Risks / Trade-offs

- **[Risk]** Spring Boot 4.0.6 + Mockito 5.x + GraalVM 21 community 三方组合本就存在已知不稳定（GH issue 多）
  → **Mitigation**：`continue-on-error: true` 让 CI 不阻塞主流程；显式 `@DisabledInNativeImage` 标注问题用例并 link issue
- **[Risk]** ClassPathScanningCandidateComponentProvider 扫描全 modules.** 包可能注册过多反射 hint 让 native image 体积膨胀
  → **Mitigation**：仅 test 阶段 scope，不影响生产 binary；main 的 `KuShipConsoleRuntimeHints` 保持只扫描 entity 包
- **[Risk]** `mockito-inline-mock-maker` 与现有 `@MockBean` 行为细微差异（如 final method mock）
  → **Mitigation**：先跑 JVM 测试确认不破现有 102 用例；切换不动测试源码
- **[Risk]** 开发者新加测试时忘记同步 native test profile
  → **Mitigation**：CLAUDE.md 加 checklist；NativeTestRuntimeHintsRegistrar 自动扫描兜底
- **[Trade-off]** 92/102 而非 102/102：放弃极少数 `@MockBean` final class 用例的 native 覆盖，换取 hardening 任务可在 1 个 sprint 内收敛

## Migration Plan

不涉及生产部署变更。开发流程：
1. 现有 JVM 测试 `mvn test` 保持唯一必过门禁
2. PR 合并前可选跑 `bash scripts/native-test.sh`（需本地 GraalVM 21）
3. CI 主 workflow 仍跑 `mvn test`；新增独立 native-test job 标 `continue-on-error: true`
4. 当 Native pass rate ≥ 90% 持续 2 周，移除 `continue-on-error`

回滚：删除 native-test profile 与 test 工具类，回退到只有 NativeSmokeTest 的状态；不影响生产 binary。

## Open Questions

- 是否要把 `@MockBean` 全部迁移到 Spring Boot 4 新 `@MockitoBean`？倾向 NO：超出 hardening 范围，单独一次 `migrate-mockito-bean` 改造更合适
- 是否在 native test profile 中开启 `@TestcontainersWithReuse`？倾向 NO：当前测试用 in-memory（H2 / 未启用真 MySQL container），保持兼容
