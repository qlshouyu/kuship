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

/** rainbond `rainbond_center_app_tag` —— 应用模板 Tag 字典（4 列）。 */
@Entity
@Table(name = "rainbond_center_app_tag")
@Getter
@Setter
@NoArgsConstructor
public class CenterAppTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "name", length = 32, nullable = false, unique = true)
    private String name;

    @Column(name = "enterprise_id", length = 32, nullable = false)
    private String enterpriseId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;
}
