package cn.kuship.console.modules.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 映射既有 {@code user_info} 表（Django {@code Users} 模型，USERNAME_FIELD = nick_name）。
 * <p>validate 模式下字段须与既有列精确对齐；主键为自增 {@code user_id}（非 BaseModel 的 ID）。
 * 无 DB 外键。
 */
@Entity
@Table(name = "user_info")
public class UserInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "nick_name", length = 64)
    private String nickName;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(name = "password", length = 64)
    private String password;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "sys_admin")
    private Boolean sysAdmin;

    @Column(name = "enterprise_id", length = 32)
    private String enterpriseId;

    @Column(name = "logo", length = 2048)
    private String logo;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Boolean getSysAdmin() {
        return sysAdmin;
    }

    public void setSysAdmin(Boolean sysAdmin) {
        this.sysAdmin = sysAdmin;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }
}
