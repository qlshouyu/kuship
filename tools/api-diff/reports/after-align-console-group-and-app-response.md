# kuship vs rainbond 接口对比 — 2026-05-14T21:16:27

- rainbond: `http://localhost:7070`
- kuship:   `http://localhost:8080`
- 总端点:    16
- 一致:      16
- 差异:      0

## 概览

| id | method | rb / kp HTTP | diff 行数 | 状态 |
|---|---|---|---|---|
| `team-arch` | GET | 200 / 200 | 0 | ✅ |
| `team-apps` | GET | 200 / 200 | 0 | ✅ |
| `team-groups-list` | GET | 200 / 200 | 0 | ✅ |
| `team-region-query` | GET | 200 / 200 | 0 | ✅ |
| `team-protocols` | GET | 200 / 200 | 0 | ✅ |
| `team-overview-groups` | GET | 200 / 200 | 0 | ✅ |
| `group-detail` | GET | 200 / 200 | 0 | ✅ |
| `group-status` | GET | 200 / 200 | 0 | ✅ |
| `group-component-names` | GET | 200 / 200 | 0 | ✅ |
| `group-governancemode` | GET | 200 / 200 | 0 | ✅ |
| `topological` | GET | 200 / 200 | 0 | ✅ |
| `operator-managed` | GET | 200 / 200 | 0 | ✅ |
| `service-group` | GET | 200 / 200 | 0 | ✅ |
| `upgradable-num` | GET | 200 / 200 | 0 | ✅ |
| `handle` | GET | 200 / 200 | 0 | ✅ |
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

## `team-apps`  ✅ MATCH

**path**: `GET /console/teams/default/apps?region_name=rainbond&page=1&page_size=10`

**HTTP**: rainbond=`200` kuship=`200`

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

## `team-groups-list`  ✅ MATCH

**path**: `GET /console/teams/default/groups?region_name=rainbond`

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
        "group_id": 6,
        "group_name": "Nginx-ARM",
        "group_note": ""
      }
    ]
  }
}
```

</details>

## `team-region-query`  ✅ MATCH

**path**: `GET /console/teams/default/region/query`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "query the data center is successful.",
  "msg_show": "数据中心获取成功",
  "data": {
    "bean": {},
    "list": [
      {
        "region_id": 1,
        "region_name": "rainbond",
        "service_status": 1,
        "is_active": true,
        "is_init": true,
        "region_scope": "default",
        "region_alisa": "默认集群",
        "region.region_tenant_id": "abd4ec9968db4d57b0246c6444e63848",
        "create_time": "2026-05-08T20:42:10.684312",
        "desc": "当前集群是默认安装添加的集群"
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
        "region_id": 1,
        "region_name": "rainbond",
        "service_status": 1,
        "is_active": true,
        "is_init": true,
        "region_scope": "default",
        "region_alisa": "默认集群",
        "region.region_tenant_id": "abd4ec9968db4d57b0246c6444e63848",
        "create_time": "2026-05-08T20:42:10.684312",
        "desc": "当前集群是默认安装添加的集群"
      }
    ]
  }
}
```

</details>

## `team-protocols`  ✅ MATCH

**path**: `GET /console/teams/default/protocols?region_name=rainbond`

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
      "udp",
      "tcp",
      "http"
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

## `group-detail`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "success",
  "data": {
    "bean": {
      "ID": 6,
      "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
      "group_name": "Nginx-ARM",
      "region_name": "rainbond",
      "is_default": false,
      "order_index": 0,
      "note": "",
      "username": "admin",
      "governance_mode": "KUBERNETES_NATIVE_SERVICE",
      "create_time": "2026-05-09 20:36:04",
      "update_time": "2026-05-09 20:36:04",
      "app_type": "rainbond",
      "app_store_name": null,
      "app_store_url": null,
      "app_template_name": null,
      "version": null,
      "logo": "",
      "k8s_app": "nginx-arm",
      "region_app_id": "7c14146052d44d86ba2854ac97428b31",
      "namespace": "default",
      "app_id": 6,
      "app_name": "Nginx-ARM",
      "service_num": 1,
      "share_num": 0,
      "resources_num": 0,
      "ingress_num": 1,
      "config_group_num": 0,
      "can_edit": false,
      "app_arch": [
        "arm64"
      ],
      "principal": "admin",
      "email": "egojit@qq.com",
      "create_status": "complete",
      "compose_id": null
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
      "ID": 6,
      "tenant_id": "abd4ec9968db4d57b0246c6444e63848",
      "group_name": "Nginx-ARM",
      "region_name": "rainbond",
      "is_default": false,
      "order_index": 0,
      "note": "",
      "username": "admin",
      "governance_mode": "KUBERNETES_NATIVE_SERVICE",
      "create_time": "2026-05-09 20:36:04",
      "update_time": "2026-05-09 20:36:04",
      "app_type": "rainbond",
      "app_store_name": null,
      "app_store_url": null,
      "app_template_name": null,
      "version": null,
      "logo": "",
      "k8s_app": "nginx-arm",
      "region_app_id": "7c14146052d44d86ba2854ac97428b31",
      "namespace": "default",
      "app_id": 6,
      "app_name": "Nginx-ARM",
      "service_num": 1,
      "share_num": 0,
      "resources_num": 0,
      "ingress_num": 1,
      "config_group_num": 0,
      "can_edit": false,
      "principal": "admin",
      "email": "egojit@qq.com",
      "create_status": "complete",
      "compose_id": null,
      "app_arch": [
        "arm64"
      ]
    },
    "list": []
  }
}
```

</details>

## `group-status`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6/status`

**HTTP**: rainbond=`200` kuship=`200`

**suppressed (合规已知差异)**:
- data.list shape: rb=dict kp=list

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": {
      "app_id": "7c14146052d44d86ba2854ac97428b31",
      "app_name": "Nginx-ARM",
      "status": "RUNNING",
      "cpu": 250,
      "gpu": null,
      "memory": 128,
      "disk": 0,
      "phase": "",
      "version": "",
      "overrides": null,
      "conditions": null,
      "k8s_app": "nginx-arm"
    }
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
        "app_id": "6",
        "app_name": "Nginx-ARM",
        "status": "RUNNING",
        "cpu": 250,
        "gpu": null,
        "memory": 128,
        "disk": 0,
        "phase": "",
        "version": "",
        "overrides": null,
        "conditions": null,
        "k8s_app": "nginx-arm"
      }
    ]
  }
}
```

</details>

## `group-component-names`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6/component_names`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {
      "component_names": [
        "nginx19"
      ]
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
      "component_names": [
        {
          "service_id": "605f3cf7250b4ca49a3a0df8b6512dd5",
          "service_alias": "gr512dd5",
          "service_cname": "Nginx-1.19",
          "k8s_component_name": "nginx19"
        }
      ]
    },
    "list": []
  }
}
```

</details>

## `group-governancemode`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6/governancemode`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "获取成功",
  "data": {
    "bean": {},
    "list": [
      {
        "name": "KUBERNETES_NATIVE_SERVICE",
        "is_default": true,
        "description": "该模式组件间使用Kubernetes service名称域名进行通信，用户需要配置每个组件端口注册的service名称，治理能力有限"
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
        "name": "KUBERNETES_NATIVE_SERVICE",
        "is_default": true,
        "description": "该模式组件间使用Kubernetes service名称域名进行通信，用户需要配置每个组件端口注册的service名称，治理能力有限"
      }
    ]
  }
}
```

</details>

## `topological`  ✅ MATCH

**path**: `GET /console/teams/default/regions/rainbond/topological?group_id=6`

**HTTP**: rainbond=`200` kuship=`200`

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

## `operator-managed`  ✅ MATCH

**path**: `GET /console/teams/default/operator-managed?group_id=6`

**HTTP**: rainbond=`200` kuship=`200`

**suppressed (合规已知差异)**:
- data.bean only-in-kuship keys: ['service']
- data.list shape: rb=dict kp=list

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "获取成功",
  "data": {
    "bean": {},
    "list": {
      "service": []
    }
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
      "service": []
    },
    "list": []
  }
}
```

</details>

## `service-group`  ✅ MATCH

**path**: `GET /console/teams/default/service/group?group_id=6&page=1&page_size=100`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "query success",
  "msg_show": "应用查询成功",
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
        "status_cn": "运行中",
        "status": "running",
        "disabledAction": [
          "restart"
        ],
        "activeAction": [
          "stop",
          "deploy",
          "visit",
          "manage_container",
          "reboot"
        ]
      }
    ],
    "total": 1
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
        "status": "running",
        "status_cn": "运行中",
        "disabledAction": [
          "restart"
        ],
        "activeAction": [
          "stop",
          "deploy",
          "visit",
          "manage_container",
          "reboot"
        ]
      }
    ],
    "total": 1
  }
}
```

</details>

## `upgradable-num`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6/upgradable_num`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "success",
  "data": {
    "bean": {
      "upgradable_num": 0
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
      "upgradable_num": 0
    },
    "list": []
  }
}
```

</details>

## `handle`  ✅ MATCH

**path**: `GET /console/teams/default/groups/6/handle`

**HTTP**: rainbond=`200` kuship=`200`

<details><summary>rainbond response</summary>

```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "success",
  "data": {
    "bean": {
      "services_info": [
        {
          "service_name": "Nginx-1.19",
          "volume": [],
          "is_related": false,
          "status": "running"
        }
      ],
      "k8s_resources": [],
      "domains": [
        "gr512dd5-80-default.172.20.0.2.nip.io"
      ],
      "config_groups": [],
      "app_share_records": []
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
      "services_info": [
        {
          "service_name": "Nginx-1.19",
          "volume": [],
          "is_related": false,
          "status": "running"
        }
      ],
      "k8s_resources": [],
      "domains": [
        "gr512dd5-80-default.172.20.0.2.nip.io"
      ],
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
