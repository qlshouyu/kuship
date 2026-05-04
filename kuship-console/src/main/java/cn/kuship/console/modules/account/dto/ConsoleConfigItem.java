package cn.kuship.console.modules.account.dto;

import cn.kuship.console.modules.account.entity.ConsoleConfig;

public record ConsoleConfigItem(String key, String value, String description) {

    public static ConsoleConfigItem from(ConsoleConfig c) {
        return new ConsoleConfigItem(c.getKey(), c.getValue(), c.getDescription());
    }
}
