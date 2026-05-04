## Context

OpenAPI v1 已交付（Phase 12, `migrate-openapi-v1`）：
- 47 endpoint，分布在 7 个子域 controller
- 双模鉴权：`X-Internal-Token`（内部服务）+ `Authorization: Bearer <PAT>`（外部 admin）
- 响应风格独立（`{detail, code}` 形式 + HTTP 状态码与业务码一致）
- `OpenApiAuthFilter` 仅匹配 `/openapi/**`
- `OpenApiExceptionHandler` 包名级 `@RestControllerAdvice`

GraalVM Native（Phase 13, `enable-graalvm-native`）已铺路：
- `KuShipConsoleRuntimeHints` 自动扫 entity 反射
- Hibernate 字节码增强已关闭
- BouncyCastle 在 build-time 初始化

Native 测试通道（Phase 14, `harden-native-tests`）：
- `NativeTestRuntimeHintsRegistrar` 自动扫 controller / DTO
- `scripts/native-test.sh` 一键 + hint 缺失诊断
- `mockito-extensions` 配置 + `@DisabledInNativeImage` 规则

业界现状（2026-05）：
- **Springdoc 2.7.0** 是首个声明完整 Spring Boot 4 / Jakarta EE 11 兼容的版本
- Springdoc 自带 GraalVM native-image hint（`META-INF/native-image/org.springdoc/springdoc-openapi-starter-webmvc-ui/native-image.properties`）
- swagger-ui webjar 静态资源大约 8-10MB（HTML/CSS/JS/字体），native binary 包含会增重
- prod 部署普遍只暴露 `/openapi/v3/api-docs`（JSON 规范）给 SDK 生成器，关闭 swagger-ui

## Goals / Non-Goals

**Goals:**
- 自动从 47 个 v1 controller 生成 OpenAPI 3 JSON（`/openapi/v3/api-docs`）
- 提供 swagger-ui 页面（`/openapi/swagger-ui/index.html`）含 Try-It-Out + 双鉴权输入框
- 生成的 JSON 包含两种 securityScheme：`InternalToken`（apiKey in header `X-Internal-Token`）+ `BearerAuth`（http bearer）
- 文档仅覆盖 `cn.kuship.console.modules.openapi.v1.**` 包，不暴露 console UI 后端 controller
- dev/local 默认开启，prod 默认关闭，`kuship.openapi.docs.enabled` 全局开关覆盖
- `mvn -Pnative,native-test test` 通过；`mvn -Pnative package` 构建的 native binary 在 dev profile 启动时 swagger-ui 可访问
- 不影响现有 105/105 测试用例

**Non-Goals:**
- 给 console UI 后端（`/console/**`）生成 OpenAPI 文档（只面向第三方的 v1 公开端点）
- 完整的 endpoint 描述 / example / response schema 注释（首版只生成自动结构，注释规范化作为后续 hardening）
- ReDoc UI（仅 Swagger UI）
- API client 代码生成（OpenAPI 3 JSON 用户自行用 openapi-generator）
- API 版本协商（`/openapi/v2/...` 不规划）

## Decisions

### 1. springdoc-openapi-starter-webmvc-ui vs starter-webmvc-api

**选择**：`springdoc-openapi-starter-webmvc-ui` 2.7.0+

**为什么**：
- `starter-webmvc-api` 仅生成 JSON，无 UI；用户接入新平台前需要 UI 验证鉴权
- UI 包 webjars 在 prod 关闭后不会启动 servlet handler（Springdoc 检测 `springdoc.swagger-ui.enabled=false` 跳过 webjar mapping）
- 单 starter 包含所有，依赖管理更简单

**替代方案**：仅 starter-webmvc-api → prod 体积更小但 dev 体验差，pass

### 2. 路径前缀：/openapi/v3/api-docs + /openapi/swagger-ui/**

**选择**：JSON 在 `/openapi/v3/api-docs`，UI 在 `/openapi/swagger-ui/index.html`

**为什么**：
- 与现有 OpenAPI v1 路径前缀 `/openapi/v1/*` 同根，便于反向代理（Nginx）单一规则
- Springdoc 默认是 `/v3/api-docs` 与 `/swagger-ui.html`（无前缀），通过 `springdoc.api-docs.path` / `springdoc.swagger-ui.path` 显式覆盖
- console UI 后端在 `/console/**`，不会冲突

**替代方案**：使用默认根路径 → 与未来可能的 `/v3/...` 业务端点冲突，不可取

### 3. 包扫描分组：仅 v1 controller

**选择**：用 `springdoc.packagesToScan=cn.kuship.console.modules.openapi.v1` 限定扫描范围

**为什么**：
- console UI 后端的 100 个 `/console/*` controller 不应该出现在公开文档里（用户不应直接调用）
- 显式 packagesToScan 比 pathsToMatch 更可靠（pathsToMatch 在 Spring Boot 4 中存在偶发匹配问题）

**替代方案**：用 `pathsToMatch=/openapi/v1/**` → 等价但 Springdoc 内部仍扫描全部 controller，启动慢

### 4. 鉴权 securityScheme 显式声明

**选择**：在 `SpringDocConfig` 用 `OpenAPI` bean 显式注册两个 SecurityScheme：

```java
.components(new Components()
    .addSecuritySchemes("InternalToken",
        new SecurityScheme().type(APIKEY).in(HEADER).name("X-Internal-Token"))
    .addSecuritySchemes("BearerAuth",
        new SecurityScheme().type(HTTP).scheme("bearer")))
```

**为什么**：
- Spring Security `permitAll` 让 Springdoc 看不到鉴权要求，必须人工注入
- 用户在 swagger-ui 输入凭据后能直接 Try-It-Out
- 双方案并列让用户根据场景选（CI/CD 用 InternalToken，admin 命令行用 PAT）

### 5. dev / prod 启用策略：两层开关

**选择**：
- 第一层：`springdoc.swagger-ui.enabled=true|false`（标准 Springdoc 配置）
- 第二层：`kuship.openapi.docs.enabled=${SPRING_PROFILES_ACTIVE=dev|local}`（项目自定义）
- 实际行为：当 `kuship.openapi.docs.enabled=false` 时同步关闭 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled`

**为什么**：
- 让运维只通过 `kuship.openapi.docs.enabled=false` 一键关闭，无需理解 Springdoc 内部配置
- 保留 Springdoc 原生开关给高级用户

### 6. GraalVM Native 兼容：体积折中

**选择**：默认 native build 包含 swagger-ui webjar；但通过 `-Pnative-no-swagger` profile 可剥离（生产部署可选）

**为什么**：
- 默认体积重 8-10MB 不影响绝大多数场景
- 极端体积敏感（边缘节点 / IoT）可走 `-Pnative-no-swagger` 不打 webjar
- 试图让 swagger-ui 通过 CDN 远程加载（不打入 binary）的方案不可行：CDN 在内网部署不可访问

### 7. OpenApiAuthFilter skip 名单

**选择**：在 `OpenApiAuthFilter` 头部新增 `SKIP_PATHS` 集合，包含：
- `/openapi/v3/api-docs`（含 trailing slash）
- `/openapi/v3/api-docs/swagger-config`
- `/openapi/swagger-ui` / `/openapi/swagger-ui/`（前缀匹配 → swagger-ui/index.html / swagger-ui/* 静态资源）

**为什么**：
- 文档浏览不应要求鉴权（与 README / GitHub 公开文档同等）
- Try-It-Out 操作还是会调实际端点，仍需真实鉴权
- skip 列表显式比泛 antMatcher 更安全

## Risks / Trade-offs

- **[Risk]** Springdoc 2.7.0 与 Spring Boot 4.0.6 / Jackson 3 兼容性可能有未知问题
  → **Mitigation**：先在 dev profile 实测启动 + JSON 端点返回；遇兼容问题降级到 starter-webmvc-api（仅 JSON）保底
- **[Risk]** Springdoc 反射 hint 不全导致 native binary 启动失败
  → **Mitigation**：Springdoc 自带 `META-INF/native-image/...` 已覆盖核心；额外手动注册 `OpenAPI` / `Components` / `SecurityScheme` 类型
- **[Risk]** dev 开启后被未授权访问浏览 47 个端点目录（信息泄露）
  → **Mitigation**：JSON 仅返回 endpoint shape 不返回业务数据；CLAUDE.md 明确 prod 必关；Production Hardening 文档加显著提示
- **[Risk]** swagger-ui webjar 在 GraalVM native 下静态资源 404
  → **Mitigation**：`-H:IncludeResources=META-INF/resources/webjars/swagger-ui/.*` 在 native profile 显式注册；测试断言根 webjar 资源（`favicon.ico`）可加载
- **[Trade-off]** 体积 +8-10MB 换 dev 体验：可接受；prod 用 `-Pnative-no-swagger` 兜底

## Migration Plan

不涉及生产部署变更。开发流程：
1. 现有 `mvn test` 保持必过门禁
2. dev/local 默认开启 swagger-ui，访问 `http://localhost:8080/openapi/swagger-ui/index.html` 验证
3. prod 默认关闭，运维需要时通过 `KUSHIP_OPENAPI_DOCS_ENABLED=true` 环境变量临时启用
4. native binary 验证：`bash scripts/native-build.sh` → `./target/kuship-console -Dkuship.openapi.docs.enabled=true` 后 curl 验证

回滚：移除 Springdoc 依赖 + 删 SpringDocConfig；OpenApiAuthFilter skip 列表保留无害；`/console/**` 与 `/openapi/v1/**` 业务功能 0 影响。

## Open Questions

- 是否给每个 OpenAPI controller 加 `@Operation(summary, description)` / `@Parameter` / `@ApiResponse` 注释？倾向 NO（首版）：47 endpoint 注释化工作量大，留作单独 hardening `enrich-openapi-annotations`
- 是否暴露给 console UI 后端文档？倾向 NO：UI 后端是内部 BFF（Backend-for-Frontend），不应被第三方调用，公开文档反而误导用户
- ReDoc 是否需要？倾向 NO：Swagger UI Try-It-Out 已满足；ReDoc 静态文档可由用户自行用 OpenAPI JSON 生成
