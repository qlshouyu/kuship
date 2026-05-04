package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRolePermsReq(
        @JsonProperty("perm_codes") @NotNull List<String> permCodes) {
}
