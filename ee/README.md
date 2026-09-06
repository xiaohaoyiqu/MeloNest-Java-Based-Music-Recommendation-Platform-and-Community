# 集群配置目录

- `current/`：从三节点读取并脱敏的当前有效配置，用于核对、迁移和技术升级准备。
- `deploy/`：仅在运维时临时创建，用于放置已审核、已补齐秘密的待分发配置；该目录不进入交付包。

同步命令：

```powershell
.\scripts\sync-sanitized-cluster-config.ps1
```

同步不会把远端原始配置写入磁盘。`current` 中仍有 `${REDIS_PASSWORD}`、`${REDACTED_SECRET}` 等占位符，不能直接用于覆盖运行环境。

