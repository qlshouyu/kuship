package cn.kuship.console.infrastructure.region.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code region_info} 表只读访问。
 *
 * <p>关键约束：
 * <ul>
 *   <li>本仓库仅查询；不提供任何 INSERT / UPDATE / DELETE 方法</li>
 *   <li>schema 演进权属于 rainbond-console（Django migrations）；本类不引入 {@code @Entity}</li>
 *   <li>region_info 写操作（添加/删除集群）由 change {@code migrate-console-region-cluster} 落地</li>
 * </ul>
 */
@Repository
public class RegionInfoRepository {

    private static final String COLUMNS = "region_id, region_name, region_alias, region_type, "
            + "url, wsurl, httpdomain, tcpdomain, token, status, scope, "
            + "ssl_ca_cert, cert_file, key_file, enterprise_id, provider, provider_cluster_id";

    private final JdbcTemplate jdbcTemplate;

    public RegionInfoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按 {@code region_name} 查询；该列在表中是 unique。
     */
    public Optional<RegionInfoDto> findByName(String regionName) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM region_info WHERE region_name = ? LIMIT 1",
                RegionInfoRowMapper.INSTANCE,
                regionName).stream().findFirst();
    }

    /**
     * 按 {@code (enterprise_id, region_name)} 查询。enterprise_id 可空（与表定义一致）；
     * 当传入 {@code null} 时退化为按名字查询。
     */
    public Optional<RegionInfoDto> findByEnterpriseAndName(String enterpriseId, String regionName) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            return findByName(regionName);
        }
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM region_info "
                        + "WHERE enterprise_id = ? AND region_name = ? LIMIT 1",
                RegionInfoRowMapper.INSTANCE,
                enterpriseId, regionName).stream().findFirst();
    }
}
