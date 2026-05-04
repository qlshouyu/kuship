package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code GET /v2/tenants/{tenant_name}/region-key} 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegionPublickeyResp(
        @JsonProperty("public_key") String publicKey
) {
}
