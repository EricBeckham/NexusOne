# 对象存储恢复

**状态：runbook / 接口占位。不是 G6，不是 MinIO/AIStor 产品验收。**

对象存储由 M0-10 File Service 拥有。本文件只固定恢复演练口径，避免审计包把未执行的 restore 写成通过。

## 原则

- 恢复到隔离 bucket / 前缀，不要覆盖生产。
- 元数据 `file_id` 与对象 key 对账；跨租户前缀在访问对象前拒绝（隔离内核 / File Guard）。
- 凭证走 SecretPort，不得写入 Git。
- 版本与授权来源由 P0-06 / M0-10 锁定；此处不发明第二套存储产品。

## 干跑步骤（人工）

1. 确认本机原生 MinIO 或 AIStor 版本与 endpoint（无 Docker）。
2. 列出源 bucket 对象计数与抽样 ETag。
3. 复制到 `restore-$DATE` 前缀。
4. 用 File Service 二次授权校验签名 URL；跨租户必须失败。
5. 记录差异。未做对账不得声称 G6。

## 本仓库接口

`RecoveryPort.dryRunObjectStore()` 不调用任何 S3 客户端，返回 `DRY_RUN` 且 `executed=false`、`profileAccepted=false`。
