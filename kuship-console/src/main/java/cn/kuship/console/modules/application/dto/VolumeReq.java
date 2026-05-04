package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VolumeReq(
        @JsonProperty("volume_name") @NotBlank @Size(max = 100) String volumeName,
        @JsonProperty("volume_type") @Size(max = 64) String volumeType,
        @JsonProperty("volume_path") @Size(max = 400) String volumePath,
        @JsonProperty("volume_capacity") Integer volumeCapacity,
        @JsonProperty("access_mode") @Size(max = 100) String accessMode,
        @Size(max = 100) String category,
        @JsonProperty("share_policy") @Size(max = 100) String sharePolicy,
        Integer mode) {
}
