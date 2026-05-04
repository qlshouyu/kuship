# kuship项目
kuship是一个基于rainbond的kubernetes的云原生服务托管管理项目，主要是rainbond-console使用java springboot技术栈进行实现

# 目录结构
```
kuship/
├── CLAUDE.md                    # 项目说明（供 Claude Code 使用）
├── README.md                    # 项目自述文件
├── standalone_build.sh          # standalone 镜像构建入口脚本
├── rke2_build.sh                # RKE2 多节点离线包构建编排入口
├── k3s-images-arm64.tar.zst     # k3s 离线镜像包（arm64）
├── rke2-bundle-<arch>.tar.zst   # RKE2 离线包（构建产物，不入库）
├── .gitmodules                  # Git 子模块配置（rainbond-console / rainbond-ui / rainbond-chart）
│
├── docker/                      # Docker 相关构建产物或配置
│
├── standalone/                  # standalone 单机部署相关资源
│   ├── Dockerfile               # standalone 镜像构建文件
│   ├── entrypoint.sh            # 容器启动入口脚本
│   ├── images-package.sh        # 离线镜像打包脚本
│   ├── config.yaml              # k3s 配置
│   └── registries.yaml          # 镜像仓库配置
│
├── rke2/                        # 多节点 RKE2 离线部署资源
│   ├── README.md                # RKE2 部署说明（端口表 / 步骤 / 故障排查）
│   ├── rke2-version.env         # RKE2 版本钉死单一真相源
│   ├── images-package.sh        # 离线包打包脚本（拉 RKE2 二进制 + 镜像 + chart）
│   ├── server-install.sh        # server 节点安装：systemd 服务 + bootstrap rainbond
│   ├── agent-install.sh         # agent 节点加入（依赖 RKE2_URL / RKE2_TOKEN env）
│   ├── bootstrap-rainbond.sh    # 渲染 rainbond-cluster HelmChart manifest
│   ├── config-server.yaml       # RKE2 server 配置（disable ingress / CIDR / tls-san）
│   ├── config-agent.yaml        # RKE2 agent 配置（server / token 由 install 脚本注入）
│   └── registries.yaml          # containerd 镜像仓库配置（goodrain.me）
│
├── openspec/                    # OpenSpec 规范与变更管理
│   ├── config.yaml              # OpenSpec 配置
│   ├── specs/                   # 已归档的能力规范
│   └── changes/                 # 进行中的变更提案
│
├── reference/                   # 参考代码（git submodule，只读）
│   ├── rainbond-console/        # Rainbond 控制台后端（参考实现）
│   ├── rainbond-ui/             # Rainbond 控制台前端
│   └── rainbond-chart/          # Rainbond Helm Chart（含 CRD 与模板）
│
├── kuship-console/              # Java/Spring Boot 4.0.6 + JPA + Java 21 后端，替代 rainbond-console
│                                #   与 rainbond-console 共享 MySQL `console` 库，URL 严格保持 /console/*
│                                #   契约层（响应/异常/JWT/分页/TraceId）已就绪
│                                #   详见 kuship-console/CLAUDE.md 与 openspec/changes/archive/
├── kuship-ui/                   # 新的控制台前端，直接拷贝rainbond-ui，调用kuship-console
└── .claude/                     # Claude Code 项目配置
    ├── commands/                # 自定义斜杠命令
    └── skills/                  # 项目级技能

```