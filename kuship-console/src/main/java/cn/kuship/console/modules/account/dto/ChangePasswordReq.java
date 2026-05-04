package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordReq(
        @JsonProperty("old_password") @NotBlank String oldPassword,
        @JsonProperty("new_password") @NotBlank @Size(min = 8, max = 64) String newPassword) {
}
