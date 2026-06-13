package cn.kuship.console.modules.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 映射既有 {@code node_labels}（节点↔标签）。只读。 */
@Entity
@Table(name = "node_labels")
public class NodeLabels {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ID")
    private Integer id;
    @Column(name = "region_id", length = 36) private String regionId;
    @Column(name = "node_uuid", length = 36) private String nodeUuid;
    @Column(name = "label_id", length = 32) private String labelId;
    @Column(name = "create_time") private java.time.LocalDateTime createTime;
    public String getLabelId() { return labelId; }
}
