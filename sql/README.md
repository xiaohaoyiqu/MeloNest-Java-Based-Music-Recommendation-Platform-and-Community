# 数据库结构说明

`current-schema.sql` 以 2026-09-06 从 node1 的 `haoranmusic_bus` 重新导出的 MySQL 5.7.44 结构为兼容性基线，并纳入同日已部署及后检通过的收藏分组迁移。新环境的版本选择与验证要求见 [技术演进与可观测性](../docs/10-technology-evolution-and-observability.md)；数据库结构已就绪时，不需要再次执行本文件。

## 内容

- 190 张基础表
- 9 个视图
- 4 个触发器
- 5 个函数
- 5 个存储过程
- 4 个事件
- SHA-256：`8dc52f8f7d835e72b27e8d3e7a789cfd1cdec994809961fafb291d04c648d67f`

导出使用 `--no-data --routines --triggers --events --set-gtid-purged=OFF`，不包含业务行数据；`DEFINER` 已替换为 `CURRENT_USER`，表级自增计数已移除。例程定义中的数据操作语句属于存储程序代码，不是业务数据。

高级对象不是示例占位：视图提供歌曲、歌单和用户收听统计读模型；函数与存储过程覆盖偏好、热度、信用及推荐查询；触发器维护局部统计一致性；事件承担过期清理和周期衰减。恢复账户需要相应对象的创建权限，定时事件还需在目标环境明确核对 `event_scheduler` 策略。

## 恢复

只在空库或专门的恢复演练库执行：

```bash
mysql --default-character-set=utf8mb4 < current-schema.sql
```

执行账户需要创建数据库、表、视图、触发器、例程和事件的权限。导入后核对对象数量、字符集、索引、外键、视图和存储对象。不要把该脚本直接覆盖到正在运行且已有数据的数据库。

升级现有环境应先取得结构与数据备份，再执行差异预检、版本化迁移、后检和回滚演练。后端资源目录中的日期型 SQL 是演进历史和相应测试夹具，不等同于新环境初始化脚本。

当前 `external_content` 使用 `(content_type, external_id)` 唯一来源索引；同一上游条目不能因页面重复点击、请求重放或并发提交生成多条记录，`external_id` 为空的手工内容不受该唯一键合并。

## node3 已保存媒体登记

新系统已具备数据库结构且公开目录已导入后，如需登记 node3 上保留的音频或 MV 文件，使用 `import-node3-media-assets.sql`。脚本使用连接级临时清单，默认只预检；清单中的 `target_id` 必须是新库已经存在的 `song.id` 或 `mv.id`，不能复用旧库编号。预检通过后才将 `@execute_import` 改为 `1` 重跑。

脚本只登记 `media_asset`、`media_asset_reference` 以及被清单明确授权更新的目录资源 URL/大小字段；不会创建或覆盖数据库结构，不导入用户、订单、审核、行为或其他业务数据。每个文件必须在 node3 受管根目录内、大小和哈希已核对、扫描状态为 `CLEAN`。同一存储路径或 URL 已被不同业务目标使用时会阻止导入。清单 URL 必须匹配新环境的受控媒体路由；不要把 Nginx 静态目录暴露当成播放授权方案。

资源角色：歌曲可填 `original`、`standard`、`high`、`lossless`、`hires`、`master`、`instrumental`；MV 可填 `original`、`360p`、`720p`、`1080p`、`2160p`。`original` 仅登记原始资源及其引用，不会自动改写目录的衍生资源 URL。

MySQL 对存储对象导出的说明：<https://dev.mysql.com/doc/refman/5.7/en/mysqldump-stored-programs.html>。
