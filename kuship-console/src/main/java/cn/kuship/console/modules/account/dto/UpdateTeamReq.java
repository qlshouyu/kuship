package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record UpdateTeamReq(
        @JsonProperty("team_alias") @Size(max = 64) String teamAlias,
        @Size(max = 2048) String logo) {
}
