## Why

Phase 12 (`migrate-openapi-v1`) 落地了 47 个 `/openapi/v1/*` 端点（region / user / admin / team / enterprise / app / other 7 子域），用于 grctl CLI、第三方平台对接、CI/CD GitOps 流水线。但目前接入方只能靠阅读 controller 源码或抓包来发现端点，缺少标准化的 API 文档：

- 第三方开发者无法离线浏览 endpoint 列表 / 参数 / 响应 shape
- 没有"Try It Out"交互能力，验证 PAT/Internal-Token 鉴权要写脚本
- 不能给开放平台 / 集成商提供 OpenAPI 3.x JSON 规范文件做客户端代码生成

Springdoc OpenAPI 3 是 Spring Boot 生态默认 OpenAPI 文档方案，但前 12 阶段刻意未集成（避免污染 console UI 后端 + 不确定 GraalVM Native 兼容性）。第 13 阶段（`enable-graalvm-native`）已为 Springdoc 兼容性做了充分铺垫（Hibernate 字节码增强关闭 / RuntimeHints 自动扫描 / AOT 启用）；第 14 阶段（`harden-native-tests`）建立了 native 测试通道。本次 hardening 把 Springdoc 接到 `/openapi/**` 路径前缀，仅暴露 v1 公开端点，不污染 console UI 后端。

## What Changes

- 引入 `org.springdoc:springdoc-openapi-starter-webmvc-ui`（Spring Boot 4 兼容版 ≥ 2.7.0，需验证）
- 新增 `cn.kuship.console.modules.openapi.docs.SpringDocConfig`：扫描 `cn.kuship.console.modules.openapi.v1` 包，自动生成 `/openapi/v3/api-docs` JSON + `/openapi/swagger-ui/index.html` UI
- 应用安全规范：声明 `X-Internal-Token` (apiKeyAuth) + `Authorization: Bearer <PAT>` (bearerAuth) 两种鉴权方案，前端 UI 内置 Try-It-Out 输入框
- 排除 `/console/**`（console UI 后端）：Springdoc grouping 仅扫 `cn.kuship.console.modules.openapi.v1.**` 包路径
- `SecurityConfig` 把 `/openapi/v3/api-docs/**` 与 `/openapi/swagger-ui/**` 加到白名单（permitAll，Filter 不拦截）
- `OpenApiAuthFilter` 加 path skip 列表：`/openapi/v3/api-docs/**` / `/openapi/swagger-ui/**` 不走鉴权（让未登录用户能浏览文档）
- prod profile 默认关闭 Swagger UI（`springdoc.swagger-ui.enabled=false`），dev / local / contract-test profile 默认开启；通过 `kuship.openapi.docs.enabled` 全局开关覆盖
- `kuship-console/CLAUDE.md` 新增"OpenAPI 文档"段落（访问路径 / 启用条件 / 安全注释规范）
- GraalVM Native 兼容：把 Springdoc 反射 hint 加到 `KuShipConsoleRuntimeHints`；在 `Dockerfile.native` 触发的 build 中启用 Springdoc swagger-ui webjar 资源 includeResources
- 跑 `bash scripts/native-test.sh` 验证 native 模式下 `/openapi/v3/api-docs` 返回有效 JSON

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `kuship-console-app`: 在 OpenAPI v1 段下增加 Swagger UI / OpenAPI 3 JSON 自动生成 / 双鉴权方案展示 / dev-only 启用策略 / GraalVM Native 兼容能力。

## Impact

- **代码改动**：1 个新 `@Configuration` 类（SpringDocConfig）；`OpenApiAuthFilter` 加 skip 列表；`SecurityConfig` 加白名单；`KuShipConsoleRuntimeHints` 注册 Springdoc 反射 hint。
- **构建管道**：`pom.xml` 新增 1 个依赖；`native-maven-plugin` buildArgs 追加 `-H:IncludeResources=META-INF/resources/webjars/swagger-ui/.*` / `application.json` 等。
- **运行时**：dev/local 启动多出 `/openapi/v3/api-docs` (~50KB JSON) + `/openapi/swagger-ui/index.html`（webjar 静态资源）；prod 默认关闭，体积影响 0。
- **依赖增加**：springdoc-openapi-starter-webmvc-ui (~250KB JAR + transitive swagger-core / swagger-models / swagger-ui webjar)；native binary 体积约增 8-12MB（webjar 静态资源）。可通过 prod 关闭 Swagger UI 但保留 JSON 端点把额外体积压到 ~200KB。
- **安全**：dev 默认开启 + Filter skip 让 dev 环境匿名可访问端点目录（无敏感数据，但要在 Production Hardening 文档中提示生产关闭）。
