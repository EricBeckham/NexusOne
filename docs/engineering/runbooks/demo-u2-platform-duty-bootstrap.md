# DEMO-U2 · 平台职责一次性启动任务

平台租户的 `perm_role` 为空时，平台页和租户列表会得到 `no_grant`。AUTH6 只读列表还要求展开后的职责恰好是 `platform_system_admin`，并已有 `tenant:list`。本卡用已有的 `PlatformDutyService.install()` / `PlatformDutyCatalog` 写入这三份职责，并把平台运营 person `01900000-0000-7000-8000-000000000222` 挂到 `platform_system_admin`。

不打开 `NEXUS_P1_DEMO_CONTEXT_ENABLED`。不授予租户侧物理导出（`ledger.export.csv`）；导出归租户管理，本卡安装后该资源仍是 `no_grant`。

## 部署后如何启用（一次性启动任务，fail-closed）

默认关闭。关闭时启动任务什么都不写，空的 `perm_role` 保持 `no_grant`。

1. 确认 `NEXUS_P1_DEMO_CONTEXT_ENABLED` 未设置或为 `false`。不要打开它，也不要靠演示身份头代替这次安装。
2. 将 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设为 `true`。
3. 用已合入 `main` 的构建重启一次。启动任务在配置的平台租户上安装职责目录，并在尚未挂接时把上述 person 赋为 `platform_system_admin`。重复启动不会再插一条授权。
4. 验收平台只读列表的前提已满足后，把 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设回 `false` 再重启。已写入的角色和授权保留。AUTH6 自己的 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 仍按该卡单独开关，本任务不代开。

若两个开关同时为 `true`，进程拒绝启动，不安装职责，也不回退到演示上下文。错误只报变量名。

演示机只部署和测试这次合入的构建。不要在演示机上热修 jar、class 或 env 来改产品语义。
