package cn.kuship.console.common.util;

import cn.kuship.console.common.exception.ServiceHandleException;

/**
 * region_name 必填校验，对齐 rainbond {@code RegionTenantHeaderView.initial}：
 * 这些 team 域端点缺 {@code region_name}（query/cookie）时，在 handler 执行前返回
 * 裸信封 {@code {code:400, msg:"", msg_show:"请求参数不全"}}。
 */
public final class RegionScope {

    private RegionScope() {
    }

    /** region_name 为空（null/空白）→ 抛裸 400「请求参数不全」。 */
    public static void require(String regionName) {
        if (regionName == null || regionName.isBlank()) {
            throw ServiceHandleException.paramIncomplete();
        }
    }
}
