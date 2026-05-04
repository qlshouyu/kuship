package cn.kuship.console.config;

import cn.kuship.console.common.context.TenantContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 {@link TenantContextInterceptor} 让 path variable 中的
 * {@code team_name}/{@code region_name} 自动写入 RequestContext。
 *
 * <p>关于 trailing slash 兼容：Spring 6 已移除 {@code PathMatchConfigurer.setUseTrailingSlashMatch}，
 * 本项目采用「在 controller 注解里同时列出两种路径」的方案（见 HealthzController）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantContextInterceptor tenantContextInterceptor;

    public WebMvcConfig(TenantContextInterceptor tenantContextInterceptor) {
        this.tenantContextInterceptor = tenantContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextInterceptor)
                .addPathPatterns("/console/**", "/openapi/**");
    }
}
