## 1. 联调与参照准备

- [x] 1.1 确认环境 + 已实测 7070 应用列表（interop default 空 `list:[]`）；项形态 `{group_name, group_id, group_note}` 取自 TenantGroupView 源码
- [x] 1.2 记 `docs/p2a-7070-reference.md`：列表项形态、排序、空/非空校准方案

## 2. 实体与仓储

- [x] 2.1 `ServiceGroup` 只读实体（@Table service_group，映射 id/tenant_id/group_name/region_name/note/order_index/update_time）
- [x] 2.2 `ServiceGroupRepository.findByTenantIdAndRegionNameOrderByUpdateTimeDescOrderIndexDesc`

## 3. 服务与接口

- [x] 3.1 `AppReadService.listApps(tenantId, regionName)`：查 service_group → `[{group_name, group_id, group_note}]`
- [x] 3.2 `AppController` GET /console/teams/{team_name}/groups（region_name 参数；general_message list/查询成功）
- [x] 3.3 单测：映射 group_id=ID/group_note=note、排序、空列表

## 4. 实跑校准与收尾

- [x] 4.1 空列表 live 校准（8000 vs 7070 default）
- [x] 4.2 非空校准：DB 直插临时 service_group 行（default tenant_id, region rainbond）→ 8000 与 7070 deep-diff 一致 → 删除临时行
- [x] 4.3 全量构建 + 单测；docs 记结论；清理临时数据
- [x] 4.4 openspec 校验，准备归档
