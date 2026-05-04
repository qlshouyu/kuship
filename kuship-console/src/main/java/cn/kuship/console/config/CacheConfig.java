package cn.kuship.console.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 内存缓存：
 *
 * <ul>
 *   <li>{@code user-team-perms}：用户在某 team 的权限码集合，TTL 60s</li>
 *   <li>{@code user-enterprise-admin}：用户在某 enterprise 是否是 admin，TTL 60s</li>
 * </ul>
 *
 * <p>权限变更时由 {@code PermService.evict*} 显式失效；TTL 兜底防止长期不一致。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(
                "user-team-perms",
                "user-enterprise-admin");
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000));
        return mgr;
    }
}
