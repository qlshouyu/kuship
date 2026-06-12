package cn.kuship.console.config;

import cn.kuship.console.common.context.TenantContextInterceptor;
import cn.kuship.console.modules.authorization.web.CheckPermsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册请求拦截器，作用于全部 /console/** 路径。
 * 次序对齐 rainbond initial()：先解析团队上下文（{@link TenantContextInterceptor}），
 * 再强制鉴权（{@link CheckPermsInterceptor}）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantContextInterceptor tenantContextInterceptor;
    private final CheckPermsInterceptor checkPermsInterceptor;

    public WebMvcConfig(TenantContextInterceptor tenantContextInterceptor,
                        CheckPermsInterceptor checkPermsInterceptor) {
        this.tenantContextInterceptor = tenantContextInterceptor;
        this.checkPermsInterceptor = checkPermsInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantContextInterceptor).addPathPatterns("/console/**").order(1);
        registry.addInterceptor(checkPermsInterceptor).addPathPatterns("/console/**").order(2);
    }
}
