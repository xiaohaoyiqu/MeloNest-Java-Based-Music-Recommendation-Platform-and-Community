# 项目文件说明

本文是交付包的独立文件清单。它说明每个顶层目录的用途、是否可直接运行以及配置边界。

## 顶层目录

| 路径 | 内容 | 使用说明 |
|---|---|---|
| `hao-ran-music-backend/` | Spring Boot 源码、资源、测试和 Maven 配置 | 完整版可测试与打包；无注释版不含测试 |
| `hao-ran-music-frontend/` | Vue 3 前端源码与构建配置 | 完整版含页面；无注释版只保留非页面合约源码 |
| `haoran-local-proxy/` | 回环本地音乐服务 | Node.js 18+，只监听本机 |
| `scripts/` | 集群、组件、日志、配置、训练和打包脚本 | 先阅读脚本分组，不直接执行破坏性命令 |
| `sql/` | 现行数据库结构与说明 | 只含结构，不含业务数据或凭据 |
| `ee/current/` | 三节点当前配置的脱敏副本与清单 | 用于核对、迁移和升级准备，不可直接覆盖生产 |
| `docs/` | 当前项目、架构、运行、安全和验证文档 | 完整版收录全部稳定文档 |

## 关键入口

- 后端入口：`com.haoran.music.HaoRanMusicApplication`。
- 后端配置：`src/main/resources/application.yml` 与环境覆盖文件。
- 前端入口：`src/main.ts`；路由：`src/router/index.ts`；API：`src/api`。
- 本地音乐入口：`haoran-local-proxy/server.js`。
- 集群入口：`scripts/cluster.sh`；快速启动：`scripts/quick-start.sh`。
- 推荐训练入口：`scripts/run-recommendation-training.sh`。
- 数据库入口：`sql/current-schema.sql`。
- 配置快照清单：`ee/current/manifest.json`。

## 两种交付包

完整注释版保留后端、完整前端页面、本地音乐服务、测试、稳定文档、现行 SQL、配置和运维/训练脚本，适合继续开发和维护。

无注释源码版移除源码注释、测试和前端页面/组件/布局/静态页面资源，保留后端业务源码、前端 API/状态/类型等合约源码、本地音乐服务、规定脚本、现行 SQL、安全配置以及本文件和《项目说明书》。该版本不能独立构建完整网页。

## 不包含内容

依赖目录、构建产物、日志、缓存、测试结果、上传文件、模型产物、数据库数据、备份、私钥、主机指纹文件、真实秘密文件、编辑器工程文件和版本托管元数据均不进入交付包。

