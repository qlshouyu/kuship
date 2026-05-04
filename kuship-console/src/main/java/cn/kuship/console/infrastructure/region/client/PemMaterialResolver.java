package cn.kuship.console.infrastructure.region.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把 {@code region_info} 表里的 {@code ssl_ca_cert}/{@code cert_file}/{@code key_file} 字段值
 * 统一解析为 PEM 字符串。
 *
 * <p>判定逻辑（与 rainbond-console Python 端 {@code Configuration} 类一致）：
 * <ul>
 *   <li>以 {@code /} 开头 → 文件路径，从磁盘读取</li>
 *   <li>否则 → 视为 PEM 内联文本，原样返回</li>
 * </ul>
 *
 * <p>本项目策略：始终把 PEM 字符串保留在内存，不再像 Python 那样落盘到 {@code data/<ent>-<region>/ssl/}。
 */
public final class PemMaterialResolver {

    private PemMaterialResolver() {
    }

    /**
     * @param value region_info 字段值（PEM 内联文本或绝对路径），允许 null/blank
     * @return PEM 字符串内容；输入为 null/blank 时返回 null
     * @throws IOException 当 value 是文件路径但读取失败
     */
    public static String resolve(String value) throws IOException {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("/")) {
            return Files.readString(Path.of(value), StandardCharsets.UTF_8);
        }
        return value;
    }
}
