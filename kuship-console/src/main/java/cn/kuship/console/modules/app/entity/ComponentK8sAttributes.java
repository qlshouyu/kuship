package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code component_k8s_attributes}（组件 k8s 属性）。只读 + to_dict(model 顺序,datetime 空格)。 */
@Entity
@Table(name = "component_k8s_attributes")
public class ComponentK8sAttributes {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "create_time")
    private LocalDateTime createTime;
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    @Column(name = "tenant_id", length = 32)
    private String tenantId;
    @Column(name = "component_id", length = 32)
    private String componentId;
    @Column(name = "name", length = 255)
    private String name;
    @Column(name = "save_type", length = 32)
    private String saveType;
    @Column(name = "attribute_value", columnDefinition = "longtext")
    private String attributeValue;

    public String getSaveType() { return saveType; }
    public String getAttributeValue() { return attributeValue; }

    /** @param valueOverride 非 null 时替换 attribute_value（json 转换后值），否则用原始字符串。 */
    public Map<String, Object> toDict(Object valueOverride) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("create_time", createTime == null ? null : createTime.format(DT));
        m.put("update_time", updateTime == null ? null : updateTime.format(DT));
        m.put("tenant_id", tenantId);
        m.put("component_id", componentId);
        m.put("name", name);
        m.put("save_type", saveType);
        m.put("attribute_value", valueOverride != null ? valueOverride : attributeValue);
        return m;
    }
}
