package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户自定义配置列表读（对齐 rainbond CustomConfigsUserCLView.get + list_by_user_nick_name 的 .values()）。 */
@Service
public class UserCustomConfigsService {

    private final ConsoleConfigRepository repository;

    public UserCustomConfigsService(ConsoleConfigRepository repository) {
        this.repository = repository;
    }

    /** 当前用户配置，每项全列 {ID, key, value, description, update_time, user_nick_name}（对齐 .values() 顺序）。 */
    public List<Map<String, Object>> list(String userNickName) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConsoleConfig c : repository.findByUserNickName(userNickName)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", c.getId());
            m.put("key", c.getKey());
            m.put("value", c.getValue());
            m.put("description", c.getDescription());
            m.put("update_time", c.getUpdateTime());
            m.put("user_nick_name", c.getUserNickName());
            out.add(m);
        }
        return out;
    }
}
