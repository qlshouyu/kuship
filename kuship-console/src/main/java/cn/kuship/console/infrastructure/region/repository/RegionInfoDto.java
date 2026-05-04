package cn.kuship.console.infrastructure.region.repository;

/**
 * region_info 表的只读视图。
 *
 * <p>Schema 由 rainbond-console（Django）侧管控；本项目仅以 {@link org.springframework.jdbc.core.JdbcTemplate}
 * 直读，不引入 JPA {@code @Entity}。字段命名与表列名（snake_case）严格对应：
 * <pre>
 *   region_id      varchar(36) unique
 *   region_name    varchar(64) unique
 *   region_alias   varchar(64)
 *   region_type    varchar(64) nullable (json string, e.g. '[]')
 *   url            varchar(256)
 *   wsurl          varchar(256)
 *   httpdomain     varchar(256)
 *   tcpdomain      varchar(256)
 *   token          varchar(255) nullable
 *   status         varchar(2)
 *   scope          varchar(10) default 'private'
 *   ssl_ca_cert    text nullable
 *   cert_file      text nullable
 *   key_file       text nullable
 *   enterprise_id  varchar(36) nullable
 *   provider       varchar(24) nullable
 *   provider_cluster_id varchar(64) nullable
 * </pre>
 */
public record RegionInfoDto(
        String regionId,
        String regionName,
        String regionAlias,
        String regionType,
        String url,
        String wsurl,
        String httpDomain,
        String tcpDomain,
        String token,
        String status,
        String scope,
        String sslCaCert,
        String certFile,
        String keyFile,
        String enterpriseId,
        String provider,
        String providerClusterId
) {
}
