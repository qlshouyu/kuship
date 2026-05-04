package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddTeamMembersReq(
        @JsonProperty("user_ids") @NotEmpty List<Integer> userIds,
        @JsonProperty("role_ids") List<Integer> roleIds) {
}
