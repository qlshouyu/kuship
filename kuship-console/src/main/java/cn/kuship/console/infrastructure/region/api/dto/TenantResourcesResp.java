package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tenant 资源使用情况响应体。字段对照 Python {@code regionapi.py:get_tenant_resources} 实际返回。
 *
 * <p>所有字段都是 nullable —— 不同 region 版本可能返回字段子集。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantResourcesResp(
        @JsonProperty("region") String region,
        @JsonProperty("namespace") String namespace,
        @JsonProperty("limit_memory") Long limitMemory,
        @JsonProperty("limit_cpu") Long limitCpu,
        @JsonProperty("limit_storage") Long limitStorage,
        @JsonProperty("memory") Long memory,
        @JsonProperty("cpu") Long cpu,
        @JsonProperty("disk") Long disk,
        @JsonProperty("running_app_num") Integer runningAppNum,
        @JsonProperty("running_app_internal_num") Integer runningAppInternalNum,
        @JsonProperty("running_app_third_num") Integer runningAppThirdNum,
        @JsonProperty("service_running_num") Integer serviceRunningNum
) {
}
