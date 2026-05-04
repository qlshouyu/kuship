package cn.kuship.console.config.native_;

import jakarta.persistence.Entity;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * GraalVM Native Image 反射 / 资源 / 代理元数据注册。
 *
 * <p>策略：
 * <ol>
 *   <li>用 {@link ClassPathScanningCandidateComponentProvider} 自动扫描 {@code cn.kuship.console.modules.**.entity}
 *       下所有 {@link Entity} 类，注册全成员反射访问（Hibernate 通过反射读字段）</li>
 *   <li>资源文件 {@code application*.yaml} / {@code db/migration/*.sql} 通过 native-maven-plugin
 *       buildArgs 中的 {@code -H:IncludeResources=...} 已注册，此处不重复</li>
 *   <li>record 类（如 {@code JwtClaims}, {@code OperationLogSummary} 等 Jackson 序列化目标）
 *       由 Spring AOT 自动发现 + 注册，此处不手动列举</li>
 * </ol>
 *
 * <p>本类通过 {@link cn.kuship.console.config.native_.NativeConfig#KUSHIP_HINTS @ImportRuntimeHints}
 * 关联到 Spring 上下文。
 */
public class KuShipConsoleRuntimeHints implements RuntimeHintsRegistrar {

    private static final String ENTITY_BASE_PACKAGE = "cn.kuship.console.modules";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.findCandidateComponents(ENTITY_BASE_PACKAGE).forEach(bd -> {
            String className = bd.getBeanClassName();
            if (className == null) return;
            try {
                Class<?> entityClass = Class.forName(className, false, classLoader);
                hints.reflection().registerType(entityClass, MemberCategory.values());
            } catch (ClassNotFoundException e) {
                // 扫描期遇到不可加载类（typically PluginEnabled 编译期 stub）忽略
            }
        });
        // resource patterns（与 native-maven-plugin buildArgs 等价；显式列出便于阅读）
        hints.resources().registerPattern("db/migration/*.sql");
        hints.resources().registerPattern("application*.yaml");
        hints.resources().registerPattern("application*.yml");
        hints.resources().registerPattern("META-INF/native-image/**");

        // add-openapi-swagger-ui: Springdoc 自带 META-INF/native-image/org.springdoc/... 已经覆盖
        // 核心反射，但 GroupedOpenApi 的 builder 链 + io.swagger.v3.oas.models 的关联 model 类在
        // Spring AOT 上下文初始化阶段会被反射触达，显式补 hint 防 native binary 启动炸开。
        // swagger-ui webjar 静态资源由 native-maven-plugin <buildArgs> 中的 IncludeResources 处理。
        for (String fqcn : new String[]{
                "io.swagger.v3.oas.models.OpenAPI",
                "io.swagger.v3.oas.models.Components",
                "io.swagger.v3.oas.models.info.Info",
                "io.swagger.v3.oas.models.info.Contact",
                "io.swagger.v3.oas.models.info.License",
                "io.swagger.v3.oas.models.security.SecurityScheme",
                "io.swagger.v3.oas.models.security.SecurityRequirement",
                "io.swagger.v3.oas.models.servers.Server",
                "org.springdoc.core.models.GroupedOpenApi"
        }) {
            hints.reflection().registerType(
                    TypeReference.of(fqcn),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }

        // add-mcp-sse: JSON-RPC records + 8 MVP tool classes. SseEmitter is a Spring core type,
        // already covered by Spring AOT defaults. Tools rely on Spring DI; class itself just needs
        // reflection so that GraalVM keeps it instantiable when AOT-detected as @Component.
        for (String fqcn : new String[]{
                "cn.kuship.console.modules.misc.mcp.protocol.JsonRpcRequest",
                "cn.kuship.console.modules.misc.mcp.protocol.JsonRpcResponse",
                "cn.kuship.console.modules.misc.mcp.protocol.JsonRpcError",
                "cn.kuship.console.modules.misc.mcp.tool.impl.GetCurrentUserTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.ListRegionsTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.ListTeamsTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.ListAppsTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.ListComponentsTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.GetComponentDetailTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.GetComponentPodsTool",
                "cn.kuship.console.modules.misc.mcp.tool.impl.GetComponentLogsTool"
        }) {
            hints.reflection().registerType(
                    TypeReference.of(fqcn),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }

        // add-gray-release: GrayReleaseStatus enum + ServiceMappingEntry record + AttributeConverter
        // + 4 个 DTO record。GrayReleaseRecord（@Entity）由上面的 ENTITY_BASE_PACKAGE 扫描自动覆盖。
        // ServiceMappingsConverter 被 Hibernate 反射实例化，需显式 hint。
        for (String fqcn : new String[]{
                "cn.kuship.console.modules.grayrelease.entity.GrayReleaseStatus",
                "cn.kuship.console.modules.grayrelease.entity.ServiceMappingEntry",
                "cn.kuship.console.modules.grayrelease.entity.ServiceMappingsConverter",
                "cn.kuship.console.modules.grayrelease.dto.CreateGrayReleaseRequest",
                "cn.kuship.console.modules.grayrelease.dto.UpdateGrayRatioRequest",
                "cn.kuship.console.modules.grayrelease.dto.GrayRollbackRequest",
                "cn.kuship.console.modules.grayrelease.dto.GrayReleaseRecordDto"
        }) {
            hints.reflection().registerType(
                    TypeReference.of(fqcn),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }

        // add-aliyun-sms: aliyun SDK 没有自带 META-INF/native-image hint。tea 框架在
        // SendSms 调用时会反射 request/response model 字段做序列化；显式注册避免 native binary
        // 启动 AliyunSmsProvider 时炸开。仅在 prod (kuship.sms.provider=aliyun) 才会被实际访问。
        for (String fqcn : new String[]{
                "com.aliyun.dysmsapi20170525.Client",
                "com.aliyun.dysmsapi20170525.models.SendSmsRequest",
                "com.aliyun.dysmsapi20170525.models.SendSmsResponse",
                "com.aliyun.dysmsapi20170525.models.SendSmsResponseBody",
                "com.aliyun.teaopenapi.models.Config",
                "com.aliyun.tea.TeaModel",
                "com.aliyun.tea.TeaRequest",
                "com.aliyun.tea.TeaResponse"
        }) {
            hints.reflection().registerType(
                    TypeReference.of(fqcn),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }
}
