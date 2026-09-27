# DPO-X5 · 打印 schema 主路径

前置：Claude L2 [#663](https://github.com/EricBeckham/NexusOne/pull/663) 已 squash 合入；V707 为该卡占用。本卡不新增迁移、不改 service、不接升级步骤备选。

夹具：[dpo-print-schema.v1.json](fixtures/dpo-print-schema.v1.json)。`PrintMappingSchemaContractTest` 对照 V707 锁定两张表、列类型、产品定义 FK、正修订约束、JSON array、复合历史主键与按保存时间索引；同时确认 Flyway 默认 outOfOrder=false 且配置没有开启。

- `market.print_mapping`：产品定义唯一当前映射，revision > 0；无 override 时 revision 0 是应用默认，不入库。
- `market.print_mapping_revision`：按产品+revision 唯一，字段快照与 saved_at/saved_by；DDL 本身不保证 append-only 权限或 revision 原子更新，这些归 L3 业务/事务验证。
- `mappings` 是字段→槽位 JSON，不是真实申请值；纸张/方向/槽位由应用合同校验。夹具只含合成映射、无 secret。

验证命令：Maven `-pl nexus-p4-market -am -Dtest=PrintMappingSchemaContractTest,XbWaveSchemaContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。源 schema 契约≠空库 Flyway 已升级≠打印重启持久化已验收；本卡不作这些宣称。
