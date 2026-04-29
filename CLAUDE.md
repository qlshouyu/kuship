# kuship项目
kuship是一个基于rainbond的kubernetes的云原生服务托管管理项目，主要是rainbond-console使用java springboot技术栈进行实现

# 目录结构
```
kuship/
├── CLAUDE.md                    # 项目说明（供 Claude Code 使用）
├── README.md                    # 项目自述文件
├── standalone_build.sh          # standalone 镜像构建入口脚本
├── k3s-images-arm64.tar.zst     # k3s 离线镜像包（arm64）
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
└── .claude/                     # Claude Code 项目配置
    ├── commands/                # 自定义斜杠命令
    └── skills/                  # 项目级技能
```