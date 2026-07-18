package cn.kuship.console.modules.region.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 平台插件读（对齐 rainbond PlatformPluginLView.get → platform_plugin_service.list_platform_plugins）。
 *
 * <p>rainbond 该列表来自<strong>云端应用市场</strong>（/app-server/openapi/apps/platform-plugins），
 * 再叠加 region 已安装状态、集群架构过滤与授权过滤。云端市场客户端尚未移植到 kuship-console，
 * 故当前返回空列表——这与 7070 在未配置授权/市场不可用环境下的实测出参（list:[]）一致。
 * 待市场客户端就绪后在此补全候选拉取与安装态合并逻辑。
 */
@Service
public class PlatformPluginService {

    /** 平台插件候选列表（当前为空，详见类注释）。 */
    public List<Map<String, Object>> listPlatformPlugins(String enterpriseId, String regionName) {
        return List.of();
    }
}
