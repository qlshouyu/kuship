package cn.kuship.console.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessTokenReq(
        @NotBlank @Size(max = 32) String note,
        String expire) {
}
