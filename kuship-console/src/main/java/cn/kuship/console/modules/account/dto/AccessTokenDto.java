package cn.kuship.console.modules.account.dto;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessTokenDto(
        @JsonProperty("id") Integer id,
        String note,
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("access_key") String accessKey,
        @JsonProperty("expire_time") Integer expireTime) {

    public static AccessTokenDto fromMasked(UserAccessKey k) {
        String key = k.getAccessKey();
        String masked = key == null ? null
                : (key.length() > 10 ? key.substring(0, 6) + "****" + key.substring(key.length() - 4)
                                     : "****");
        return new AccessTokenDto(k.getId(), k.getNote(), k.getUserId(), masked, k.getExpireTime());
    }

    public static AccessTokenDto fromPlain(UserAccessKey k) {
        return new AccessTokenDto(k.getId(), k.getNote(), k.getUserId(), k.getAccessKey(), k.getExpireTime());
    }
}
