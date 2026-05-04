## 1. Maven 工程骨架

- [x] 1.1 在仓库根创建 `kuship-console/` 目录
- [x] 1.2 编写 `kuship-console/pom.xml`：`groupId=cn.kuship`、`artifactId=kuship-console`、`version=0.1.0-SNAPSHOT`、`packaging=jar`、Java 21、`spring-boot-starter-parent` 4.0.6
- [x] 1.3 在 pom.xml 加依赖：`spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`spring-boot-starter-security`、`spring-boot-starter-actuator`、`spring-boot-starter-validation`
- [x] 1.4 在 pom.xml 加依赖：`mysql-connector-j`、`flyway-core`、`flyway-mysql`
- [x] 1.5 在 pom.xml 加依赖：`io.jsonwebtoken:jjwt-api/impl/jackson`（jjwt 0.12.x）
- [x] 1.6 在 pom.xml 加依赖：`com.querydsl:querydsl-jpa:jakarta` + `querydsl-apt:jakarta`（apt 配在 build-helper 或 maven-compiler-plugin annotationProcessorPaths）
- [x] 1.7 在 pom.xml 加依赖：`org.mapstruct:mapstruct` + `mapstruct-processor`（同样进 annotationProcessorPaths）
- [x] 1.8 在 pom.xml 加依赖：`io.kubernetes:client-java`（标记 `<optional>true</optional>`，仅占位，rke2 阶段才正式使用）
- [x] 1.9 在 pom.xml 加依赖：`org.apache.httpcomponents.client5:httpclient5`（仅占位，region client 阶段使用）
- [x] 1.10 在 pom.xml 加依赖：`org.projectlombok:lombok`（`<scope>provided</scope>` + 进 annotationProcessorPaths）
- [x] 1.11 配置 `maven-compiler-plugin`：`source/target=21`、`parameters=true`、`annotationProcessorPaths` 包含 lombok / mapstruct / querydsl-apt
- [x] 1.12 配置 `spring-boot-maven-plugin`：repackage 默认；不在本 change 启用 native-maven-plugin
- [x] 1.13 在 `kuship-console/` 创建 `.gitignore`：`target/`、`*.iml`、`.idea/`、`.vscode/`、`application-local.yaml`、`HELP.md`

## 2. 包结构与启动类

- [x] 2.1 创建 `kuship-console/src/main/java/cn/kuship/console/KuShipConsoleApplication.java`，加 `@SpringBootApplication`
- [x] 2.2 创建空目录占位（带 `.gitkeep` 或 `package-info.java`）：`config/`、`common/response/`、`common/exception/`、`common/util/`、`infrastructure/jpa/`、`infrastructure/region/`、`infrastructure/k8s/`、`modules/`、`healthz/`
- [x] 2.3 在 `modules/` 下用 `package-info.java` 列出预期业务模块（account、team、application、region、plugin、market、misc 等）作为后续 change 的占位说明

## 3. 配置文件

- [x] 3.1 创建 `kuship-console/src/main/resources/application.yaml`：默认 profile 不指向真实数据库，包含 `spring.datasource.url=${DB_URL:}`、`spring.datasource.username=${DB_USERNAME:}`、`spring.datasource.password=${DB_PASSWORD:}` 占位；`spring.jpa.hibernate.ddl-auto=validate`；命名策略指向 `PhysicalNamingStrategyStandardImpl`；Flyway `baseline-on-migrate=true`、`baseline-version=0`、`locations=classpath:db/migration`
- [x] 3.2 创建 `kuship-console/src/main/resources/application-local.yaml`（**不会进 git**）：`spring.datasource.url=jdbc:mysql://127.0.0.1:3306/console`、`username=root`、`password=123456`、`spring.jpa.show-sql=true`
- [x] 3.3 创建 `kuship-console/src/main/resources/application-local.yaml.example`（**进 git**）：与 `application-local.yaml` 同结构但密码占位为 `<your-password>`，作为团队上手模板
- [x] 3.4 创建 `kuship-console/src/main/resources/db/migration/.gitkeep`，并在该目录添加 `README.md` 说明：本目录刻意不放业务表 SQL；schema 由 rainbond-console（Django）一方管控；如有 kuship 独有的辅助表才允许新增 V*__ 文件
- [x] 3.5 在 `application.yaml` 中配置 actuator：`management.endpoints.web.exposure.include=health,info,metrics`、`management.endpoint.health.show-details=when-authorized`

## 4. Spring 配置类

- [x] 4.1 创建 `config/SecurityConfig.java`：声明一个 `@Bean SecurityFilterChain`，对所有路径 `permitAll`，关闭 csrf，关闭默认登录页；为后续 JWT 接入留好链路注释
- [x] 4.2 创建 `config/JpaConfig.java`：显式声明 `PhysicalNamingStrategyStandardImpl`（保险起见，即使 application.yaml 也配了）；预留 `@EnableJpaAuditing` 注释（本 change 不开）
- [x] 4.3 创建 `config/WebMvcConfig.java`：实现 `WebMvcConfigurer` 作为扩展点。trailing slash 兼容采用「在 controller 注解里显式列出两种路径」的方案（Spring 6 已移除全局 trailing slash 匹配开关）

## 5. 响应包装工具类

- [x] 5.1 创建 `common/response/ApiResult.java`：record，字段顺序 `code: int`、`msg: String`、`msg_show: String`、`data: Map<String,Object>`；用 `@JsonProperty("msg_show")` + `@JsonPropertyOrder` 显式控制 JSON 字段名与顺序
- [x] 5.2 创建 `common/response/GeneralMessage.java`：静态工厂 `ok()`、`ok(Map<String,Object> bean)`、`okList(List<?> list)`、`ok(String msgShow, Map<String,Object> extras)`、`okWithExtras(...)`、`error(int code, String msg, String msgShow)`；内部用 `LinkedHashMap` 组装 `data` 节点保证至少包含 `bean`/`list` 两个键，并保留插入顺序
- [x] 5.3 创建 `common/exception/ServiceHandleException.java`：业务异常基类，字段 `int code`、`String msg`、`String msgShow`；本 change 不挂 ControllerAdvice，仅作为占位

## 6. Healthz 端点

- [x] 6.1 创建 `healthz/HealthzController.java`：`@RestController`，`@GetMapping({"/console/healthz", "/console/healthz/"})`，返回 `GeneralMessage.ok("OK", Map.of())`
- [x] 6.2 单元测试 `HealthzControllerTest`：使用 `@WebMvcTest(HealthzController.class)` + `@AutoConfigureMockMvc(addFilters = false)`（避免 @WebMvcTest 切片下手动装配 HttpSecurity bean），断言响应为 200、Content-Type=application/json、JSON 结构 `code=200`、`msg="success"`、`msg_show="OK"`、`data.bean={}`、`data.list=[]`
- [x] 6.3 单元测试新增一例：请求 `/console/healthz/`（带尾斜杠）也返回 200 且响应体一致

## 7. Dockerfile

- [x] 7.1 创建 `kuship-console/Dockerfile`：multi-stage（builder: `maven:3.9-eclipse-temurin-21` + 缓存 `dependency:go-offline` + `mvn package -DskipTests`；runtime: `eclipse-temurin:21-jre`）；`EXPOSE 8080`；`ENTRYPOINT ["java","-jar","/app/kuship-console.jar"]`
- [x] 7.2 创建 `kuship-console/.dockerignore`：忽略 `target/`、`.git/`、`.idea/`、`*.md`、`application-local.yaml` 等

## 8. 文档

- [x] 8.1 创建 `kuship-console/README.md`：包含模块定位、技术栈版本、本地启动步骤（前置：Java 21 / Maven 3.9+ / 本地 MySQL `console` 库）、`mvn clean package`、`mvn spring-boot:run -Dspring-boot.run.profiles=local`、Docker 构建命令、`curl http://localhost:8080/console/healthz` 验证示例
- [x] 8.2 创建 `kuship-console/CLAUDE.md`：包结构图、共享数据库约束、URL 路径策略、响应格式契约、技术栈版本、迁移路线图引用

## 9. 根仓库联动

- [x] 9.1 更新仓库根 `CLAUDE.md` 的目录结构段落，把 `kuship-console/` 的描述补充为更具体的范围说明，并指向 `kuship-console/CLAUDE.md`
- [x] 9.2 在仓库根 `.gitignore` 加上 `kuship-console/target/` 与 `kuship-console/**/application-local.yaml` 的全局忽略

## 10. 验收

- [x] 10.1 在仓库根执行 `mvn -pl kuship-console clean package`，BUILD SUCCESS，项目代码 0 warning，2/2 测试通过（local 实测：10.97s 完成；唯一剩余 warning 来自 surefire 的 byte-buddy/JVM 21 兼容提示，非项目代码）
- [x] 10.2 启动应用 `java -jar target/kuship-console.jar --spring.profiles.active=local`：Hibernate 与 Hikari 成功连接 `console` 库，无 schema validate 异常（实测 Spring Boot v4.0.6 / Spring v7.0.7 / Hibernate ORM 7.2.12.Final / MySQL 8.0.46 / Tomcat 11.0.21）
- [x] 10.3 `curl http://localhost:8080/console/healthz` 返回 `{"code":200,"msg":"success","msg_show":"OK","data":{"bean":{},"list":[]}}`、Content-Type=application/json
- [x] 10.4 `curl http://localhost:8080/console/healthz/` 返回同样结果（trailing slash 兼容）
- [x] 10.5 `curl http://localhost:8080/actuator/health` 返回 `{"groups":["liveness","readiness"],"status":"UP"}`
- [x] 10.6 `docker build -t kuship-console:dev kuship-console/` 构建成功（multi-stage：maven 镜像内 11.5s 完成 BUILD SUCCESS，runtime 镜像基于 eclipse-temurin:21-jre）
- [x] 10.7 review 检查清单：`application-local.yaml` 在 `git status --ignored` 显示为 `!!`（忽略状态）；`pom.xml` 依赖与 tasks 列出范围一致；`SecurityConfig` 关闭 csrf/formLogin/httpBasic 且 `anyRequest().permitAll()`；`application.yaml` `ddl-auto=validate`

## 实施期间发现的关键事实（供后续 change 参考）

- **Spring Boot 4 模块化拆分**：`spring-boot-test-autoconfigure` 在 4.x 只剩 `jdbc/json` 子包。`@WebMvcTest`、`@AutoConfigureMockMvc`、`MockMvcBuilders` 等 web 切片测试支持搬到独立 artifact `spring-boot-starter-webmvc-test`，包路径变为 `org.springframework.boot.webmvc.test.autoconfigure.*`。后续业务 change 写测试时直接按这个路径导入即可。
- **`@WebMvcTest` 切片下未装配 `HttpSecurity` bean**：导致依赖 `HttpSecurity` 的自定义 `SecurityFilterChain` 注入失败。统一对策：测试类加 `@AutoConfigureMockMvc(addFilters = false)` 跳过过滤器（业务 controller 的 permission 矩阵在集成测试或专门的 security-only 测试中验证）。
- **本机 Maven settings.xml 含失效 mirror**：`~/.m2/settings.xml` 配置了 `depend.iflytek.com`（公司内网仓库）当前 DNS 不通，覆盖 `central` 时直接构建会失败。本机构建临时方案是 `-s` 指定旁路 settings；docker build 不受影响。后续可在 README 补充内网/外网两套 settings 切换说明。
- **Java 实际版本**：本机 JDK 是 GraalVM Oracle 21.0.11 —— 与未来 Phase 13 `enable-graalvm-native` 的 native-image 编译同源，对早期 native 化探索友好。
