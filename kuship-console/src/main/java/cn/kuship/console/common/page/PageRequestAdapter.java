package cn.kuship.console.common.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 把 query 参数 {@code page}、{@code page_size} 转 Spring {@link Pageable}。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code page} 一基（page=1 表示第一页），与 rainbond-console / Django 一致；内部转 0 基传给 Spring Data</li>
 *   <li>{@code page} 缺省 1；&lt; 1 → {@link IllegalArgumentException}（被 GlobalExceptionHandler 映射为 400）</li>
 *   <li>{@code pageSize} 缺省由 {@link PaginationProperties#defaultPageSize()} 决定；上限由 {@link PaginationProperties#maxPageSize()} 决定</li>
 * </ul>
 *
 * <p>响应输出形状由 {@code GeneralMessageResponseBodyAdvice} 处理：检测到 {@code Page<T>} 时
 * 自动注入 {@code data.list = page.content} + {@code data.bean.total = page.totalElements}，
 * 不输出顶层 {@code page}/{@code page_size} 字段（与 rainbond-console 实际响应形状一致）。
 */
@Component
public class PageRequestAdapter {

    private final PaginationProperties properties;

    public PageRequestAdapter(PaginationProperties properties) {
        this.properties = properties;
    }

    public Pageable toPageable(Integer page, Integer pageSize) {
        return toPageable(page, pageSize, Sort.unsorted());
    }

    public Pageable toPageable(Integer page, Integer pageSize, Sort sort) {
        int p = page == null ? 1 : page;
        int s = pageSize == null ? properties.defaultPageSize() : pageSize;
        validate(p, s);
        return PageRequest.of(p - 1, s, sort);
    }

    private void validate(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException(
                    "page must be >= 1 (1-based pagination), got " + page);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException(
                    "page_size must be >= 1, got " + pageSize);
        }
        int max = properties.maxPageSize();
        if (pageSize > max) {
            throw new IllegalArgumentException(
                    "page_size must be <= " + max + ", got " + pageSize);
        }
    }
}
