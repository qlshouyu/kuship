package cn.kuship.console.modules.appmarket.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rainbond `rainbond_center_app_tag_relation` —— 应用-Tag 关联（4 列，enterprise_id varchar(36)）。 */
@Entity
@Table(name = "rainbond_center_app_tag_relation")
@Getter
@Setter
@NoArgsConstructor
public class CenterAppTagRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "enterprise_id", length = 36, nullable = false)
    private String enterpriseId;

    @Column(name = "app_id", length = 32, nullable = false)
    private String appId;

    @Column(name = "tag_id", nullable = false)
    private Integer tagId;
}
