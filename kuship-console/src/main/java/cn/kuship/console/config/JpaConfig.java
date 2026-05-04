package cn.kuship.console.config;

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JPA 配置：
 *
 * <ul>
 *   <li>显式声明 {@link PhysicalNamingStrategyStandardImpl}，与 application.yaml 双重保险，
 *       确保实体上 {@code @Column(name="...")} 严格按字面落到 SQL，不做 camelCase → snake_case 自动转换</li>
 *   <li>{@code @EnableJpaAuditing} 在本 change 不开启；后续业务 change 引入 BaseEntity 时再启用</li>
 * </ul>
 *
 * <p>注意：本 change 不引入任何 entity，因此启动时 Hibernate metadata 集合为空，
 * {@code ddl-auto=validate} 不会触发任何 schema 校验失败。
 */
@Configuration
public class JpaConfig {

    @Bean
    public PhysicalNamingStrategyStandardImpl physicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }
}
