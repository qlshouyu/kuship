# P2-e（企业集群列表读，safe 级）7070 校准基线
- `GET /console/enterprise/{eid}/regions?status=`（check_status 默认空→不调 region 后端），msg_show=获取成功。
- 项 22 字段：region_id/region_alias/region_name/status/region_type(JSON→list,空→[])/enterprise_id/url/scope/provider/provider_cluster_id/desc + 资源默认(total/used_memory/cpu/disk=0, rbd_version="unknown", health_status="ok", resource_proxy_status=false) + create_time(ISO) + enterprise_alias(由 eid 查 tenant_enterprise)。
- safe 级不含 wsurl/httpdomain/tcpdomain/ssl_ca_cert/cert_file/key_file/token（仅 open 级）。
- 源：__init_region_resource_data(level=safe)，纯 DB(region_info 列)+固定默认。check_status=yes 才调 region_api 拉实时资源(留后续)。
- **坑**：kuship RegionConfig 是手写 getter(非 Lombok)，原缺 getRegionType/getDesc/getScope/getProviderClusterId——已补。region_type 用极简手写 JSON 数组解析(无 jackson-databind 编译依赖)。scope 直接取 region.scope(IS_STANDALONE env 覆盖暂不处理，实测一致)。
- 校准：真实 rainbond 集群 deep-diff 8000 vs 7070 全 0 ✓。单测 95/95。
