package cn.kuship.console.modules.rbac.repository;

import cn.kuship.console.modules.rbac.entity.RoleInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleInfoRepository extends JpaRepository<RoleInfo, Integer> {

    /** 某范围（如 kind=team, kindId=tenant_id）下的角色定义。 */
    List<RoleInfo> findByKindAndKindId(String kind, String kindId);

    /** with_default：团队角色 ∪ 默认角色（kindIds = [tenant_id, "default"]），按 ID 升序。 */
    List<RoleInfo> findByKindAndKindIdInOrderById(String kind, List<String> kindIds);

    /** with_default 下按 ID 取单个角色。 */
    Optional<RoleInfo> findByKindAndKindIdInAndId(String kind, List<String> kindIds, Integer id);

    /** 团队自有角色按 ID（不含默认，用于改/删的存在性校验）。 */
    Optional<RoleInfo> findByKindAndKindIdAndId(String kind, String kindId, Integer id);

    /** with_default 下按名查重。 */
    Optional<RoleInfo> findByKindAndKindIdInAndName(String kind, List<String> kindIds, String name);
}
