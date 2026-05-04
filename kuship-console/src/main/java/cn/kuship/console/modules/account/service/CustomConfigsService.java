package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.dto.ConsoleConfigItem;
import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 复刻 rainbond {@code custom_configs_service.bulk_create_or_update} 行为：
 *
 * <ul>
 *   <li>对入参 list 中每项：若 key 已存在则先删除旧行再插入；若 key 不存在则直接插入</li>
 *   <li>等价于 upsert（rainbond Django bulk_create 不带 update，所以用 delete+insert 模拟）</li>
 * </ul>
 */
@Service
public class CustomConfigsService {

    private final ConsoleConfigRepository repo;

    public CustomConfigsService(ConsoleConfigRepository repo) {
        this.repo = repo;
    }

    public List<ConsoleConfig> list(String userNickName) {
        return repo.findByUserNickName(userNickName == null ? "" : userNickName);
    }

    @Transactional
    public List<ConsoleConfig> bulkCreateOrUpdate(List<ConsoleConfigItem> items, String userNickName) {
        if (items == null) {
            throw new ServiceHandleException(400, "items must not be null", "请求参数必须为列表");
        }
        String nick = userNickName == null ? "" : userNickName;
        List<String> incomingKeys = new ArrayList<>();
        for (ConsoleConfigItem item : items) {
            if (item != null && item.key() != null && !item.key().isBlank()) {
                incomingKeys.add(item.key());
            }
        }
        if (!incomingKeys.isEmpty()) {
            repo.deleteByUserNickNameAndKeyIn(nick, incomingKeys);
        }
        List<ConsoleConfig> rows = new ArrayList<>();
        for (ConsoleConfigItem item : items) {
            if (item == null || item.key() == null || item.key().isBlank()) {
                continue;
            }
            ConsoleConfig c = new ConsoleConfig();
            c.setKey(item.key());
            c.setValue(item.value() == null ? "" : item.value());
            c.setDescription(item.description());
            c.setUserNickName(nick);
            c.setUpdateTime(LocalDateTime.now());
            rows.add(c);
        }
        return repo.saveAll(rows);
    }
}
