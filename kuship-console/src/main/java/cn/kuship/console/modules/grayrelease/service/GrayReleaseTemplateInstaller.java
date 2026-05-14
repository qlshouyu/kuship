package cn.kuship.console.modules.grayrelease.service;

import cn.kuship.console.modules.grayrelease.api.GrayReleaseOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 灰度服务组实例化的 stub + region 接线（migrate-console-grayrelease-finalize）。
 *
 * <p>本 change 在原 stub 之上追加 region 调用：{@code installGrayServiceGroup} 调
 * {@link GrayReleaseOperations#createAppGrayRelease} 让 region 端 desired_replicas / strategy
 * 与本地 record 同步；{@code uninstallGrayServiceGroup} 调 {@code operateAppGrayRelease(rollback)}
 * 通知 region 解除灰度对象。
 *
 * <p>仍 stub 范围（待 {@code migrate-console-app-install} 落地）：
 * <ul>
 *   <li>本地 service_group / tenant_service / service_group_relation 批量 INSERT 仍是合成 id</li>
 *   <li>region 端真实 service_id 来自 {@code createAppGrayRelease} 响应；缺失时 fallback 合成 id</li>
 * </ul>
 *
 * <p>降级阀 {@code kuship.gray-release.skip-region-template-install=true} 时回退到
 * {@code add-gray-release} 原始合成 id 行为，便于无 region 集成测试。
 */
@Component
public class GrayReleaseTemplateInstaller {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseTemplateInstaller.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GrayReleaseOperations grayReleaseOps;
    private final boolean skipRegionTemplateInstall;

    public GrayReleaseTemplateInstaller(GrayReleaseOperations grayReleaseOps,
                                          @Value("${kuship.gray-release.skip-region-template-install:false}")
                                          boolean skipRegionTemplateInstall) {
        this.grayReleaseOps = grayReleaseOps;
        this.skipRegionTemplateInstall = skipRegionTemplateInstall;
    }

    public Result installGrayServiceGroup(String regionName, String tenantName, String tenantId,
                                            Integer appId, Integer regionAppId,
                                            String templateId, String version,
                                            String marketName, boolean installFromCloud) {
        // 合成 id 兜底（region 响应字段缺失时使用，与 add-gray-release 既定行为一致）
        String synGray = randomServiceId();
        String synOrig = randomServiceId();
        Integer synGrayUg = RANDOM.nextInt(900_000) + 100_000;
        Integer synOrigUg = RANDOM.nextInt(900_000) + 100_000;
        log.warn("[GrayRelease][stub] local service_group write bypassed; tenant={} app={} template={} version={} "
                + "cloud={}; synthetic ids gray_svc={} orig_svc={}; pending migrate-console-app-install",
                tenantId, appId, templateId, version, installFromCloud, synGray, synOrig);

        if (skipRegionTemplateInstall) {
            return new Result(synOrig, "original", synGray, "gray-" + (templateId == null ? "v1" : templateId),
                    synOrigUg, synGrayUg);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId == null ? "" : templateId);
        body.put("version", version == null ? "" : version);
        body.put("market_name", marketName == null ? "" : marketName);
        body.put("install_from_cloud", installFromCloud);
        body.put("gray_ratio", 0); // 由 caller 后续在 ApisixRouteWeightUpdater 里切

        Map<String, Object> resp = grayReleaseOps.createAppGrayRelease(regionName, tenantName,
                regionAppId == null ? appId : regionAppId, body);

        String origServiceId = stringOr(resp.get("original_service_id"), synOrig);
        String grayServiceId = stringOr(resp.get("gray_service_id"), synGray);
        Integer origUg = intOr(resp.get("original_upgrade_group_id"), synOrigUg);
        Integer grayUg = intOr(resp.get("gray_upgrade_group_id"), synGrayUg);

        return new Result(origServiceId, "original",
                grayServiceId, "gray-" + (templateId == null ? "v1" : templateId),
                origUg, grayUg);
    }

    public void uninstallGrayServiceGroup(String regionName, String tenantName, String tenantId,
                                             Integer appId, Integer regionAppId,
                                             String namespace, Integer grayUpgradeGroupId) {
        log.warn("[GrayRelease][stub] local service_group cleanup bypassed; tenant={} app={} upgrade_group={}; "
                + "pending migrate-console-app-install", tenantId, appId, grayUpgradeGroupId);

        if (skipRegionTemplateInstall) {
            return;
        }

        try {
            grayReleaseOps.operateAppGrayRelease(regionName, tenantName,
                    regionAppId == null ? appId : regionAppId, namespace, "rollback");
        } catch (RuntimeException e) {
            log.warn("[GrayRelease] rollback region operate failed for app {}; cause={}", appId, e.getMessage());
        }
    }

    private static String randomServiceId() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String stringOr(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static Integer intOr(Object value, Integer fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignore) {}
        }
        return fallback;
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
