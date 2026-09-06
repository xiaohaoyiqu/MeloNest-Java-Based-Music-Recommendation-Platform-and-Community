# 软件与配置要求

## 本地开发

- Windows 10/11 或可运行相同工具链的 Linux/macOS。
- JDK 8 与 Maven 3.6+，用于 Spring Boot 2.7.18 后端。
- Node.js 18+ 与 npm，用于 Vite 5 前端和本地音乐服务。
- MySQL 5.7、Redis 5；Kafka、Elasticsearch、Hadoop、Hive、Spark 按功能需要启用。搜索默认使用 MySQL，只有显式选择 Elasticsearch 后才切换。
- FFmpeg/ffprobe 6.1.1 用于音频解码、视频探测、缩略图和转码；后端通过绝对路径调用，不使用系统旧版本。
- OpenSSH 客户端用于三节点管理，主机指纹必须固定。

Spring Boot 2.7 文档说明 Java 8 为最低版本并支持 Maven 3.5+：<https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements>。Vite 5 的项目与构建说明见 <https://v5.vite.dev/guide/>。Maven 安装说明见 <https://maven.apache.org/install.html>。

## 集群软件

| 软件 | 当前版本 | 主要用途 |
|---|---:|---|
| Java | 8u461（应用目录） | 后端与大数据组件 |
| MySQL | 5.7.44 | 业务事实与事务 |
| Redis | 5.0.14 | 缓存、限流、锁与短期状态 |
| Nginx | 1.28.0 | 前端入口、代理和媒体路由 |
| Hadoop | 3.3.6 | HDFS/YARN |
| Hive | 3.1.2 | 离线表与查询 |
| Spark | 2.4.8 | 推荐训练与分析 |
| Kafka | 2.4.1 | 事件与异步处理 |
| ZooKeeper | 3.7.2 | 集群协调 |
| HBase | 2.5.12（node1） | 扩展数据能力 |
| Elasticsearch | 7.17.24（node1） | 可选搜索引擎 |
| FFmpeg | 6.1.1（三节点） | 媒体解码、探测与转码 |
| Python | 3.7.16（node3 分析运行时） | 音频特征提取 |
| Essentia | 2.1 beta6 dev1034（node3 隔离目录） | BPM 与调式分析 |

Kafka 2.4 的 broker 配置以 `broker.id`、`log.dirs`、`zookeeper.connect` 等为核心，静态只读项变更需要重启：<https://kafka.apache.org/24/configuration/broker-configs/>。Redis 配置文件与运行时配置方式见 <https://redis.io/docs/latest/operate/oss_and_stack/management/config/>。Nginx 配置与重载方式见 <https://nginx.org/en/docs/beginners_guide.html>。

## 环境变量

至少配置数据库地址、用户和口令，Redis 口令，JWT 密钥，允许来源以及各媒体根目录。`FFMPEG_PATH`、`FFPROBE_PATH` 默认指向 `/usr/local/soft/ffmpeg-6.1.1/bin`；Windows 本地开发需显式指向本机安装。`NODE3_LYRIC_URL` 指向 node3 只读歌词目录的内部 HTTP 基址，默认值为 `http://192.168.153.133:8081/lyrics/`；生产环境应限制为集群内访问，留空才启用维护用 SFTP 回退。支付、短信、邮件、外部 AI、Elasticsearch 认证和对象存储仅在启用对应能力时配置。真实值放入权限受控的环境或秘密文件，不写入 `application*.yml`、脚本、文档或压缩包。

## Elasticsearch 搜索适配

Elasticsearch 7.17.24 的脱敏节点配置位于 `ee/current/node1/elasticsearch/`；后端包含搜索路由、索引服务、事务 outbox 和索引重建任务。MySQL 始终保存业务事实并负责结果组装，Elasticsearch 请求不可用、索引不存在或结果无法组装时自动回退到 MySQL。

部署 Elasticsearch 后，先确认服务可访问和索引存储目录可写，再通过受控环境设置下列变量；未设置时仍使用 MySQL。

```bash
export SEARCH_ENGINE=elasticsearch
export SEARCH_ES_ENABLED=true
export SEARCH_ES_URIS=http://127.0.0.1:9200
export SEARCH_ES_INDEX_PREFIX=haoran_music
```

首次导入或需要全量校准时，才额外设置 `SEARCH_ES_BOOTSTRAP_REBUILD_ENABLED=true` 或在授权的管理端点触发重建。定时重建由 `SEARCH_ES_REBUILD_ENABLED=true` 单独开启。用户名、密码和地址由受控环境注入，不写入交付包；切回 MySQL 只需将 `SEARCH_ENGINE=mysql` 或 `SEARCH_ES_ENABLED=false` 后重启应用。

## Windows DeepSeek 服务

当前 DeepSeek 由 Windows 上的 Ollama 提供，默认监听 `11434`；node1 后端通过 `DEEPSEEK_BASE_URL` 指向 Windows 宿主机的受控内网地址，不在代码和文档中固化实际地址。启动前先确认端口和进程，避免重复拉起服务：

```powershell
Get-Process ollama,llama-server -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 11434 -State Listen
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

从 node1 还应使用配置后的基址读取 `/api/tags`，确认虚拟机到宿主机链路可达；防火墙只放行所需私有网段，不向公网暴露模型端口。默认单次生成超时为 600 秒，线程内传输重试为 0；失败由数据库持久任务按退避时间重新投递，避免一次任务在工作线程内连续占用多个超时周期。2026-09-05 运行核对时，Windows 的 `ollama.exe` 正在监听，`deepseek-r1:8b` 和 `deepseek-r1:latest` 已安装；实际最小生成请求会按需拉起 `llama-server.exe` 并在约 31.8 秒后返回，空闲后工作进程和显存模型自动卸载属于正常行为。端口在线只代表通道可用，不代表长歌词能在请求超时内完成，因此业务端仍以持久任务、退避重试和最终状态为准。

Essentia 作为 node3 的独立运行依赖，按 AGPL-3.0 许可管理，不打入后端 JAR；固定安装脚本同时校验 NumPy、PyYAML、six 与 Essentia wheel 的 SHA-256。项目使用 FFmpeg 6.1.1 先解码受控片段，再由 Essentia 的 `RhythmExtractor2013` 和 `KeyExtractor` 提取核心 DJ 特征。算法说明可参考 <https://essentia.upf.edu/reference/std_RhythmExtractor2013.html>。

生产环境应启用 HTTPS 与安全 Cookie，限制 CORS 来源，关闭无必要管理端口，设置数据库和中间件最小权限，并按组件分别规划数据、日志、PID、临时文件和备份目录。

## 构建与启动

```powershell
cd hao-ran-music-backend
mvn clean test
mvn -DskipTests package

cd ..\hao-ran-music-frontend
npm ci
npm run build

cd ..\haoran-local-proxy
npm ci
npm start
```

MySQL 结构导入到空库前先校验版本和字符集：

```bash
mysql --default-character-set=utf8mb4 < sql/current-schema.sql
```

MySQL 官方文档说明 `--no-data` 只输出表结构，而存储过程、函数和事件需显式使用 `--routines`、`--events`，触发器默认包含：<https://dev.mysql.com/doc/refman/5.7/en/mysqldump-stored-programs.html>。
