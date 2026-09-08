# MeloNest

> Java 音乐推荐平台与社区系统。

本项目整合歌曲、专辑、歌单、MV、内容互动、投稿审核和推荐分析能力。
不包含前端页面、运行时数据、媒体文件、密钥或本机依赖目录。
这是我的毕业设计，拿去用的前提是你能看懂，此仓库没有代码注释，没有前端页面，不要看都不看拿去用，也不要舍不得token。

## 功能范围

- 音乐目录、歌单、专辑、歌手与 MV 数据服务
- 播放地址、歌词、收藏、历史、本地音乐和推荐链路
- 投稿、多文件媒体处理、审核、互动与通知
- MySQL 默认搜索与可开关的 Elasticsearch 适配
- Redis 缓存、Kafka 异步事件和 WebSocket 实时推送
- Node3 受管媒体的派生、病毒扫描、资产引用与延迟回收

## 当前技术栈

下表仅记录压缩包及其配套配置中的实际版本；未写入未来迁移版本。

| 类别 | 当前版本 | 用途 |
|---|---|---|
| Java | 8u461 | 后端与集群运行时。 |
| Spring Boot | 2.7.18 | REST API、WebSocket、缓存、验证与应用配置。 |
| Maven | 3.6.3 | 后端构建。 |
| MyBatis-Plus | 3.5.5 | ORM 与数据访问。 |
| Druid | 1.2.20 | JDBC 连接池。 |
| MySQL Server | 5.7.44 | 业务事实与事务数据。 |
| MySQL Connector/J | 5.1.49 | 后端数据库驱动。 |
| Redis | 5.0.14 | 缓存、限流、锁与短期状态。 |
| Kafka | 2.4.1 | 异步事件处理。 |
| ZooKeeper | 3.7.2 | 集群协调。 |
| Elasticsearch | 7.17.24 | 可选搜索引擎；默认仍可回退 MySQL。 |
| Hadoop | 3.3.6 | HDFS/YARN。 |
| Hive | 3.1.2 | 离线表与查询。 |
| Spark | 2.4.8（Scala 2.11.12） | 推荐训练与离线分析。 |
| HBase | 2.5.12 | 仅保留 node1 单机配置，不承载业务数据。 |
| Nginx | 1.28.0 | 前端入口、反向代理与媒体路由。 |
| FFmpeg / FFprobe | 6.1.1 | 三节点统一用于音频解码、视频探测、缩略图与转码。 |
| ClamAV 客户端 | `fi.solita.clamav:clamav-client` 1.0.1 | 通过 clamd INSTREAM 协议扫描上传文件。 |
| ClamAV 服务 | clamd，默认端口 3310 | 守护进程版本不在源码压缩包中固定；部署时需用 `clamd --version` 记录实际版本。 |
| Python | 3.7.16 | node1/node2/node3 的 PySpark 与音频分析运行时。 |
| Node.js | 18+ | Vite 5 前端构建与本地回环媒体代理。 |
| Vue | 3.4.x | 前端状态与交互层。 |
| Vite | 5.x | 前端开发与构建。 |
| TypeScript | 5.3.x | 前端类型检查。 |

媒体工具由 `FFMPEG_PATH`、`FFPROBE_PATH` 指向 `/usr/local/soft/ffmpeg-6.1.1/bin`，不依赖系统 `PATH`。病毒扫描默认启用、不可用时失败关闭；扫描服务地址、超时、隔离目录和大小限制均由部署环境变量覆盖，不能将真实值提交到仓库。

## 项目结构

```text
.
├── hao-ran-music-backend/     # Spring Boot 后端源码
├── hao-ran-music-frontend/    # Vue / TypeScript 逻辑源码（不含页面）
├── haoran-local-proxy/        # 仅监听本机的 Node.js 媒体代理
├── docs/                      # 架构、部署、数据与验证说明
├── ee/                        # 脱敏集群配置样例
├── scripts/                   # 构建、训练、运维与校验脚本
└── sql/                       # 数据库结构与资源登记脚本
```

## 使用边界

- 不提交数据库行数据、Node3 媒体文件、密钥、令牌、账号信息、日志、构建产物或 `node_modules`。
- 公开目录数据须来自具备使用权的数据源。Node3 已保存媒体在新库目录数据准备完毕后，使用 `sql/import-node3-media-assets.sql` 按新库 ID 登记。
- Elasticsearch 是可选搜索适配，MySQL 保持业务事实来源与回退路径；HBase 不在当前业务数据路径中。
- 投稿、播放、审核与支付属于跨服务链路；修改时应验证权限、重放、并发、缓存、消息、文件回收与故障恢复。

## 文档入口

- [项目文档索引](docs/public-project-guide.md#文档索引)
- [软件与配置](docs/software-and-configuration.md)
- [集群部署与运维](docs/public-project-guide.md#集群部署与运维)
- [测试与验证](docs/public-project-guide.md#测试与验证)
- [Node3 资源登记](sql/README.md)

## 发布说明

发布到远端仓库时，请将本文件命名为 `README.md`，并仅放入指定源码压缩包解压后的内容。对外发布前应单独复核第三方许可证、字体/媒体/数据集的再分发权利，以及所有配置文件中是否遗留地址、账号或密钥。
