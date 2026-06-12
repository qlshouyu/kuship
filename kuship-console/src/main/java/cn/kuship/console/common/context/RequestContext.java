package cn.kuship.console.common.context;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.team.entity.Tenants;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 请求级多租户上下文（@RequestScope，TARGET_CLASS 代理）。
 * <ul>
 *   <li>{@code currentUser} —— 由 JwtAuthenticationFilter 验签后真实加载写入</li>
 *   <li>{@code teamName} / {@code regionName} —— 由 TenantContextInterceptor 从路径变量注入</li>
 *   <li>{@code enterprise} / {@code team} —— 由 P1-a 的 EnterpriseContextResolver / TeamContextResolver 解析注入</li>
 * </ul>
 * 请求结束随作用域销毁，天然隔离并发请求。
 */
@Component
@RequestScope
public class RequestContext {

    private UserInfo currentUser;
    private String enterpriseId;
    private String teamName;
    private String regionName;
    private TenantEnterprise enterprise;
    private Tenants team;

    public UserInfo getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(UserInfo currentUser) {
        this.currentUser = currentUser;
        if (currentUser != null) {
            this.enterpriseId = currentUser.getEnterpriseId();
        }
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public TenantEnterprise getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(TenantEnterprise enterprise) {
        this.enterprise = enterprise;
    }

    public Tenants getTeam() {
        return team;
    }

    public void setTeam(Tenants team) {
        this.team = team;
        if (team != null) {
            this.teamName = team.getTenantName();
        }
    }
}
