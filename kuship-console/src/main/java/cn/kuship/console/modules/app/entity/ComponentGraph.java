package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** 映射既有 {@code component_graphs}（组件监控图）。只读 + to_dict(model 顺序)。 */
@Entity
@Table(name = "component_graphs")
public class ComponentGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "component_id", length = 32)
    private String componentId;
    @Column(name = "graph_id", length = 32)
    private String graphId;
    @Column(name = "title", length = 255)
    private String title;
    @Column(name = "promql", length = 2047)
    private String promql;
    @Column(name = "sequence")
    private Integer sequence;

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("component_id", componentId);
        m.put("graph_id", graphId);
        m.put("title", title);
        m.put("promql", promql);
        m.put("sequence", sequence);
        return m;
    }
}
