package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * {@code GET /v2/tenants/{tenant_name}/labels} 响应体。
 *
 * <p>region 端通常返回一个 label 字符串列表（如 {@code ["windows", "ssd"]}）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegionLabelsResp(
        List<String> labels
) {
}
