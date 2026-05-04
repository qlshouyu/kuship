package cn.kuship.console.infrastructure.region.errormsg;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 Rainbond Go 集群返回的英文错误消息映射为用户友好中文 {@code msg_show}。
 *
 * <p>覆盖范围（与 rainbond-console {@code build_region_error_msg_show} 一致）：
 * <ol>
 *   <li>Helm 接管冲突（命名空间已存在同名资源 + 缺少 helm.sh 元数据）</li>
 *   <li>域名冲突（多个 ingress 声明同一 host）</li>
 *   <li>其他原始消息：原样透传</li>
 * </ol>
 *
 * <p>正则常量直接照搬 Python 版（{@code regionapibaseclient.py:27-39}）。
 */
@Component
public class RegionErrorMsgEnricher {

    private static final Pattern HELM_OWNERSHIP_CONFLICT = Pattern.compile(
            "(?<kind>[A-Za-z]+)\\s+\"(?<name>[^\"]+)\"\\s+in namespace\\s+\"(?<namespace>[^\"]+)\"\\s+"
                    + "exists and cannot be imported into the current release: invalid ownership metadata;.*?"
                    + "meta\\.helm\\.sh/release-name\":\\s+must be set to \"(?<releaseName>[^\"]+)\";.*?"
                    + "meta\\.helm\\.sh/release-namespace\":\\s+must be set to \"(?<releaseNamespace>[^\"]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DOMAIN_CONFLICT = Pattern.compile(
            "domain conflict:\\s+domain\\s+'(?<domain>[^']+)'\\s+conflicts with existing domain\\s+"
                    + "'(?<existingDomain>[^']+)'\\s+in namespace\\s+'(?<namespace>[^']+)'\\s+"
                    + "\\(resource:\\s+(?<resource>[^)]+)\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * @return 中文化后的 msg_show；输入 null/blank 时返回原值。
     */
    public String enrich(String msg) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        String helm = enrichHelmOwnershipConflict(msg);
        if (helm != null) {
            return helm;
        }
        String domain = enrichDomainConflict(msg);
        if (domain != null) {
            return domain;
        }
        return msg;
    }

    private String enrichHelmOwnershipConflict(String msg) {
        if (!msg.contains("cannot be imported into the current release")
                || !msg.contains("invalid ownership metadata")) {
            return null;
        }
        Matcher m = HELM_OWNERSHIP_CONFLICT.matcher(msg);
        if (!m.find()) {
            return "命名空间中已存在同名资源，且缺少 Helm 接管元数据，请先删除冲突资源或补齐 Helm 元数据后重试";
        }
        return String.format(
                "命名空间 %s 中已存在资源 %s/%s，且缺少 Helm 接管元数据，"
                        + "Release %s 无法继续安装。请先删除该资源，或补齐 Helm 元数据后重试："
                        + "app.kubernetes.io/managed-by=Helm，"
                        + "meta.helm.sh/release-name=%s，"
                        + "meta.helm.sh/release-namespace=%s。",
                m.group("namespace"), m.group("kind"), m.group("name"),
                m.group("releaseName"),
                m.group("releaseName"), m.group("releaseNamespace"));
    }

    private String enrichDomainConflict(String msg) {
        if (!msg.contains("domain conflict:") || !msg.contains("conflicts with existing domain")) {
            return null;
        }
        Matcher m = DOMAIN_CONFLICT.matcher(msg);
        if (!m.find()) {
            return "域名与现有证书配置冲突，请先清理冲突配置后重试。";
        }
        return String.format(
                "域名 %s 与命名空间 %s 中已存在的域名 %s 冲突（资源：%s），请先清理冲突配置后重试。",
                m.group("domain"), m.group("namespace"),
                m.group("existingDomain"), m.group("resource"));
    }
}
