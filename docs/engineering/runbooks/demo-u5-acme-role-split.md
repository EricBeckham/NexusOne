# DEMO-U5 · demo_acme 租户管理与一般用户的权限分异

在 `demo_acme`（`01900000-0000-7000-8000-000000000101`）上：

- 林晓（person `…0203`）挂 `tenant_admin`。报表查询走 `ledger/read`，导出走 `ledger.export.csv` / `submit`，管理走 `tenant.admin` / `manage`。这三项对该角色为允许。
- 周然（person `…0204`）只挂 `tenant_member`。导出和管理是 `no_grant`。不把 `tenant_admin` 授给周然。

安装时机与 DEMO-U2 / DEMO-U3 相同的一次性窗口：`NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true` 且 `NEXUS_P1_DEMO_CONTEXT_ENABLED` 保持 false。本地演示开关打开时也会安装，并且仍然拒绝非本机库。两个开关都关闭时不写角色。重复安装不追加第二条授权。

本卡不改平台职责目录，也不打开演示上下文。演示机只部署已合入 `main` 的构建。
