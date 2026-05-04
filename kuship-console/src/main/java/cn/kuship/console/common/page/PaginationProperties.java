package cn.kuship.console.common.page;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分页配置项。
 *
 * <p>请求侧 {@code page} 一基（page=1 是第一页），与 rainbond-console / Django 一致。
 * 响应侧不输出 {@code page}/{@code page_size} 字段，仅在 {@code data.bean.total} 中返回总数。
 */
@ConfigurationProperties(prefix = "kuship.pagination")
public record PaginationProperties(
        int defaultPageSize,
        int maxPageSize
) {
    public PaginationProperties {
        if (defaultPageSize <= 0) {
            defaultPageSize = 10;
        }
        if (maxPageSize <= 0) {
            maxPageSize = 200;
        }
    }
}
