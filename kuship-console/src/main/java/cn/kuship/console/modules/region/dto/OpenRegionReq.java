package cn.kuship.console.modules.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record OpenRegionReq(
        @JsonProperty("region_name") @NotBlank String regionName) {
}
