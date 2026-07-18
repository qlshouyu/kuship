package cn.kuship.console.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Python {@code datetime.isoformat()} 语义的 ISO 序列化：
 * 微秒为 0 时不带小数部分；否则固定 6 位微秒（不截尾零）。
 * <p>Jackson 默认 LocalDateTime 序列化会把 {@code .185550} 截成 {@code .18555}，
 * 与 DRF 出参不一致——凡对齐 rainbond raw ISO 时间戳的字段一律经此格式化。
 */
public final class PyIsoDateTime {

    private static final DateTimeFormatter NO_FRACTION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter MICROS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private PyIsoDateTime() {
    }

    public static String iso(LocalDateTime t) {
        if (t == null) {
            return null;
        }
        return t.getNano() == 0 ? NO_FRACTION.format(t) : MICROS.format(t);
    }
}
