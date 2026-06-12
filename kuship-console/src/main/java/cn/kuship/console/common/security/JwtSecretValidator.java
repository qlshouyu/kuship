package cn.kuship.console.common.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * 启动期强校验：非 local profile 下 {@code kuship.jwt.secret}（来自环境变量 SECRET_KEY）为空则拒绝启动，
 * 防止与 rainbond-console 密钥不同源导致 token 互不认。
 */
@Component
public class JwtSecretValidator {

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretValidator(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (!isLocal && (properties.getSecret() == null || properties.getSecret().isBlank())) {
            throw new IllegalStateException(
                    "kuship.jwt.secret (env SECRET_KEY) 未配置：非 local 环境必须与 rainbond-console 同源，拒绝启动");
        }
    }
}
