# P0-10：PostgreSQL 部署 Profile、CryptoPort 与供应链验收

对应任务：[GitHub Issue #10](https://github.com/EricBeckham/NexusOne/issues/10)。用户指定所有任务仅在 PostgreSQL 上执行。`deployment-matrix.csv` 是**具体组合**的验收表，每行填写同一次安装、升级、回滚、离线演练的 OS/CPU/JDK/PostgreSQL 版本和证据；不要将 x86_64 的结果外推给 ARM64，也不要将本机 PoC 计为目标 Linux Profile。目标 Profile 的具体发行版、CPU 型号、JDK 21 发行版和 PostgreSQL 版本仍需交付方填写。

## 最小 CryptoPort 契约

`CryptoContract` 使用随机生成的临时密钥，按同一 `CryptoPort` 接口执行摘要、MAC、AEAD 加解密与数字签名。国际套件为 SHA-256、HmacSHA256、AES-256-GCM、SHA256withECDSA；国密套件为 SM3、HmacSM3、SM4-GCM、SM3withSM2。每套检查正向往返及修改数据后的拒绝/差异。

```powershell
mvn -q compile exec:java
```

本机实际执行结果：Maven 3.9.15 / Java 17.0.12 下，国际与国密两套均打印 `PASS`，退出码 0。这里验证的是 Bouncy Castle `bcprov-jdk18on:1.85.2` 提供的基本 JCA 算法路径，**尚不等于**商用密码合规、正式 GM/T Provider 资质、KMS/密码机、国密 TLS、密钥生命周期、固定测试向量、跨 Provider 互操作或 Java 21/目标 CPU 测试。生产密钥不得由该 PoC 的内存随机密钥模式代替。

## PostgreSQL 只读探针

```powershell
$env:P0_10_DB_URL = 'jdbc:postgresql://127.0.0.1:55438/postgres'
$env:P0_10_DB_USER = 'nexus_poc'
# 需要认证的目标环境再设置 P0_10_DB_PASSWORD，切勿将密码写入仓库。
mvn -q compile exec:java '-Dexec.mainClass=poc.DbProbe'
```

本机临时 PoC 环境于 `127.0.0.1:55438` 使用 host trust、仅绑定回环地址，`DbProbe` 只读查询返回 PostgreSQL `15.18`、数据库 `postgres`、用户 `nexus_poc`，退出码 0。没有在这个实例做安装、升级、回滚、备份恢复或目标部署测试。脚本结束后清理本进程的 `P0_10_DB_*` 环境变量。

## 目标 Profile 执行记录

1. 先冻结一行 Profile：OS 版本、CPU 型号/架构、JDK 发行版与补丁、PostgreSQL 版本、部署方式、镜像/包 digest。每个组合单独执行，保存 `collect-profile.sh` 的机器与包版本输出及 `sha256sum` 结果。脚本只采集机器/工具版本；不要把环境变量、凭据或完整配置作为证据上传。
2. 安装：按离线包清单全新安装 PostgreSQL、应用及 P0 外部组件；记录服务状态、数据库连接、组件契约测试和耗时。目标环境的最低权限账号、备份目录和访问控制需确认。
3. 升级：从确定的旧版及测试数据开始，记录迁移脚本、数据库基线、维护窗口、升级后版本/数据校验和全量契约测试。PostgreSQL 大版本升级可选 `pg_upgrade` 或逻辑导出/导入，具体路线须在该 Profile 实测。
4. 回滚：升级**前**先做并验证备份，升级失败时按书面步骤恢复原版本程序与原版本数据库快照/逻辑备份；不得假定旧程序可读取升级后的 Schema。记录 RPO、RTO、校验行数与哈希、失败日志。
5. 离线：从隔离网络独立安装镜像/包；检查每个组件的 SHA-256/digest、SBOM、许可证文本及 CVE 处置记录，并重复冒烟与恢复检查。

`deployment-matrix.csv` 的 `not-tested` 状态不得提前改为 `pass`；证据目录应指向不可变构建产物或受控归档。PostgreSQL 的 `pg_dump`/`pg_restore` 可用于逻辑备份恢复；大版本升级不能简单复用旧版物理数据目录，参见官方文档。测试恢复必须落到独立实例并验证，只有备份命令成功不算恢复通过。

## 供应链门禁

在 `supply-chain-evidence.csv` 为每个交付组件逐行记录确切版本、官方下载/仓库地址、包 SHA-256 或镜像 digest、许可证 SPDX ID 和许可证文件、SBOM、CVE 扫描及处置、离线包、适用架构、验收人。尤其要确认 Keycloak、Flowable、Quartz、Flyway、Redis 与对象存储各自的版本和授权；不能把开发机的 Maven 缓存当成来源追溯证据。镜像应按 digest 复拉并在目标架构验证。供应链表目前全部 `not-collected`。

## 仍未验证

- 所有目标 Linux/CPU/JDK 21/PostgreSQL 的安装、升级、回滚及离线部署。
- CryptoPort 互操作、SM2/SM3/SM4 固定向量、国密 TLS 与合规资质、密钥管理和密码机集成。
- PostgreSQL 备份恢复演练、RPO/RTO、跨版本兼容。
- SBOM、许可证选择、CVE、镜像 digest 和离线包验收。

## 文档来源

- [Bouncy Castle Java Provider 与依赖](https://github.com/bcgit/bc-java)
- [PostgreSQL 17 备份与恢复](https://www.postgresql.org/docs/17/app-pgdump.html)
- [PostgreSQL 17 升级](https://www.postgresql.org/docs/17/upgrading.html)
- [PostgreSQL 17 pg_upgrade](https://www.postgresql.org/docs/17/pgupgrade.html)
