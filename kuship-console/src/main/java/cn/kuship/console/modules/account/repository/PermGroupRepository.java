package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.PermGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermGroupRepository extends JpaRepository<PermGroup, Integer> {
}
