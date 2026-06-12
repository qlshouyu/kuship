package cn.kuship.console.infrastructure.region;

/**
 * region-api 调用超时梯度（秒），对齐 rainbond-console RegionInvokeApi 的分档：
 * 轻查询 2s → 常规 10/15s → 构建 20s → 导入/备份 300s。
 */
public enum TimeoutTier {

    LIGHT_QUERY(2),
    NORMAL(15),
    BUILD(20),
    IMPORT_BACKUP(300);

    private final int seconds;

    TimeoutTier(int seconds) {
        this.seconds = seconds;
    }

    public int seconds() {
        return seconds;
    }
}
