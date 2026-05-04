## Context

13 阶段路线终点：把 kuship-console 从 fat jar 编译为 GraalVM Native Image。这不是新增功能，是构建系统升级。

为什么前 12 阶段就要为这一刻铺路？回顾路线设计中的"native 友好"决策：

| 阶段 | 决策 | 为何 native 友好 |
|------|------|-------------------|
| 第 6 阶段 application-core | env 仅本地写不调 region | 避免 RestClient 复杂代理 |
| 第 8 阶段 app-runtime | 不上 SSE / WebSocket，事件用同步轮询 | 避开 reactor-netty 在 native image 兼容性问题 |
| 第 11 阶段 misc MCP | 仅 HTTP JSON-RPC，无 SSE 推流 | 同上 |
| 第 12 阶段 OpenAPI v1 | Springdoc Swagger UI 推迟 hardening | springdoc 生成 schema 用大量反射 |
| 全局 | Caffeine 而非 Redis | 减少 native image 反射注册 |
| 全局 | Hibernate ddl-auto=validate | 不上字节码增强（spring-boot 4 native 默认禁用） |
| 全局 | JJWT vs nimbus-jose-jwt | JJWT API 更少反射 |
| 全局 | Lombok 编译期生成 | runtime 零反射 |
| 全局 | MapStruct 编译期生成 | 同上 |

涉及外部依赖的 native 兼容性矩阵（截至 2026-Q1）：

| 依赖 | native 兼容 | 备注 |
|------|--------------|------|
| Spring Boot 4.0.6 | ✅ first-class | Spring AOT 自动生成 hint |
| Hibernate 6.x | ✅ 需关字节码增强 | `hibernate.jakarta.persistence.bytecode.strategy = none` |
| MySQL Connector/J 8.x | ✅ reachability metadata | 已在仓库 |
| Spring Data JPA | ✅ | Spring AOT 处理 |
| Spring Security 6 | ✅ | Spring AOT 处理 |
| Caffeine | ✅ reachability metadata | |
| JJWT 0.12.x | ✅ | 已修过 native bug |
| BouncyCastle 1.78+ | ✅ | 加载 provider 用反射，需 hint（reachability metadata 提供） |
| MapStruct | ✅ | 编译期生成 |
| Lombok | ✅ | 编译期生成 |
| io.kubernetes:client-java | ⚠️ 部分功能受限 | 主要复杂 watch / informer 流；本项目仅用同步 API |
| HttpComponents 5 | ✅ | RestClient 底层 |
| Flyway | ✅ baseline only | 我们不出 DDL，仅 baseline |

涉及代码量：现有 ~16500 LOC Java + 122 spec requirements 不变；本 change 仅添加构建配置 + 元数据文件。

## Goals / Non-Goals

**Goals:**
- `mvn -Pnative -DskipTests native:compile` 在本地能成功生成 `target/kuship-console` 二进制（Linux/macOS）。
- 启动后跑 `curl localhost:8080/console/healthz` 返回 200 OK。
- 启动时间在 macOS M2 / Linux x86_64 上**< 2 秒**（fat jar 是 8s，目标降到 1/4）。
- RSS 运行时内存 **< 300MB**（fat jar 是 ~600MB）。
- 通过 1 个 native smoke test 验证 contract layer + 1 个业务 endpoint 可调（不要求 101 用例全跑，因为部分用例用了 Mockito 反射 mock）。
- 提供 `Dockerfile.native` 多阶段构建：第 1 阶段 GraalVM 21-community + Maven build；第 2 阶段 distroless 跑二进制。
- 集成到 standalone 镜像（可选，fall back fat-jar 路径保留）。
- CI 矩阵：amd64 + arm64 双架构 native build（GitHub Actions）。

**Non-Goals:**
- 不实现 native test 跑全部 101 用例（部分用 Mockito mock region，需独立 hint 配置；分批迁移到独立 hardening change `harden-native-tests`）。
- 不切换 standalone 镜像默认构建路径（保留 fat-jar 作为 prod 兼容 fallback；用户主动 `--build-arg NATIVE=true` 才用 native binary）。
- 不实现 Profile-Guided Optimization（PGO）—— 需要 GraalVM Enterprise，本项目用 community edition。
- 不实现 Static linking with musl libc（最小镜像极致），保持 distroless dynamic linking。
- 不解决 io.kubernetes client watch 流的 native 兼容性（rke2 阶段用，本阶段不阻塞 console 主功能）。
- 不实现 Springdoc Swagger UI native 集成（推迟到 `add-openapi-swagger-ui` hardening）。

## Decisions

### 决策 1：Maven profile 隔离 native 构建

```xml
<profile>
    <id>native</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>0.10.4</version>
                <executions>
                    <execution>
                        <id>build-native</id>
                        <goals><goal>compile-no-fork</goal></goals>
                        <phase>package</phase>
                    </execution>
                </executions>
                <configuration>
                    <imageName>kuship-console</imageName>
                    <buildArgs>
                        <buildArg>--enable-url-protocols=http,https</buildArg>
                        <buildArg>-H:+AddAllCharsets</buildArg>
                        <buildArg>-H:IncludeResources=db/migration/.*\.sql</buildArg>
                        <buildArg>-H:IncludeResources=application.*\.yaml</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

不污染默认 profile —— 普通 `mvn package` 仍然产 fat jar。仅 `mvn -Pnative package` 走 AOT + native build。

### 决策 2：Spring AOT enable

`application.yaml` 默认 `spring.aot.enabled=false`；profile 化：
```yaml
spring:
  aot:
    enabled: ${SPRING_AOT:false}
```
native 构建时 plugin 自动设 `spring.aot.enabled=true`，跑 AOT processor 提前生成 hint。普通 fat jar 启动不受影响。

### 决策 3：手写 RuntimeHints

虽然 Spring AOT 能自动发现绝大多数反射点，但有 3 类需要手动加 hint：

```java
@ImportRuntimeHints(KuShipConsoleRuntimeHints.class)
@Configuration
public class NativeConfig {}

public class KuShipConsoleRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        // 1. 全部 JPA Entity（确保 Hibernate 反射访问）
        hints.reflection().registerType(UserInfo.class, MemberCategory.values());
        hints.reflection().registerType(Tenants.class, MemberCategory.values());
        // ... ~58 entity（用 ClassPathScanner 自动列出更优）

        // 2. Jackson 序列化的 record 类（共 ~15 个 DTO）
        hints.reflection().registerType(JwtClaims.class, MemberCategory.values());
        hints.reflection().registerType(OperationLogSummary.class, MemberCategory.values());

        // 3. yaml / sql 资源（已通过 IncludeResources 处理）
        hints.resources().registerPattern("db/migration/*.sql");
    }
}
```

实际 entity 列表用 reflection scanner 从 `cn.kuship.console.modules.**.entity` 扫描所有 `@Entity` 类，自动注册避免手写 58 行。

### 决策 4：Hibernate 字节码增强关闭

```yaml
spring:
  jpa:
    properties:
      hibernate.jakarta.persistence.bytecode.strategy: none
      hibernate.bytecode.use_reflection_optimizer: false
```
原因：Hibernate 默认用 ByteBuddy 在运行时增强 entity 类（实现 lazy load proxy 等），native image 不允许运行时类生成。代价：
- ~5% 运行时性能损失（lazy load 退化为 eager load）
- 项目目前所有关联都是手动 `findByXxxId(id)` 模式，未用 `@ManyToOne fetch=LAZY`，影响为零

### 决策 5：Resources 显式注册

native image 默认仅打包 `.class` 文件。资源文件（YAML / SQL / properties / static asset）需显式注册：

```json
// META-INF/native-image/cn.kuship/kuship-console/resource-config.json
{
  "resources": {
    "includes": [
      {"pattern": "application.*\\.yaml"},
      {"pattern": "db/migration/.*\\.sql"},
      {"pattern": "META-INF/spring.factories"},
      {"pattern": "META-INF/services/.*"}
    ]
  }
}
```

或在 plugin 配置中用 `IncludeResources` build arg（决策 1 已含）。两种等价；本 change 用 plugin build arg 简化。

### 决策 6：Dockerfile 多阶段构建

```dockerfile
# Stage 1: build
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -Pnative -DskipTests package

# Stage 2: runtime
FROM gcr.io/distroless/base-debian12
COPY --from=builder /build/target/kuship-console /app/kuship-console
EXPOSE 8080
ENTRYPOINT ["/app/kuship-console"]
```

镜像大小：约 80MB（distroless base 22MB + binary 60MB 含静态链接 jvm 部分）。
对比 fat jar 镜像：openjdk:21-jre-slim 200MB + jar 150MB = 350MB。

### 决策 7：Profile 化 prod / native / dev 矩阵

5 种启动方式：

| 启动方式 | 启动时间 | 内存 | 用途 |
|----------|----------|------|------|
| `java -jar fat.jar` | ~8s | ~600MB | dev / 已有 prod |
| `java -jar -Dspring.profiles.active=prod fat.jar` | ~9s | ~600MB | prod fat-jar |
| `./kuship-console` (native, dev profile) | ~1.5s | ~250MB | local 验证 native |
| `./kuship-console -Dspring.profiles.active=prod` | ~1.5s | ~250MB | prod native binary |
| `docker run kuship-console-native` | ~2s | ~280MB | k8s 部署 native |

13 阶段路线完成后默认推荐 prod 用 native binary + docker。

### 决策 8：CI 双架构 build

```yaml
# .github/workflows/native-build.yml
strategy:
  matrix:
    os: [ubuntu-22.04, ubuntu-22.04-arm64]
steps:
  - uses: graalvm/setup-graalvm@v1
    with:
      java-version: '21'
      distribution: 'graalvm-community'
  - run: mvn -Pnative -DskipTests package
  - uses: actions/upload-artifact@v4
    with:
      name: kuship-console-${{ matrix.os }}
      path: kuship-console/target/kuship-console
```

两个架构产物分别命名 `kuship-console-amd64` / `kuship-console-arm64`，作为 release artifact 上传。standalone 镜像构建时按当前 host arch 选用对应 binary。

### 决策 9：Smoke test 而非完整测试

完整 101 测试用例中约 30 个用 `@MockitoBean` mock region API。Mockito 在 native image 中需要额外 hint 注册，复杂度高。本 change 仅要求 1 个 smoke test 通过：

```java
// kuship-console/src/test/java/.../native_/NativeSmokeTest.java
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class NativeSmokeTest {
    @Autowired MockMvc mvc;
    @Test void healthz_returns200() throws Exception {
        mvc.perform(get("/console/healthz")).andExpect(status().isOk());
    }
}
```

`mvn -Pnative -Dtest=NativeSmokeTest test` 验证 native image 在测试环境可运行；其他 100 个用例继续走 fat-jar 测试不变。完整 native test coverage 留作 `harden-native-tests` change。

### 决策 10：standalone 镜像默认仍 fat-jar

`standalone/Dockerfile` 增加 build arg `NATIVE=false`（默认）；用户主动 `--build-arg NATIVE=true` 时切换到多阶段 native build。这样：
- 现有 standalone 用户不受影响（fat-jar 路径继续可用）
- 想用 native 优化的用户能一键切换
- 渐进式 native 推广，避免一次性激进切换

未来若 native build 经过几个版本验证稳定，可在独立 change `make-native-default` 中翻转默认值。

## Risks / Trade-offs

- **[Risk]** native build 在 macOS / Linux 上可能失败（GraalVM 21 community 在 ARM64 偶发链接问题） → Mitigation：CI 用 ubuntu-22.04 amd64 + arm64 双 matrix；本地优先支持 macOS arm64 + Linux amd64；其他平台用 docker buildx。
- **[Risk]** RuntimeHints 不全 → 启动时 NoClassDefFoundError → Mitigation：用 graalvm-reachability-metadata 仓库（已在 plugin 默认开启）；本项目额外手动注册 entity 反射；首次跑 smoke test 失败时 `--enable-monitoring=heapdump` 调试。
- **[Risk]** Hibernate ddl-auto=validate 在 native 模式下报错 → Mitigation：决策 4 关闭字节码增强；启动时 ddl-validate 仍跑（不需要字节码增强即可）。
- **[Risk]** JJWT HS256 用 SecureRandom 在 native image 下首次启动较慢 → Mitigation：build arg 加 `--initialize-at-build-time=org.bouncycastle`；java.security 默认 fast path（NativePRNG）。
- **[Risk]** 测试 mock 反射不兼容 → Mitigation：决策 9 仅 smoke test；其他 100 测试用例继续 fat-jar；hardening 单独 change 升级。
- **[Risk]** Swagger UI 仍未集成（决策 6） → Mitigation：第 12 阶段已留作 hardening；本 change 不阻塞。
- **[Trade-off]** Hibernate 字节码增强 5% 性能损失 → 用户量当前 < 1k DAU，CPU 不成瓶颈；hardening 改写 entity 关联为手动 join 也行。
- **[Trade-off]** native binary 单架构（Linux 上 build 的 binary 不能在 macOS 跑）→ CI 双架构 build；docker buildx 跨架构。

## Migration Plan

阶段 A：pom.xml 添加 `native` profile + plugin 配置（决策 1 + 决策 2）
阶段 B：手写 `KuShipConsoleRuntimeHints.java` + `META-INF/native-image/...` 元数据文件（决策 3 + 决策 5）
阶段 C：调整 `application.yaml`（Hibernate 字节码增强 + spring.aot.enabled）（决策 4）
阶段 D：本地 `mvn -Pnative -DskipTests package` 验证 native build 成功
阶段 E：写 `NativeSmokeTest.java` + `mvn -Pnative -Dtest=NativeSmokeTest test` 验证（决策 9）
阶段 F：写 `Dockerfile.native` + `scripts/native-build.sh`（决策 6）
阶段 G：CI workflow 双架构 build（决策 8）
阶段 H：可选 —— `standalone/Dockerfile` 加 `NATIVE` build arg（决策 10）
阶段 I：文档（CLAUDE.md + README.md 更新启动方式矩阵）

阶段 D 是关键里程碑——成功就证明前 12 阶段的 native 友好决策都对了；失败需逐项排查 hint 缺失。

## Open Questions

- **(Q1)** 是否要 PGO（Profile-Guided Optimization）？需 GraalVM Enterprise；本阶段用 community edition，跳过。
- **(Q2)** 是否要 musl libc 静态链接？最小镜像极致；本阶段用 distroless dynamic linking 简化（80MB vs 60MB 差 20MB，可接受）。
- **(Q3)** standalone 镜像默认是否切换到 native？决策 10 保持默认 fat-jar；未来评估稳定性后翻转。
- **(Q4)** CI 矩阵成本：每次 PR 都 build native 耗时 ~10min；建议改为仅在 release tag 触发，PR 仍用 fat-jar 测试。

不阻塞实施。
