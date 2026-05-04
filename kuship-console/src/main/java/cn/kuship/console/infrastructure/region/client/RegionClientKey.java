package cn.kuship.console.infrastructure.region.client;

/**
 * Region client 缓存 key：{@code (enterpriseId, regionName)} 二元组。
 *
 * <p>{@code enterpriseId} 可为 {@code null}（与 {@code region_info.enterprise_id} 字段允许 null 一致）。
 */
public record RegionClientKey(String enterpriseId, String regionName) {
}
