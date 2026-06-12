# P1-a 接口 7070 参照响应（校准基线）

> 环境：rainbond-console v6.9.0-release(7070)，共享 console 库；测试用户 interop(user_id=700002)，
> 企业 b16bf28d35cfde02d5a72e5038a59d79(KuShip)，团队 default(owner=700002, region=rainbond)。
> 用途：kuship-console(8000) 各接口实现后逐字段对照本文件。

## GET /console/users/details
```json
{
  "code": 200,
  "msg": "Obtain my details to be successful.",
  "msg_show": "获取我的详情成功",
  "data": {
    "bean": {
      "user_id": 700002,
      "user_name": "interop",
      "real_name": "",
      "logo": null,
      "email": "interop@kuship.cn",
      "enterprise_id": "b16bf28d35cfde02d5a72e5038a59d79",
      "phone": "13900000000",
      "is_sys_admin": true,
      "is_enterprise_active": 0,
      "is_enterprise_admin": true,
      "is_initial_enterprise_admin": true,
      "roles": [
        "admin"
      ],
      "permissions": [
        "app_store.create_app_store",
        "app_store.get_ent_teams",
        "app_store.delete_app",
        "app_store.edit_app_version",
        "app_store.delete_app_version",
        "app_store.edit_app",
        "app_store.import_app",
        "app_store.get_app_store",
        "app_store.edit_app_store",
        "app_store.create_app",
        "app_store.export_app",
        "app_store.delete_app_store"
      ],
      "teams": [
        {
          "team_id": 1,
          "team_name": "default",
          "team_alias": "interop 工作空间",
          "limit_memory": 0,
          "region": [
            {
              "service_status": 1,
              "is_active": true,
              "region_status": "1",
              "team_region_alias": "默认集群",
              "region_tenant_id": "a274b41cfac64b84b9e3dbc9f28a83b8",
              "team_region_name": "rainbond",
              "region_scope": "default",
              "region_create_time": "2026-06-12T19:52:31",
              "websocket_uri": "ws://172.20.0.2:6060",
              "tcpdomain": "172.20.0.2",
              "region_id": "beb16025f33644c88b79e62eb05f50f6"
            }
          ],
          "creater": 700002,
          "create_time": "2026-06-12T21:09:00.923764",
          "namespace": "default",
          "role_name_list": [
            {
              "role_id": "1",
              "role_name": "管理员"
            }
          ],
          "tenant_actions": {
            "team": {
              "sub_models": [
                {
                  "team_overview": {
                    "sub_models": [],
                    "perms": [
                      {
                        "describe": true
                      },
                      {
                        "app_list": true
                      }
                    ]
                  }
                },
                {
                  "team_app_create": {
                    "sub_models": [],
                    "perms": [
                      {
                        "describe": true
                      }
                    ]
                  }
                },
                {
                  "team_app_manage": {
                    "sub_models": [],
                    "perms": {}
                  }
                },
                {
                  "team_gateway_manage": {
                    "sub_models": [
                      {
                        "team_gateway_monitor": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            }
                          ]
                        }
                      },
                      {
                        "team_route_manage": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      },
                      {
                        "team_target_services": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      },
                      {
                        "team_certificate": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      }
                    ],
                    "perms": []
                  }
                },
                {
                  "team_plugin_manage": {
                    "sub_models": [],
                    "perms": [
                      {
                        "describe": true
                      },
                      {
                        "create": true
                      },
                      {
                        "edit": true
                      },
                      {
                        "delete": true
                      }
                    ]
                  }
                },
                {
                  "team_manage": {
                    "sub_models": [
                      {
                        "team_dynamic": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            }
                          ]
                        }
                      },
                      {
                        "team_member": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      },
                      {
                        "team_region": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "install": true
                            },
                            {
                              "uninstall": true
                            }
                          ]
                        }
                      },
                      {
                        "team_role": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      },
                      {
                        "team_registry_auth": {
                          "sub_models": [],
                          "perms": [
                            {
                              "describe": true
                            },
                            {
                              "create": true
                            },
                            {
                              "edit": true
                            },
                            {
                              "delete": true
                            }
                          ]
                        }
                      }
                    ],
                    "perms": []
                  }
                }
              ],
              "perms": []
            }
          },
          "is_team_owner": true
        }
      ],
      "oauth_services": []
    },
    "list": []
  }
}
```

## GET /console/enterprises
```json
{
  "code": 200,
  "msg": "success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": [
      {
        "ID": 1,
        "enterprise_alias": "KuShip",
        "enterprise_name": "qo8wzjsp",
        "is_active": 0,
        "enterprise_id": "b16bf28d35cfde02d5a72e5038a59d79",
        "enterprise_token": "",
        "create_time": "2026-06-12T21:09:00.916691",
        "enable_team_resource_view": true
      }
    ]
  }
}
```

## GET /console/enterprise/b16bf28d35cfde02d5a72e5038a59d79/overview
```json
{
  "code": 200,
  "msg": "success",
  "msg_show": null,
  "data": {
    "bean": {
      "shared_apps": 0,
      "total_teams": 1,
      "total_users": 1
    },
    "list": []
  }
}
```

## GET /console/enterprise/b16bf28d35cfde02d5a72e5038a59d79/teams
```json
{
  "code": 200,
  "msg": "success",
  "msg_show": null,
  "data": {
    "bean": {
      "total_count": 1,
      "page": 1,
      "page_size": 10,
      "list": [
        {
          "ID": 1,
          "tenant_id": "a274b41cfac64b84b9e3dbc9f28a83b8",
          "tenant_name": "default",
          "is_active": true,
          "create_time": "2026-06-12 21:09:00",
          "creater": 700002,
          "limit_memory": 0,
          "update_time": "2026-06-12 21:09:00",
          "tenant_alias": "interop 工作空间",
          "enterprise_id": "b16bf28d35cfde02d5a72e5038a59d79",
          "namespace": "default",
          "logo": "",
          "region": "rainbond",
          "region_list": [
            {
              "region_name": "rainbond",
              "region_alias": "默认集群",
              "region_id": "beb16025f33644c88b79e62eb05f50f6"
            }
          ],
          "team_alias": "interop 工作空间",
          "team_name": "default",
          "user_number": 1,
          "owner_name": "interop",
          "set_limit_memory": 0,
          "set_limit_cpu": 0,
          "set_limit_storage": 0,
          "running_apps": 0,
          "memory_request": 0,
          "cpu_request": 0,
          "storage_request": 0
        }
      ]
    },
    "list": []
  }
}
```

## GET /console/enterprise/b16bf28d35cfde02d5a72e5038a59d79/user/700002/teams
```json
{
  "code": 200,
  "msg": "team query success",
  "msg_show": "查询成功",
  "data": {
    "bean": {},
    "list": [
      {
        "team_name": "default",
        "team_alias": "interop 工作空间",
        "team_id": "a274b41cfac64b84b9e3dbc9f28a83b8",
        "create_time": "2026-06-12T21:09:00.923764",
        "enterprise_id": "b16bf28d35cfde02d5a72e5038a59d79",
        "owner": 700002,
        "owner_name": "interop",
        "logo": "",
        "roles": [
          "管理员",
          "owner"
        ],
        "region": "rainbond",
        "region_list": [
          {
            "region_name": "rainbond",
            "region_alias": "默认集群"
          }
        ],
        "app_count": 0,
        "service_count": 0
      }
    ]
  }
}
```

## GET /console/teams/default/overview
```json
{
  "code": 400,
  "msg": "",
  "msg_show": "请求参数不全"
}
```

