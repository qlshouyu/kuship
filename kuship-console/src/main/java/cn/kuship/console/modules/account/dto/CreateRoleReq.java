package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateRoleReq(
        @NotBlank String name,
        @JsonProperty("perm_codes") List<String> permCodes) {
}
