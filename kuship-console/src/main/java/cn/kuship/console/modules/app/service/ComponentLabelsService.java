package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.Labels;
import cn.kuship.console.modules.app.entity.NodeLabels;
import cn.kuship.console.modules.app.entity.ServiceLabels;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.LabelsRepository;
import cn.kuship.console.modules.app.repository.NodeLabelsRepository;
import cn.kuship.console.modules.app.repository.ServiceLabelsRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件标签读（对齐 AppLabelView.get + get_service_labels）。
 * used_labels=组件已用标签；unused_labels=region 节点标签中未被组件使用的。纯 DB。
 */
@Service
public class ComponentLabelsService {

    private final TenantServiceInfoRepository serviceRepository;
    private final RegionConfigRepository regionConfigRepository;
    private final ServiceLabelsRepository serviceLabelsRepository;
    private final NodeLabelsRepository nodeLabelsRepository;
    private final LabelsRepository labelsRepository;

    public ComponentLabelsService(TenantServiceInfoRepository serviceRepository,
                                  RegionConfigRepository regionConfigRepository,
                                  ServiceLabelsRepository serviceLabelsRepository,
                                  NodeLabelsRepository nodeLabelsRepository,
                                  LabelsRepository labelsRepository) {
        this.serviceRepository = serviceRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.serviceLabelsRepository = serviceLabelsRepository;
        this.nodeLabelsRepository = nodeLabelsRepository;
        this.labelsRepository = labelsRepository;
    }

    public Map<String, Object> getLabels(String serviceAlias, String regionName) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        String region = (regionName == null || regionName.isBlank()) ? service.getServiceRegion() : regionName;

        List<String> serviceLabelIds = serviceLabelsRepository.findByServiceId(service.getServiceId())
                .stream().map(ServiceLabels::getLabelId).toList();

        // 节点标签中排除组件已用（对齐 get_node_label_by_region(...).exclude(label_id__in=service_label_ids)）
        List<String> nodeLabelIds = new ArrayList<>();
        RegionConfig rc = regionConfigRepository.findByRegionName(region).orElse(null);
        if (rc != null) {
            for (NodeLabels nl : nodeLabelsRepository.findByRegionId(rc.getRegionId())) {
                if (!serviceLabelIds.contains(nl.getLabelId())) {
                    nodeLabelIds.add(nl.getLabelId());
                }
            }
        }

        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("used_labels", toDicts(serviceLabelIds));
        bean.put("unused_labels", toDicts(nodeLabelIds));
        return bean;
    }

    private List<Map<String, Object>> toDicts(List<String> labelIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!labelIds.isEmpty()) {
            for (Labels l : labelsRepository.findByLabelIdInOrderById(labelIds)) {
                out.add(l.toDict());
            }
        }
        return out;
    }
}
