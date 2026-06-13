package cn.kuship.console.modules.app.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件状态策略表，1:1 移植 rainbond {@code www/utils/status_translate.status_map} +
 * {@code get_status_info_map}：status → {status_cn, disabledAction, activeAction}。
 */
public final class StatusTranslate {

    private StatusTranslate() {
    }

    private record Policy(String statusCn, List<String> disabled, List<String> active) {
    }

    private static final Map<String, Policy> STATUS_MAP = new LinkedHashMap<>();

    static {
        STATUS_MAP.put("running", new Policy("运行中", List.of("restart"),
                List.of("stop", "deploy", "visit", "manage_container", "reboot")));
        STATUS_MAP.put("starting", new Policy("启动中", List.of("restart", "visit", "manage_container"),
                List.of("deploy", "stop", "reboot")));
        STATUS_MAP.put("restoring", new Policy("恢复中", List.of("restart", "visit", "manage_container"),
                List.of("deploy", "stop", "reboot")));
        STATUS_MAP.put("waiting", new Policy("等待运行", List.of("restart"),
                List.of("stop", "deploy", "visit", "manage_container", "reboot")));
        STATUS_MAP.put("paused", new Policy("挂起", List.of("restart"),
                List.of("stop", "deploy", "visit", "manage_container", "reboot")));
        STATUS_MAP.put("checking", new Policy("检测中", List.of("deploy", "restart", "visit", "manage_container"),
                List.of("stop", "reboot")));
        STATUS_MAP.put("stoping", new Policy("关闭中",
                List.of("deploy", "restart", "stop", "visit", "manage_container", "reboot"), List.of()));
        STATUS_MAP.put("unusual", new Policy("运行异常", List.of("visit", "restart", "manage_container"),
                List.of("stop", "deploy", "reboot")));
        STATUS_MAP.put("closed", new Policy("已关闭", List.of("visit", "stop", "manage_container", "reboot"),
                List.of("restart", "deploy")));
        STATUS_MAP.put("owed", new Policy("余额不足已关闭",
                List.of("deploy", "visit", "restart", "stop", "manage_container", "reboot"), List.of("pay")));
        STATUS_MAP.put("Owed", new Policy("余额不足已关闭",
                List.of("deploy", "visit", "restart", "stop", "manage_container", "reboot"), List.of("pay")));
        STATUS_MAP.put("expired", new Policy("试用已到期",
                List.of("visit", "restart", "deploy", "stop", "manage_container", "reboot"), List.of("pay")));
        STATUS_MAP.put("undeploy", new Policy("未部署",
                List.of("restart", "stop", "visit", "manage_container", "reboot"), List.of("deploy")));
        STATUS_MAP.put("unKnow", new Policy("未知", List.of("deploy", "restart", "visit", "manage_container"),
                List.of("stop", "reboot")));
        // 原 Python status_map["deployed"]="已部署"(裸字符串)，get_status_info_map 对其会异常；现实 status 不会返回 deployed → 按表外(未知)处理
        STATUS_MAP.put("abnormal", new Policy("运行异常", List.of("visit", "restart", "manage_container"),
                List.of("stop", "deploy", "reboot")));
        STATUS_MAP.put("failure", new Policy("未知", List.of("deploy", "restart", "visit", "manage_container"),
                List.of("stop", "reboot")));
        STATUS_MAP.put("upgrade", new Policy("升级中", List.of("manage_container"),
                List.of("stop", "visit", "deploy", "restart", "reboot")));
        STATUS_MAP.put("stopping", new Policy("关闭中",
                List.of("deploy", "restart", "stop", "visit", "manage_container", "reboot"), List.of()));
        STATUS_MAP.put("some_abnormal", new Policy("部分实例异常", List.of("restart"),
                List.of("stop", "visit", "deploy", "reboot", "manage_container")));
        STATUS_MAP.put("uncreate", new Policy("未部署", List.of(), List.of()));
        STATUS_MAP.put("creating", new Policy("创建中",
                List.of("restart", "stop", "visit", "manage_container", "reboot"), List.of("deploy")));
        STATUS_MAP.put("succeeded", new Policy("已完成", List.of(), List.of("restart", "stop")));
    }

    /**
     * 移植 get_status_info_map：返回有序 {status, status_cn, disabledAction, activeAction}。
     * 表外 status → status_cn=未知、动作列表为空。
     */
    public static Map<String, Object> getStatusInfoMap(String status) {
        Map<String, Object> rt = new LinkedHashMap<>();
        Policy p = STATUS_MAP.get(status);
        rt.put("status", status);
        if (p != null) {
            rt.put("status_cn", p.statusCn());
            rt.put("disabledAction", p.disabled());
            rt.put("activeAction", p.active());
        } else {
            rt.put("status_cn", "未知");
            rt.put("disabledAction", List.of());
            rt.put("activeAction", List.of());
        }
        return rt;
    }
}
