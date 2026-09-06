# 数据库结构说明

`current-schema.sql` 以 2026-09-06 从 node1 的 `haoranmusic_bus` 重新导出的结构为基础，并纳入同日已部署及后检通过的收藏分组迁移；目标版本为 MySQL 5.7.44。

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

MySQL 对存储对象导出的说明：<https://dev.mysql.com/doc/refman/5.7/en/mysqldump-stored-programs.html>。
