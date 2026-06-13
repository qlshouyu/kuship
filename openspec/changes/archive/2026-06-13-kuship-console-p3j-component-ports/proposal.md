## Why
region 组件域续：端口列表读 `teams/{team}/apps/{serviceAlias}/ports`（`AppPortView.get`）。每端口 = port.to_dict + 内部连接环境变量 + inner/outer url + region 网关绑定域名。用可丢弃组件+一个内部 TCP 端口对 7070 校准（稳定字段）。

## What Changes
- 端口列表（对齐 `AppPortView.get`）：`GET /console/teams/{tenantName}/apps/{serviceAlias}/ports?region_name=` → `list`=[每端口]，每项=`port.to_dict()`(12 列)+service_alias+environment(inner service 的连接变量 {desc=env.name,name=attr_name,value=attr_value})+inner_url(HOST:PORT)+outer_url(outer 时 tcpdomain:port / httpdomain 复合)+bind_domains+(非 http 时)bind_tcp_domains；http/tcp 经 region 网关 `routes/{http|tcp}/domains` 取绑定并据此定 is_outer_service。`msg_show=查询成功`。
- **读路径不执行 rainbond 的 `port.save()` 副作用**（is_outer_service 显示值在内存按网关结果计算；共享库已由 7070 收敛）。
- **不包含**：端口增删改、http bind_domains 的非空分支深度校准（探针用关闭外网的 TCP 端口）。

## Capabilities
### New Capabilities
- `component-ports-read`: 组件端口列表读（port.to_dict + env 连接变量 + inner/outer url + region 网关绑定域名）。

## Impact
- 代码：TenantServicesPort/TenantServiceEnvVar 实体+repo；ComponentPortsService；ComponentPortsController。
- 数据：只读 tenant_services_port/tenant_service_env_var/tenant_service/tenant_info/region_info + region 网关；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：内部 TCP 端口 8000 vs 7070 逐字节一致（is_outer/bind_* 经网关收敛）。dev 需 REGION_URL_OVERRIDE。
