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

/** 映射既有 {@code labels}（标签主表）。只读 + to_dict(model 顺序,datetime 空格)。 */
@Entity
@Table(name = "labels")
public class Labels {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "label_id", length = 32)
    private String labelId;
    @Column(name = "label_name", length = 128)
    private String labelName;
    @Column(name = "label_alias", length = 15)
    private String labelAlias;
    @Column(name = "category", length = 20)
    private String category;
    @Column(name = "create_time")
    private LocalDateTime createTime;

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", id);
        m.put("label_id", labelId);
        m.put("label_name", labelName);
        m.put("label_alias", labelAlias);
        m.put("category", category);
        m.put("create_time", createTime == null ? null : createTime.format(DT));
        return m;
    }
}
