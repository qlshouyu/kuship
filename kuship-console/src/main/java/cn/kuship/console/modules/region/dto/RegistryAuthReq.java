package cn.kuship.console.modules.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistryAuthReq(
        @NotBlank @Size(max = 255) String domain,
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(max = 255) String password,
        @JsonProperty("hub_type") @Size(max = 32) String hubType,
        @JsonProperty("region_name") @Size(max = 255) String regionName,
        @JsonProperty("secret_id") @Size(max = 32) String secretId) {
}
