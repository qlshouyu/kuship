package cn.kuship.console.infrastructure.region.api;

/**
 * 工具类：未实现的 region API method 统一抛出异常。
 *
 * <p>每个资源域的接口（除 {@link TenantOperations} 已完整落地外）都在 method 默认实现中调
 * {@link #unsupported(String)} 抛错，提示由哪个后续 change 落地。
 */
public final class UnsupportedRegionOperations {

    private UnsupportedRegionOperations() {
    }

    public static <T> T unsupported(String implementingChange) {
        throw new UnsupportedOperationException(
                "not yet implemented; will be filled in by " + implementingChange);
    }
}
