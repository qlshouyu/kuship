package cn.kuship.console.infrastructure.region.repository;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 把 {@code region_info} 表的一行映射为 {@link RegionInfoDto}。snake_case 列名直接读取，无名字转换。
 */
public class RegionInfoRowMapper implements RowMapper<RegionInfoDto> {

    public static final RegionInfoRowMapper INSTANCE = new RegionInfoRowMapper();

    @Override
    public RegionInfoDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RegionInfoDto(
                rs.getString("region_id"),
                rs.getString("region_name"),
                rs.getString("region_alias"),
                rs.getString("region_type"),
                rs.getString("url"),
                rs.getString("wsurl"),
                rs.getString("httpdomain"),
                rs.getString("tcpdomain"),
                rs.getString("token"),
                rs.getString("status"),
                rs.getString("scope"),
                rs.getString("ssl_ca_cert"),
                rs.getString("cert_file"),
                rs.getString("key_file"),
                rs.getString("enterprise_id"),
                rs.getString("provider"),
                rs.getString("provider_cluster_id"));
    }
}
