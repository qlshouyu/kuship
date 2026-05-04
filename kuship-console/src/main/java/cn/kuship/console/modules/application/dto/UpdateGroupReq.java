package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record UpdateGroupReq(
        @JsonProperty("group_name") @Size(max = 128) String groupName,
        @Size(max = 2048) String note,
        @JsonProperty("k8s_app") @Size(max = 64) String k8sApp,
        @JsonProperty("governance_mode") @Size(max = 255) String governanceMode,
        @Size(max = 255) String logo) {
}
