package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /v2/tenants} 的请求体。字段命名与 Go 后端 / Python 端 {@code create_tenant} 完全一致。
 */
public record CreateTenantReq(
        @JsonProperty("tenant_name") String tenantName,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("eid") String enterpriseId,
        @JsonProperty("namespace") String namespace,
        @JsonProperty("bind_existing") Boolean bindExisting
) {
}
