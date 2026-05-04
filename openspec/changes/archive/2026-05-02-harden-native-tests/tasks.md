## 1. 测试依赖与 Mockito 兼容

- [x] 1.1 在 `kuship-console/pom.xml` 测试依赖块新增 `org.mockito:mockito-inline-mock-maker`（test scope） — 实施时改为 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 写入 `mock-maker-inline`（Mockito 5.x 已默认走 inline，artifact 已 deprecated），等价方案
- [x] 1.2 验证现有 17 个 IT 测试在 JVM 模式 `mvn test` 仍 102/102 通过 — 实测 105/105（含 3 个新增 NativeTestRuntimeHintsRegistrarTest）
- [x] 1.3 检视所有 `@MockBean` 用例，识别需要 mock final class / static method / 私有字段的用例 — 扫描结果：5 个 `@MockitoBean` 全部 mock interface；0 个 `Mockito.mockStatic`；**预期 0 个用例需要 `@DisabledInNativeImage`**
- [x] 1.4 为不兼容用例添加 `@DisabledInNativeImage(value="<具体原因>")` 注解 — 0 个用例命中（同 1.3）
- [x] 1.5 把禁用清单（用例名 + 原因）整理到 `kuship-console/CLAUDE.md` 的 Native Test 段落 — 已在 "Native Test 运行指南" 写明 "当前代码库 0 个用例命中"

## 2. RuntimeHints test contributor

- [x] 2.1 新增 `kuship-console/src/test/java/cn/kuship/console/native_/NativeTestRuntimeHintsRegistrar.java`
- [x] 2.2 实现 `RuntimeHintsRegistrar` 接口，扫描 controller / DTO / common.response — controller 扫描扩展到 `cn.kuship.console`（覆盖 healthz/contract test），DTO 与 common.response 按设计扫描
- [x] 2.3 用 `ClassPathScanningCandidateComponentProvider` 扫描，对每个发现的类调用 `hints.reflection().registerType(<class>, MemberCategory.values())`
- [x] 2.4 注册 Mockito proxy 必需的 hint — 通过 `TypeReference.of(fqcn)` 字符串方式注册（避免 Class.forName 触发链接 NoClassDefFoundError）
- [x] 2.5 通过 `META-INF/spring/aot.factories` 将 registrar 接到 Spring AOT SPI（仅 test classpath）— `src/test/resources/META-INF/spring/aot.factories`
- [x] 2.6 编写 `NativeTestRuntimeHintsRegistrarTest` 单元测试断言扫描到的类数量 ≥ 100 — 实测基线 179 个

## 3. AbstractIntegrationTest native 端口兜底

- [x] 3.1 检视现有测试基类 — 0 个测试用 RANDOM_PORT/@LocalServerPort（全部 MockMvc）；不需要批量改 17 个 IT 类
- [x] 3.2 新增/修改基类 — 改为新增轻量 `NativeTestSupport.isNativeImageRuntime()` helper，未来需要真端口的测试可调用
- [x] 3.3 通过系统属性 `org.graalvm.nativeimage.imagecode` 判断当前是否 native 运行时 — 在 NativeTestSupport 实现
- [x] 3.4 把现有 17 个 IT 测试类的基类替换或加 `@TestPropertySource` — 不需要（无端口绑定测试），future-proofing scaffolding 已就位
- [x] 3.5 新增 `src/test/resources/application-native-test.yaml`：包含 `server.port: 0` 与 native 专用日志级别

## 4. Maven native-test profile

- [x] 4.1 在 `kuship-console/pom.xml` 现有 `<profile id=native>` 之外新增 `<profile id=native-test>`
- [x] 4.2 native-test profile 继承 `<aotEnabled>true</aotEnabled>` 并显式 `<skipTests>false</skipTests>`
- [x] 4.3 native-maven-plugin `<buildArgs>` 追加 `--initialize-at-run-time=org.mockito.internal.creation.bytebuddy.MockMethodAdvice`
- [x] 4.4 buildArgs 追加 `-H:IncludeResources=application-native-test.*\.yaml`
- [x] 4.5 surefire 配置 `<argLine>--enable-native-access=ALL-UNNAMED</argLine>` 抑制 native access 警告
- [x] 4.6 验证 `mvn -Pnative,native-test help:active-profiles` 显示两个 profile 同时激活 — 通过 `bash scripts/native-test.sh` 间接验证（`[SUMMARY] passed=105 failed=0 skipped=0`）

## 5. native-test.sh 一键脚本

- [x] 5.1 新增 `scripts/native-test.sh`，加 `chmod +x`
- [x] 5.2 脚本头部检测 `native-image --version`，缺失时报错并退出非零 — 实测 `PATH=/usr/bin:/bin bash scripts/native-test.sh` 报错正确
- [x] 5.3 调用 `mvn -Pnative,native-test test -s /tmp/kuship-mvn-settings.xml` 走 aliyun 镜像 — 通过 `KUSHIP_MVN_SETTINGS` 环境变量可覆盖
- [x] 5.4 在 mvn 输出末尾 grep `ClassNotFoundException|NoSuchMethodException|MissingResourceException` 提取 FQCN
- [x] 5.5 输出 `[HINT-MISSING] <class>` 汇总行 + `[SUMMARY] passed=X failed=Y skipped=Z`
- [x] 5.6 保留 mvn 退出码作为脚本退出码 — `exit "$MVN_EXIT"`

## 6. 文档与开发者指引

- [x] 6.1 在 `kuship-console/CLAUDE.md` 新增"Native Test 运行指南"段落
- [x] 6.2 文档列出何时加 `@DisabledInNativeImage` 的 3 类规则（final / static / private-field）
- [x] 6.3 文档列出新增测试时的 hint 注册检查清单（5 步）
- [x] 6.4 在 `kuship-console/CLAUDE.md` 启动方式矩阵中追加"Native Test"行（5 → 6 种）

## 7. CI 工作流

- [x] 7.1 起草 `.github/workflows/native-test.yml`（amd64 + arm64 双架构 matrix）
- [x] 7.2 文档化 CI 接入步骤：使用 `graalvm/setup-graalvm@v1` action + `bash scripts/native-test.sh`
- [x] 7.3 标 `continue-on-error: true`，并在文档中写明 pass rate ≥ 90% 持续 2 周后的收紧策略

## 8. 验证与收尾

- [x] 8.1 本地运行 `mvn test` → 验证 JVM 测试通过 — **105/105 pass**
- [x] 8.2 本地（GraalVM 已装）运行 `bash scripts/native-test.sh` → **[SUMMARY] passed=105 failed=0 skipped=0**
- [x] 8.3 没有 GraalVM 的环境下验证脚本正确报错并退出非零 — 实测 `PATH=/usr/bin:/bin bash scripts/native-test.sh` 报错并退出码非零
- [x] 8.4 把 `NativeTestRuntimeHintsRegistrar` 扫描到的类数量记录到 CLAUDE.md — 基线 **179 个类型** 写入 "Native Test 运行指南" 段落
- [x] 8.5 `openspec validate harden-native-tests --strict` 通过
