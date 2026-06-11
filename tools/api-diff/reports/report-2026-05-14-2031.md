# kuship vs rainbond 接口对比 — 2026-05-14T20:31:54

- rainbond: `http://localhost:7070`
- kuship:   `http://localhost:8080`
- 总端点:    16
- 一致:      3
- 差异:      13

## 概览

| id | method | rb / kp HTTP | diff 行数 | 状态 |
|---|---|---|---|---|
| `team-arch` | GET | 200 / 200 | 0 | ✅ |
| `team-apps` | GET | 200 / 200 | 1 | ⚠️ |
| `team-groups-list` | GET | 200 / 200 | 2 | ⚠️ |
| `team-region-query` | GET | 400 / 200 | 1 | ⚠️ |
| `team-protocols` | GET | 200 / 200 | 2 | ⚠️ |
| `team-overview-groups` | GET | 200 / 200 | 0 | ✅ |
| `group-detail` | GET | 400 / 200 | 1 | ⚠️ |
| `group-status` | GET | 400 / 200 | 1 | ⚠️ |
| `group-component-names` | GET | 400 / 200 | 1 | ⚠️ |
| `group-governancemode` | GET | 400 / 200 | 1 | ⚠️ |
| `topological` | GET | 200 / 200 | 2 | ⚠️ |
| `operator-managed` | GET | 400 / 200 | 1 | ⚠️ |
| `service-group` | GET | 400 / 200 | 1 | ⚠️ |
| `upgradable-num` | GET | 400 / 200 | 1 | ⚠️ |
| `handle` | GET | 400 / 200 | 1 | ⚠️ |
| `storage-statistics` | GET | 200 / 200 | 0 | ✅ |

## `team-arch`  ✅ MATCH

**path**: `GET /console/teams/default/arch?region_name=rainbond`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "架构获取成功",
  "data": {
    "bean": {},
    "list": [
      "arm64"
    ]
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      "arm64"
    ]
  }
}
```

</details>

## `team-apps`  ⚠️ DIFF

**path**: `GET /console/teams/default/apps?region_name=rainbond&page=1&page_size=10`

**HTTP**: rainbond=`200` kuship=`200`

**diff**:
- data.list[0].update_time: rb=2026-05-09T20:36:04.071300 vs kp=2026-05-09T20:36:04.0713

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {
      "total": 1
    },
    "list": [
      {
        "group_id": 6,
        "update_time": "2026-05-09T20:36:04.071300",
        "create_time": "2026-05-09T20:36:04.071309",
        "group_name": "Nginx-ARM",
        "group_note": "",
        "used_mem": 128,
        "used_cpu": 250,
        "used_disk": 0,
        "status": "RUNNING",
        "logo": "",
        "accesses": [
          {
            "access_type": "http_port",
            "access_info": [
              {
                "ID": 1,
                "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
                "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
                "container_port": 80,
                "mapping_port": 80,
                "lb_mapping_port": 0,
                "protocol": "http",
                "port_alias": "GR248DC580",
                "is_inner_service": true,
                "is_outer_service": true,
                "k8s_service_name": "gr512dd5",
                "name": "",
                "service_cname": "Nginx-1.19",
                "access_urls": [
                  "http://gr512dd5-80-default.172.20.0.2.nip.io/"
                ]
              }
            ]
          }
        ],
        "services_num": 1,
        "run_service_num": 1,
        "allocate_mem": 128
      }
    ]
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "total": 1
    },
    "list": [
      {
        "group_id": 6,
        "update_time": "2026-05-09T20:36:04.0713",
        "create_time": "2026-05-09T20:36:04.071309",
        "group_name": "Nginx-ARM",
        "group_note": "",
        "used_mem": 128,
        "used_cpu": 250,
        "used_disk": 0,
        "status": "RUNNING",
        "logo": "",
        "accesses": [
          {
            "access_type": "http_port",
            "access_info": [
              {
                "ID": 1,
                "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
                "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
                "container_port": 80,
                "mapping_port": 80,
                "lb_mapping_port": 0,
                "protocol": "http",
                "port_alias": "GR248DC580",
                "is_inner_service": true,
                "is_outer_service": true,
                "k8s_service_name": "gr512dd5",
                "name": "",
                "service_cname": "Nginx-1.19",
                "access_urls": [
                  "http://gr512dd5-80-default.172.20.0.2.nip.io/"
                ]
              }
            ]
          }
        ],
        "services_num": 1,
        "run_service_num": 1,
        "allocate_mem": 128
      }
    ]
  }
}
```

</details>

## `team-groups-list`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups?region_name=rainbond`

**HTTP**: rainbond=`200` kuship=`200`

**diff**:
- data.list[0] only-in-rainbond keys: ['group_note']
- data.list[0] only-in-kuship keys: ['ID', 'app_id', 'app_type', 'create_time', 'governance_mode', 'k8s_app', 'logo', 'note', 'region_name', 'tenant_id', 'update_time']

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": [
      {
        "group_name": "Nginx-ARM",
        "group_id": 6,
        "group_note": ""
      }
    ]
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      {
        "app_id": 6,
        "group_id": 6,
        "ID": 6,
        "group_name": "Nginx-ARM",
        "note": "",
        "region_name": "rainbond",
        "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
        "k8s_app": "nginx-arm",
        "governance_mode": "KUBERNETES_NATIVE_SERVICE",
        "app_type": "rainbond",
        "logo": "",
        "create_time": "2026-05-09T20:36:04.071309",
        "update_time": "2026-05-09T20:36:04.0713"
      }
    ]
  }
}
```

</details>

## `team-region-query`  ⚠️ DIFF

**path**: `GET /console/teams/default/region/query`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      {
        "region_id": "d681b965a75e4843b95d6489d035177e",
        "region_name": "rainbond",
        "region_alias": "默认集群",
        "region_type": [],
        "url": "https://rbd-api-api:8443",
        "status": "1",
        "scope": "default"
      }
    ]
  }
}
```

</details>

## `team-protocols`  ⚠️ DIFF

**path**: `GET /console/teams/default/protocols?region_name=rainbond`

**HTTP**: rainbond=`200` kuship=`200`

**diff**:
- data.list length: rb=3 kp=5
- data.list[0]: rb=udp vs kp=HTTP

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": [
      "udp",
      "tcp",
      "http"
    ]
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      "HTTP",
      "HTTPS",
      "TCP",
      "UDP",
      "GRPC"
    ]
  }
}
```

</details>

## `team-overview-groups`  ✅ MATCH

**path**: `GET /console/teams/default/overview/groups?region_name=rainbond`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": [
      {
        "group_id": 6,
        "group_name": "Nginx-ARM",
        "service_list": [
          {
            "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
            "service_cname": "Nginx-1.19",
            "service_alias": "gr512dd5"
          }
        ]
      }
    ]
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      {
        "group_id": 6,
        "group_name": "Nginx-ARM",
        "service_list": [
          {
            "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
            "service_cname": "Nginx-1.19",
            "service_alias": "gr512dd5"
          }
        ]
      }
    ]
  }
}
```

</details>

## `group-detail`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "app_id": 6,
      "group_id": 6,
      "ID": 6,
      "group_name": "Nginx-ARM",
      "note": "",
      "region_name": "rainbond",
      "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
      "k8s_app": "nginx-arm",
      "governance_mode": "KUBERNETES_NATIVE_SERVICE",
      "app_type": "rainbond",
      "logo": "",
      "create_time": "2026-05-09T20:36:04.071309",
      "update_time": "2026-05-09T20:36:04.0713"
    },
    "list": []
  }
}
```

</details>

## `group-status`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6/status`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "app_id": 6,
      "component_count": 1,
      "create_status_summary": [
        "complete"
      ]
    },
    "list": []
  }
}
```

</details>

## `group-component-names`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6/component_names`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      {
        "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
        "service_alias": "gr512dd5",
        "service_cname": "Nginx-1.19",
        "k8s_component_name": "nginx19"
      }
    ]
  }
}
```

</details>

## `group-governancemode`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6/governancemode`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "governance_mode": "KUBERNETES_NATIVE_SERVICE"
    },
    "list": []
  }
}
```

</details>

## `topological`  ⚠️ DIFF

**path**: `GET /console/teams/default/regions/rainbond/topological?group_id=6`

**HTTP**: rainbond=`200` kuship=`200`

**diff**:
- data.bean only-in-rainbond keys: ['json_data', 'json_svg']
- data.bean only-in-kuship keys: ['group_id', 'group_name', 'region_status', 'services']

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "Obtain topology success.",
  "msg_show": "获取拓扑图成功",
  "data": {
    "bean": {
      "json_data": {
        "605f3cf7250b4ca49a3a0df8b6512dd5": {
          "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
          "service_cname": "Nginx-1.19",
          "service_alias": "gr512dd5",
          "service_source": "market",
          "component_memory": 128,
          "node_num": 1,
          "app_id": 6,
          "app_type": "rainbond",
          "app_name": "Nginx-ARM",
          "app_status": "RUNNING",
          "cur_status": "running",
          "status_cn": "运行中",
          "is_internet": true
        }
      },
      "json_svg": {
        "605f3cf7250b4ca49a3a0df8b6512dd5": []
      }
    },
    "list": []
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "group_id": 6,
      "group_name": "Nginx-ARM",
      "services": [
        {
          "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
          "service_alias": "gr512dd5",
          "service_cname": "Nginx-1.19",
          "extend_method": "stateless_multiple",
          "service_region": "rainbond"
        }
      ],
      "region_status": {}
    },
    "list": []
  }
}
```

</details>

## `operator-managed`  ⚠️ DIFF

**path**: `GET /console/teams/default/operator-managed?group_id=6`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "service": []
    },
    "list": []
  }
}
```

</details>

## `service-group`  ⚠️ DIFF

**path**: `GET /console/teams/default/service/group?group_id=6&page=1&page_size=100`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {},
    "list": [
      {
        "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
        "service_alias": "gr512dd5",
        "create_status": "complete",
        "service_cname": "Nginx-1.19",
        "service_type": "application",
        "deploy_version": "20230830215047",
        "version": "1.19",
        "update_time": "2026-05-09T20:36:06.565209",
        "min_memory": 128,
        "group_name": "Nginx-ARM",
        "k8s_service_name": "gr512dd5",
        "service_source": "market",
        "status": "unknow",
        "status_cn": "未知",
        "disabledAction": [],
        "activeAction": []
      }
    ],
    "total": 1
  }
}
```

</details>

## `upgradable-num`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6/upgradable_num`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "upgradable_num": 0
    },
    "list": []
  }
}
```

</details>

## `handle`  ⚠️ DIFF

**path**: `GET /console/teams/default/groups/6/handle`

**HTTP**: rainbond=`400` kuship=`200`

**diff**:
- HTTP status: rb=400 kp=200

<details><summary>rainbond response</summary>

```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "services_info": [],
      "k8s_resources": [],
      "domains": [],
      "config_groups": [],
      "app_share_records": []
    },
    "list": []
  }
}
```

</details>

## `storage-statistics`  ✅ MATCH

**path**: `GET /console/storage_statistics?region_name=rainbond`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "获取存储统计成功",
  "data": {
    "bean": {
      "used_storage": {
        "value": 0,
        "unit": "B"
      }
    },
    "list": []
  }
}
```

</details>

<details><summary>kuship response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "OK",
  "data": {
    "bean": {
      "used_storage": {
        "value": 0,
        "unit": "B"
      }
    },
    "list": []
  }
}
```

</details>
