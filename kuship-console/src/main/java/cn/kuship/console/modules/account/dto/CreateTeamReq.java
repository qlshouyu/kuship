package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamReq(
        @JsonProperty("team_name") @NotBlank @Size(min = 2, max = 64) String teamName,
        @JsonProperty("team_alias") @Size(max = 64) String teamAlias,
        @JsonProperty("namespace") @Size(max = 33) String namespace,
        @JsonProperty("enterprise_id") @Size(max = 32) String enterpriseId,
        @JsonProperty("useable_regions") String useableRegions) {
}
