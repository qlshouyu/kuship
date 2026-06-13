package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 批量创建/更新（对齐 bulk_create_or_update）：当前用户已存在该 key 且值为真→更新(该 key 全局删后重建)，否则新建；
     * 无 key 跳过。全部 bulk 写入（user_nick_name=当前用户，update_time=null）。
     */
    @Transactional
    public void bulkCreateOrUpdate(String userNickName, java.util.List<java.util.Map<String, Object>> configs) {
        java.util.Map<String, Object> existTruthy = new java.util.HashMap<>();
        for (ConsoleConfig c : repository.findByUserNickName(userNickName)) {
            if (c.getValue() != null && !c.getValue().isEmpty()) {
                existTruthy.put(c.getKey(), c.getValue());
            }
        }
        java.util.List<ConsoleConfig> toSave = new java.util.ArrayList<>();
        java.util.List<String> updateKeys = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> cfg : configs) {
            Object keyObj = cfg.get("key");
            if (keyObj == null || keyObj.toString().isEmpty()) {
                continue;
            }
            String key = keyObj.toString();
            Object val = cfg.get("value");
            ConsoleConfig m = new ConsoleConfig();
            m.setKey(key);
            m.setValue(val == null ? "" : val.toString());
            m.setUserNickName(userNickName);
            if (existTruthy.get(key) != null) {
                updateKeys.add(key);
            }
            toSave.add(m);
        }
        if (!updateKeys.isEmpty()) {
            repository.deleteByKeyIn(updateKeys);
        }
        repository.saveAll(toSave);
    }
}
