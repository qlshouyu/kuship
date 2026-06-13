# P3-i 组件详情读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/detail?region_name=rainbond`
- rainbond: `AppDetailView.get`（非 vm/非 market 路径）—— **纯 DB，无 region 运行态调用**。
- `bean`={service, event_websocket_url, is_third}：
  - `service` = `TenantServiceInfo.to_dict()`（BaseModel.to_dict 全 63 列、model 声明顺序、datetime→'%Y-%m-%d %H:%M:%S'）+ 末尾 `group_name`/`group_id`/`disk_cap`；`namespace` 用 `tenant.namespace` 原位覆盖。
  - 组名：service_group_relation→service_group（无关系 → group_name="未分组"、group_id=-1）。
  - disk_cap：vm?30:10（有 volume 则 volumes[0].volume_capacity，本期 defer）。
  - event_websocket_url：region.wsurl=="auto"?`ws://{host}:6060/event_log`:`{wsurl}/event_log`（本环境 wsurl=ws://172.20.0.2:6060）。
  - is_third：service_source=="third_party"。

## 校准结果（8000 vs 7070，team=default，镜像组件 grf5ab6b，未部署即可）
```
service 字段数 7070=66 8000=66  顺序一致=True   (63 to_dict + group_name/group_id/disk_cap)
DIFF: MATCH ✓   (含 event_websocket_url=ws://172.20.0.2:6060/event_log、is_third=false)
```
- 探针组件经 [[kuship-region-component-provision]] 配方**仅创建**（detail 纯 DB 无需部署），校准毕已删除组件+空组，库恢复原状。
- 实现：`TenantServiceInfo` 扩为全 63 列 + `toDict()`(LinkedHashMap 精确顺序，datetime 空格格式)；`desc` 列用 `@Column(name="`desc`")` 反引号转义；ServiceGroupRelation 实体。
