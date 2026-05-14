## 1. region API method 实现（ServiceVolumeOperationsImpl）

- [x] 1.1 实现 `getVolumeOptions(regionName, tenantName)`：`GET /v2/volume-options`（无 tenant 路径段）→ `safeBean(processor.extractBean(...))`
- [x] 1.2 实现 `getVolumes(regionName, tenantName, serviceAlias)`：`GET /v2/tenants/{t}/services/{a}/volumes?enterprise_id=`（enterprise_id 暂留空）
- [x] 1.3 实现 `getVolumeStatus(regionName, tenantName, serviceAlias)`：`GET /v2/tenants/{t}/services/{a}/volumes-status`
- [x] 1.4 实现 `getDepVolumes(regionName, tenantName, serviceAlias)`：`GET /v2/tenants/{t}/services/{a}/depvolumes`
- [x] 1.5 实现 `addDepVolumes(regionName, tenantName, serviceAlias, body)`：`POST /v2/tenants/{t}/services/{a}/depvolumes`
- [x] 1.6 实现 `deleteDepVolumes(regionName, tenantName, serviceAlias, body)`：`DELETE /v2/tenants/{t}/services/{a}/depvolumes` + body（含 Content-Type: application/json）

## 2. Entity：TenantServiceMountRelation

- [x] 2.1 新建 `modules/application/entity/TenantServiceMountRelation.java`：`@Entity @Table("tenant_service_mnt_relation")`，字段 `ID`（PK auto）/ `tenantId` / `serviceId` / `depServiceId` / `mntName`(100) / `mntDir`(400)

## 3. Repository：TenantServiceMountRelationRepository

- [x] 3.1 新建 `modules/application/repository/TenantServiceMountRelationRepository extends JpaRepository<TenantServiceMountRelation, Integer>`
- [x] 3.2 添加方法：`List<> findByTenantIdAndServiceId(String tenantId, String serviceId)`
- [x] 3.3 添加方法：`Optional<> findByServiceIdAndDepServiceIdAndMntName(String serviceId, String depServiceId, String mntName)`
- [x] 3.4 添加方法：`List<> findByTenantIdAndDepServiceId(String tenantId, String depServiceId)`
- [x] 3.5 添加方法：`void deleteByServiceIdAndDepServiceIdAndMntName(String serviceId, String depServiceId, String mntName)`

## 4. Service：AppMntService

- [x] 4.1 新建 `modules/application/service/AppMntService.java` + `@Service`，注入 `TenantServiceMountRelationRepository` + `TenantServiceVolumeRepository` + `TenantServiceRepository` + `ServiceVolumeOperations` + `ServiceGroupRelationRepository` + `ServiceGroupRepository`
- [x] 4.2 实现 `List<Map<String,Object>> getMounted(String tenantId, String serviceId)`：join mnt_relation + volume + dep_service，返回 `{local_vol_path, dep_vol_name, dep_vol_path, dep_vol_type, dep_app_name, dep_app_group, dep_vol_id, dep_group_id, dep_app_alias}` 形状（对齐 `mnt_service.get_service_mnt_details` Python 锚点）
- [x] 4.3 实现 `List<Map<String,Object>> getUnmounted(String tenantId, String serviceId, String regionName, String depAppGroup, String configName)`：查租户下其他组件 RWX 存储（排除 config-file / local-path / 有状态组件存储 / 已挂载），返回同形状（对齐 `mnt_service.get_service_unmount_volume_list`）
- [x] 4.4 实现 `void addMnt(TenantService service, String tenantName, String tenantId, TenantServiceVolume depVolume, String localPath)`：先调 region `addDepVolumes`，后 INSERT mnt_relation；region 失败不阻止本地写入
- [x] 4.5 实现 `void deleteMnt(TenantService service, String tenantName, String tenantId, Integer depVolId)`：查 depVolume → 调 region `deleteDepVolumes`（404 ignore）→ 删本地 mnt_relation

## 5. Controller：AppMntController

- [x] 5.1 新建 `modules/application/controller/AppMntController.java`：`@RestController @RequestMapping("/console/teams/{team_name}/apps/{service_alias}/mnt")`
- [x] 5.2 `GET {"", "/"}` with `@RequestParam(defaultValue="mnt") String type`：`mnt` → `service.getMounted()`；`unmnt` → `service.getUnmounted()`；其他 400；`@RequirePerm(APP_OVERVIEW_DESCRIBE)`
- [x] 5.3 `POST {"", "/"}` + `@RequestBody Map<String, Object> body`：body 必含 `body` key（JSON 数组字符串）；batch 挂载；`@RequirePerm(APP_CREATE_PERMS)`；`@Transactional`
- [x] 5.4 `DELETE {"/{dep_vol_id}", "/{dep_vol_id}/"}` + `@PathVariable Integer depVolId`：取消挂载；`@RequirePerm(APP_CREATE_PERMS)`；`@Transactional`
- [x] 5.5 响应 `GET mnt` → `GeneralMessage.okList(list)`，`GET unmnt` → `GeneralMessage.okList(list)`，`POST` → `GeneralMessage.ok()`，`DELETE` → `GeneralMessage.ok()`

## 6. 测试

- [x] 6.1 新建 `src/test/java/cn/kuship/console/modules/application/volume/ServiceVolumeOperationsImplTest.java`：`MockRestServiceServer` 单测 6 个 method（mock region 200 响应 `{"bean":{},"list":[]}`）
- [x] 6.2 新建 `src/test/java/cn/kuship/console/modules/application/integration/AppMntIntegrationTest.java`：`@SpringBootTest + @MockitoBean ServiceVolumeOperations`，seed `tenant_service_mnt_relation` fixture，断言 GET mnt 返回 `data.list`，断言 POST 写入 mnt_relation，断言 DELETE 删除 mnt_relation

## 7. 编译与验证

- [x] 7.1 `cd kuship-console && mvn -DskipTests package` 编译通过
- [x] 7.2 `cd kuship-console && mvn -Dtest='ServiceVolumeOperationsImplTest' test` 通过（6/6）；AppMntIntegrationTest 需真实 DB（与 AppEnvIntegrationTest 一致，本机无 local.yaml）
- [ ] 7.3 curl 验证（需用户联动验证）：`curl -s http://localhost:8080/console/teams/{team}/apps/{alias}/mnt?type=mnt -H "Authorization: GRJWT ..."  | jq .`

## 8. OpenSpec specs

- [x] 8.1 写 `specs/kuship-console-app/spec.md` 新增 2 条 ADDED Requirement
