package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户访问令牌列表读（对齐 rainbond UserAccessTokenCLView.get）。 */
@Service
public class UserAccessTokenService {

    private final UserAccessKeyRepository repository;

    public UserAccessTokenService(UserAccessKeyRepository repository) {
        this.repository = repository;
    }

    /** 当前用户令牌列表，每项 {note, expire_time, user_id, ID}。 */
    public List<Map<String, Object>> list(Integer userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserAccessKey k : repository.findByUserId(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("note", k.getNote());
            m.put("expire_time", k.getExpireTime());
            m.put("user_id", k.getUserId());
            m.put("ID", k.getId());
            out.add(m);
        }
        return out;
    }
}
