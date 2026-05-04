package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record PemTransferReq(
        @JsonProperty("user_id") @NotNull Integer userId) {
}
