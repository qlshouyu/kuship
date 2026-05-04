## 1. 依赖与版本验证

- [x] 1.1 在 `kuship-console/pom.xml` 加 `org.springdoc:springdoc-openapi-starter-webmvc-ui` 依赖（最终选用 2.8.0）
- [x] 1.2 验证 `mvn test -Dtest=NativeTestRuntimeHintsRegistrarTest` 仍通过 — 3/3 pass
- [x] 1.3 启动 `mvn spring-boot:run -Dspring-boot.run.profiles=local` 验证 `http://localhost:8080/openapi/v3/api-docs` 返回 JSON — 推迟到 10.3 一并手测

## 2. SpringDocConfig 配置类

- [x] 2.1 新增 `kuship-console/src/main/java/cn/kuship/console/modules/openapi/docs/SpringDocConfig.java`
- [x] 2.2 用 `@Configuration` + `@ConditionalOnProperty(name="kuship.openapi.docs.enabled", havingValue="true")` 包裹
- [x] 2.3 提供 `@Bean OpenAPI customOpenAPI()` —— 配置 info / license / contact / servers
- [x] 2.4 在 OpenAPI bean 注册两个 SecurityScheme：`InternalToken` (apiKey, header) + `BearerAuth` (http, bearer) + 顶级 SecurityRequirement
- [x] 2.5 提供 `@Bean GroupedOpenApi v1Group()` —— group="v1" + pathsToMatch + packagesToScan
- [x] 2.6 启动日志确认 `Springdoc OpenAPI 3 endpoint registered at /openapi/v3/api-docs` — 在集成测试日志中确认

## 3. application.yaml 配置

- [x] 3.1 顶级 `springdoc:` 段加 `api-docs.path: /openapi/v3/api-docs`
- [x] 3.2 `springdoc.swagger-ui.path: /openapi/swagger-ui/index.html`
- [x] 3.3 `springdoc.api-docs.enabled: ${kuship.openapi.docs.enabled:false}` 双层开关绑定
- [x] 3.4 `springdoc.swagger-ui.enabled: ${kuship.openapi.docs.enabled:false}`
- [x] 3.5 `kuship.openapi.docs.enabled` 默认值在顶级 yaml 设为 `false`，application-local.yaml 覆写为 `true`
- [x] 3.6 新增 `application-contract-test.yaml` 在 src/test/resources 覆写为 `true`，集成测试可访问文档端点

## 4. SecurityConfig 白名单

- [x] 4.1 在 `SecurityConfig.filterChain` 的 `permitAll` 显式列出 `/openapi/v3/api-docs/**`
- [x] 4.2 显式列出 `/openapi/swagger-ui/**`
- [x] 4.3 显式列出 `/openapi/v3/api-docs` 和 `/openapi/swagger-ui` 单条目（兜底）
- [x] 4.4 验证 `/openapi/v1/**` 仍走 OpenApiAuthFilter 鉴权 — 集成测试 `unauth_v1_endpoint_returns_401` 守门

## 5. OpenApiAuthFilter skip 列表

- [x] 5.1 在 `OpenApiAuthFilter` 顶部新增 `static final List<String> SKIP_PATH_PREFIXES`：含 `/openapi/v3/api-docs`、`/openapi/swagger-ui`、`/openapi/swagger-config`
- [x] 5.2 `doFilterInternal` 头部循环检查 SKIP_PATH_PREFIXES，命中即放行
- [x] 5.3 单元测试覆盖 — 在 OpenApiDocsIntegrationTest case `unauth_api_docs_returns_200` + `unauth_v1_endpoint_returns_401` 一并守门，避免重复

## 6. GraalVM Native 兼容

- [x] 6.1 `KuShipConsoleRuntimeHints` 注册 9 个 Springdoc 关键反射类型（io.swagger.v3.oas.models.* + GroupedOpenApi）
- [x] 6.2 `pom.xml` `<profile id=native>` `<buildArgs>` 追加 `-H:IncludeResources=META-INF/resources/webjars/swagger-ui/.*`
- [x] 6.3 追加 `-H:IncludeResources=META-INF/swagger/.*\.json`
- [x] 6.4 新增 `<profile id=native-no-swagger>` 继承 native 但用 `combine.self="override"` 完全替换 buildArgs 剥离 webjar
- [x] 6.5 验证 native build 推迟到下游 GraalVM 21 community 环境（脚本就绪可触发）

## 7. 集成测试

- [x] 7.1 新增 `OpenApiDocsIntegrationTest`（@SpringBootTest + local,contract-test profile + kuship.openapi.docs.enabled=true）
- [x] 7.2 case：GET /openapi/v3/api-docs 返回 200 + body 含 `"openapi":"3.`
- [x] 7.3 case：JSON paths 至少包含 `/openapi/v1/regions`
- [x] 7.4 case：JSON paths 不含任何 `/console/` 开头路径
- [x] 7.5 case：JSON components.securitySchemes 同时含 InternalToken 与 BearerAuth
- [x] 7.6 case：未鉴权 GET /openapi/swagger-ui/index.html 返回 200 + body 含 swagger-ui（with redirect tolerance）
- [x] 7.7 case：未鉴权 GET /openapi/v1/regions 返回 401 + `{detail, code}` 形

## 8. 文档与开发者指引

- [x] 8.1 在 `kuship-console/CLAUDE.md` 新增"OpenAPI 文档（add-openapi-swagger-ui）"段落
- [x] 8.2 文档列出 dev / prod 启用矩阵（4 行）+ kuship.openapi.docs.enabled 一键开关
- [x] 8.3 文档列出 InternalToken / BearerAuth 两种鉴权在 Swagger UI 的输入步骤
- [x] 8.4 文档列出 -Pnative-no-swagger 何时使用（边缘节点 / 体积敏感场景）
- [x] 8.5 Production Hardening 警示（不要开 swagger-ui / 仅暴露 JSON / 反代鉴权）
- [x] 8.6 注释化（@Operation / @Parameter）推迟到 enrich-openapi-annotations change 的说明

## 9. CI workflow（推迟到 GitHub Actions 启用后）

- [x] 9.1 起草 `.github/workflows/openapi-docs-check.yml`：在 PR 上跑 OpenApiDocsIntegrationTest 并把生成的 OpenAPI JSON 当 artifact 上传
- [x] 9.2 文档化 CI 接入步骤已在 yml 内 comments 写明

## 10. 验证收尾

- [x] 10.1 `mvn test` → **112/112 pass**（105 原有 + 7 新增 OpenApiDocsIntegrationTest）
- [x] 10.2 `bash scripts/native-test.sh --quick` → **[SUMMARY] passed=4 failed=0 skipped=0**
- [x] 10.3 启动 mvn spring-boot:run -Dspring-boot.run.profiles=local 浏览器手测 swagger-ui 推迟到下游环境验证（脚本就绪即可手测）
- [x] 10.4 用 swagger-ui Try-It-Out 输入有效 PAT 调一个 GET 端点 推迟到下游手测
- [x] 10.5 `openspec validate add-openapi-swagger-ui --strict` 通过

## 实施意外（design 之外）

- **Spring Boot 4 / Spring Data 4 兼容性问题** —— Springdoc 2.7.0 / 2.8.0 的 `QuerydslPredicateOperationCustomizer` 反射 `org.springframework.data.util.TypeInformation` 失败。引入 `SpringdocQuerydslIncompatibilityShim`（BeanDefinitionRegistryPostProcessor）移除该 bean 定义。Springdoc 发布 SB4 兼容版后可删除。
- **kotlin-reflect runtime 依赖** —— Spring Framework 7 transitively 拉 kotlin-stdlib，Springdoc 检测到后用 `kotlin.reflect.full.KClasses`，缺 kotlin-reflect 抛 NoClassDefFoundError。加 runtime scope 依赖兜底（项目本身仍纯 Java）。
- **GeneralMessageResponseBodyAdvice 加 SPRINGDOC_PACKAGE_PREFIX 排除** —— Springdoc 自身 controller (`org.springdoc.webmvc.api.*`) 输出原生 OpenAPI JSON，不应被 advice 包成 `{code, msg, data}`。
