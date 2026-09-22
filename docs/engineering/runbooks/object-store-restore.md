# 对象存储恢复（本地 MinIO）

**状态：G6 S1 环境级对象恢复演练（本地 MinIO）。不是 AIStor 企业验收，不是目标 Profile，不是 G17。**

对象字节由 File Service / S3 API 拥有。本文件固定环境级恢复演练：源 bucket 整桶备份后恢复到**隔离** bucket + 前缀，并对账 SHA-256。

可复现脚本：[`scripts/g6-recovery/run-drill.sh`](../../../scripts/g6-recovery/run-drill.sh)。实测记录：[`g6-recovery-drill-EVIDENCE.md`](../g6-recovery-drill-EVIDENCE.md)。

## 原则

- 恢复到隔离 bucket / 前缀，不要覆盖源 bucket。
- 使用 P0 锁定的社区 MinIO：`RELEASE.2025-09-07T16-13-09Z`，本机 API `127.0.0.1:9100`。
- 对象键保持租户前缀 `tenants/{tenantId}/…`；演练同时放入两个租户对象 + `platform/` 标记，证明这是环境级整桶恢复，不是单租户导出。
- 凭证走 SecretPort（S0：进程内 overlay 或环境变量 `S3_ACCESS_KEY` / `S3_SECRET_KEY`），或本机 workdir。不得写入 Git。不要求 OpenBao / KMS。
- 不发明第二套存储产品。AIStor 企业镜像/授权仍由采购/交付提供，本演练不替代。

## 步骤

1. 确认本机 MinIO 版本与 endpoint（无 Docker）。`curl -fsS http://127.0.0.1:9100/minio/health/live`
2. 列出源 bucket 对象，计算每个对象的 SHA-256。
3. `mc mirror` 源 bucket 到本地备份目录。
4. `mc mirror` 备份目录到 `nexusone-g6-dst/restore-$DATE/`。
5. 比较对象键集合与 SHA-256。有差异即失败。

```bash
mc alias set g6local http://127.0.0.1:9100 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mirror --overwrite g6local/nexusone-g6-src "$WORK/object-backup/nexusone-g6-src"
mc mirror --overwrite "$WORK/object-backup/nexusone-g6-src" \
  g6local/nexusone-g6-dst/restore-20260922
mc cat g6local/nexusone-g6-dst/restore-20260922/tenants/.../file-a.bin | sha256sum
```

## 本仓库接口

`RecoveryPort.dryRunObjectStore()` 不调用任何 S3 客户端，返回 `DRY_RUN` 且 `executed=false`、`profileAccepted=false`。G6 对象恢复走本 runbook / `scripts/g6-recovery/`，不改产品代码。
