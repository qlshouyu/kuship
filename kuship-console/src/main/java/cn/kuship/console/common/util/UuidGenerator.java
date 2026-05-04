package cn.kuship.console.common.util;

import java.util.UUID;

/** 与 rainbond `www/utils/crypt.py` 的 make_uuid / make_tenant_id 行为对齐：去掉 UUID 中的 `-` 得到 32 位字符。 */
public final class UuidGenerator {

    private UuidGenerator() {}

    public static String makeUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String makeTenantId() {
        return makeUuid();
    }
}
