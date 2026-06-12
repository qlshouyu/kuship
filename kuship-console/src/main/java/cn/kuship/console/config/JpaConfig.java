package cn.kuship.console.config;

import org.springframework.context.annotation.Configuration;

/**
 * JPA 配置占位。实体/仓库扫描由 Spring Boot 自动配置（主类位于 cn.kuship.console 根包）覆盖。
 *
 * <p>关键约束（见架构文档 §5、design.md D6）：
 * <ul>
 *   <li>{@code hibernate.ddl-auto=validate}：共享 Django 的 console 库，绝不输出 DDL；</li>
 *   <li>无 DB 外键（Django db_constraint=False）：实体关系不声明外键约束、不级联；</li>
 *   <li>不引入 Django 不认识的列（如乐观锁 @Version）。</li>
 * </ul>
 * 暂不提供共享 BaseEntity：user_info 主键为 user_id、BaseModel 系列为 ID，PK 命名不一致，逐表显式映射更安全。
 */
@Configuration
public class JpaConfig {
}
