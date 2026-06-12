package cn.kuship.console.modules.authorization.annotation;

/** 权限作用域：企业级（仅企业码）/ 团队级（企业码 ∪ 团队码，需 team_name 上下文）。 */
public enum PermScope {
    ENTERPRISE,
    TEAM
}
