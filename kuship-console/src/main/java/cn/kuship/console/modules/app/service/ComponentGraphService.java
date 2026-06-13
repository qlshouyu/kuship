package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.ComponentGraph;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ComponentGraphRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 组件监控图列表读（对齐 ComponentGraphListView.get + list_component_graphs，纯 DB）。 */
@Service
public class ComponentGraphService {

    private final TenantServiceInfoRepository serviceRepository;
    private final ComponentGraphRepository graphRepository;

    public ComponentGraphService(TenantServiceInfoRepository serviceRepository,
                                 ComponentGraphRepository graphRepository) {
        this.serviceRepository = serviceRepository;
        this.graphRepository = graphRepository;
    }

    public List<Map<String, Object>> listGraphs(String serviceAlias) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComponentGraph g : graphRepository.findByComponentIdOrderBySequence(service.getServiceId())) {
            out.add(g.toDict());
        }
        return out;
    }
}
