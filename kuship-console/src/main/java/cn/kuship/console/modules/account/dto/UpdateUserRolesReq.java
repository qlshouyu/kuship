package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRolesReq(
        @JsonProperty("role_ids") @NotNull List<Integer> roleIds) {
}
