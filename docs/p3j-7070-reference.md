# P3-j 组件端口列表读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/ports?region_name=rainbond`
- rainbond: `AppPortView.get`。每端口 = `port.to_dict()`(tenant_services_port 12 列) + service_alias + environment + inner_url + outer_url + bind_domains +（非 http）bind_tcp_domains。
- environment：仅 is_inner_service，读 tenant_service_env_var(按 container_port)，项={desc:env.name, name:env.attr_name, value:env.attr_value}。
- inner_url：environment 非空 && inner → 解析 *_HOST/*_PORT → "host:port"（默认 host 127.0.0.1）。
- outer_url：is_outer_service 时 get_port_variables 算（tcp→tcpdomain:mapping/lb_mapping_port；http→httpdomain 复合:port）。
- bind_domains/bind_tcp_domains：region 网关 `routes/{http|tcp}/domains?service_alias=&port=`，据此回写 is_outer_service。
- **rainbond 有 `port.save()` 副作用持久化 is_outer**；kuship 读路径不 save（共享库已由 7070 GET 收敛）。复用 RegionClient(mTLS)。

## 校准结果（8000 vs 7070，team=default，组件 grc8ca26 + 内部 TCP 端口 5000）
```
字段顺序: True
DIFF: MATCH ✓
项: {ID,tenant_id,service_id,container_port,mapping_port,lb_mapping_port,protocol,port_alias,
     is_inner_service:true,is_outer_service:false,k8s_service_name,name:null,service_alias,
     environment:[{desc:连接地址,name:PARITYPORT_HOST,value:grc8ca26},{desc:端口,name:PARITYPORT_PORT,value:5000}],
     inner_url:"grc8ca26:5000", outer_url:"", bind_domains:[], bind_tcp_domains:[]}
```
- 探针经 [[kuship-region-component-provision]] 配方部署 + 加内部 TCP 端口（POST .../ports：port/protocol=tcp/port_alias/is_inner_service）。
- **坑**：加端口时 rainbond 自动设 is_inner=true（含自动生成 *_HOST/*_PORT env 变量）；首次 GET 的 port.save() 会按网关结果回写 is_outer，故校准在收敛态进行。校准毕删除组件+组，库恢复原状。
