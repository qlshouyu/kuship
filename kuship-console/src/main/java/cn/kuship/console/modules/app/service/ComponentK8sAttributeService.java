package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.ComponentK8sAttributes;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ComponentK8sAttributesRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件 k8s 属性列表读（对齐 ComponentK8sAttributeListView.get + list_by_component_ids，纯 DB）。
 * save_type=="json" 且 attribute_value 非空：解析 JSON——对象→[{key,value}]，数组/字符串透传；解析失败→原样。
 */
@Service
public class ComponentK8sAttributeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantServiceInfoRepository serviceRepository;
    private final ComponentK8sAttributesRepository attributeRepository;

    public ComponentK8sAttributeService(TenantServiceInfoRepository serviceRepository,
                                        ComponentK8sAttributesRepository attributeRepository) {
        this.serviceRepository = serviceRepository;
        this.attributeRepository = attributeRepository;
    }

    public List<Map<String, Object>> listAttributes(String serviceAlias) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComponentK8sAttributes attr : attributeRepository.findByComponentId(service.getServiceId())) {
            out.add(toDict(attr));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toDict(ComponentK8sAttributes attr) {
        if (!"json".equals(attr.getSaveType()) || attr.getAttributeValue() == null || attr.getAttributeValue().isEmpty()) {
            return attr.toDict(null);
        }
        Object parsed;
        try {
            parsed = MAPPER.readValue(attr.getAttributeValue(), Object.class);
        } catch (Exception e) {
            return attr.toDict(null); // 解析失败 → 原样字符串
        }
        Object value;
        if (parsed instanceof Map) {
            List<Map<String, Object>> kv = new ArrayList<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) parsed).entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", e.getKey());
                item.put("value", e.getValue());
                kv.add(item);
            }
            value = kv;
        } else {
            value = parsed; // 数组/字符串/数字 透传
        }
        return attr.toDict(value);
    }
}
