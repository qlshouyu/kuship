package cn.kuship.console.modules.platform.service;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台级自定义配置读（对齐 rainbond CustomConfigsCLView.get + custom_configs_service.list()
 * → custom_configs_repo.list() = ConsoleConfig.filter(user_nick_name="").values()）。
 */
@Service
public class CustomConfigsService {

    private final ConsoleConfigRepository repository;

    public CustomConfigsService(ConsoleConfigRepository repository) {
        this.repository = repository;
    }

    /** 平台级配置（user_nick_name=""），每项全列 {ID, key, value, description, update_time, user_nick_name}（对齐 .values() 顺序）。 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConsoleConfig c : repository.findByUserNickName("")) {
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
