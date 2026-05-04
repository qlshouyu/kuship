package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchUserIdsReq(
        @JsonProperty("user_ids") @NotEmpty List<Integer> userIds) {
}
