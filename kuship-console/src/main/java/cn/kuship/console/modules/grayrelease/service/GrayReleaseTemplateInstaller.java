package cn.kuship.console.modules.grayrelease.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 灰度服务组实例化的 stub 实现。完整 "从模板批量创建组件 + 写 service_group_relation + 调 region createService"
 * 的流程依赖未迁移的 {@code AppInstallService}（rainbond Python {@code market_app_service.install_app}），
 * 由后续 {@code migrate-console-app-install} change 落地。
 *
 * <p>当前 stub 行为：
 * <ul>
 *   <li>生成 32-char 合成 service_id（uniqueness 上没冲突）</li>
 *   <li>生成 6 位随机 upgrade_group_id（int 范围内）</li>
 *   <li>记录 WARN 日志告知调用方 stub 行为</li>
 *   <li>不调 region createService，不写 tenant_service / service_group_relation</li>
 * </ul>
 *
 * <p>由此 stub 创建的"灰度 service"在 ApisixRoute 权重更新时不会有真实后端 endpoint；
 * 在 rainbond-go core 真实集群中应表现为 ApisixRoute 4xx（service not found）。集成测试通过
 * mock {@code GatewayOperations} 隔离这层依赖。
 */
@Component
public class GrayReleaseTemplateInstaller {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseTemplateInstaller.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @return 包含 grayServiceId / grayServiceCname / grayUpgradeGroupId / originalServiceId
     *         / originalServiceCname / originalUpgradeGroupId 的简化结果。
     */
    public Result installGrayServiceGroup(String tenantId, Integer appId, String templateId, String version,
                                            String marketName, boolean installFromCloud) {
        String grayServiceId = randomServiceId();
        String origServiceId = randomServiceId();
        Integer grayUpgradeGroupId = RANDOM.nextInt(900_000) + 100_000;
        Integer origUpgradeGroupId = RANDOM.nextInt(900_000) + 100_000;
        log.warn("[GrayRelease][stub] template install bypassed; tenant={} app={} template={} version={} cloud={}; "
                + "synthetic ids gray_svc={} orig_svc={}; pending migrate-console-app-install",
                tenantId, appId, templateId, version, installFromCloud,
                grayServiceId, origServiceId);
        return new Result(origServiceId, "original",
                grayServiceId, "gray-" + (templateId == null ? "v1" : templateId),
                origUpgradeGroupId, grayUpgradeGroupId);
    }

    public void uninstallGrayServiceGroup(String tenantId, Integer appId, Integer grayUpgradeGroupId) {
        log.warn("[GrayRelease][stub] template uninstall bypassed; tenant={} app={} upgrade_group={}; "
                + "pending migrate-console-app-install", tenantId, appId, grayUpgradeGroupId);
    }

    private static String randomServiceId() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public record Result(
            String originalServiceId,
            String originalServiceCname,
            String grayServiceId,
            String grayServiceCname,
            Integer originalUpgradeGroupId,
            Integer grayUpgradeGroupId) {
    }
}
