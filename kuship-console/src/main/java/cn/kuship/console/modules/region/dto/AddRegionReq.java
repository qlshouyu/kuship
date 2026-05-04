package cn.kuship.console.modules.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddRegionReq(
        @JsonProperty("region_name") @NotBlank @Size(min = 2, max = 64) String regionName,
        @JsonProperty("region_alias") @Size(max = 64) String regionAlias,
        @NotBlank String token,
        @Size(max = 200) String desc,
        @JsonProperty("region_type") List<String> regionType,
        @JsonProperty("scope") @Size(max = 10) String scope,
        @JsonProperty("provider") @Size(max = 24) String provider,
        @JsonProperty("provider_cluster_id") @Size(max = 64) String providerClusterId) {
}
