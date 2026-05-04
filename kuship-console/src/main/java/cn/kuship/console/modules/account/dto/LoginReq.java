package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginReq(
        @JsonProperty("nick_name") @NotBlank String nickName,
        @NotBlank @Size(min = 1, max = 64) String password) {
}
