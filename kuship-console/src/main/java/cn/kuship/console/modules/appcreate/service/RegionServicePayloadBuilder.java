package cn.kuship.console.modules.appcreate.service;

import cn.kuship.console.modules.appcreate.entity.ServiceSourceInfo;
import cn.kuship.console.modules.application.entity.TenantService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把已写入 console DB 的 {@link TenantService} + {@link ServiceSourceInfo} 字段拼成
 * region API {@code POST /v2/tenants/{tenant}/services} 所需的 body。
 */
@Component
public class RegionServicePayloadBuilder {

    public Map<String, Object> build(TenantService s, ServiceSourceInfo source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_id", s.getServiceId());
        body.put("service_alias", s.getServiceAlias());
        body.put("service_key", s.getServiceKey());
        body.put("k8s_component_name", s.getK8sComponentName());
        body.put("namespace", s.getNamespace());
        body.put("image", s.getImage());
        body.put("cmd", s.getCmd());
        body.put("docker_cmd", s.getDockerCmd());
        body.put("extend_method", s.getExtendMethod());
        body.put("port_type", s.getPortType());
        body.put("server_type", s.getServerType());
        body.put("protocol", s.getProtocol());
        body.put("category", s.getCategory());
        body.put("min_node", s.getMinNode());
        body.put("min_cpu", s.getMinCpu());
        body.put("min_memory", s.getMinMemory());
        body.put("container_gpu", s.getContainerGpu());
        body.put("total_memory", s.getTotalMemory());
        body.put("arch", s.getArch());
        body.put("dockerfile", s.getDockerfile());
        body.put("language", s.getLanguage());
        body.put("build_strategy", s.getBuildStrategy());
        body.put("creater", s.getCreater());
        if (source != null) {
            body.put("git_url", null);
            // 透传 service_source 字段（git 鉴权信息保留）
            body.put("user_name", source.getUserName());
            body.put("password", source.getPassword());
            body.put("group_key", source.getGroupKey());
            body.put("source_version", source.getVersion());
        }
        return body;
    }
}
