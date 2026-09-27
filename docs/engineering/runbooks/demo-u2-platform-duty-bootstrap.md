# DEMO-U2 · 平台职责一次性启动任务

平台租户的 `perm_role` 为空时，平台页和租户列表会得到 `no_grant`。AUTH6 只读列表还要求展开后的职责恰好是 `platform_system_admin`，并已有 `tenant:list`。本卡用已有的 `PlatformDutyService.install()` / `PlatformDutyCatalog` 写入这三份职责，并把平台运营 person `01900000-0000-7000-8000-000000000222` 挂到 `platform_system_admin`。

不打开 `NEXUS_P1_DEMO_CONTEXT_ENABLED`。不授予租户侧物理导出（`ledger.export.csv`）；导出归租户管理，本卡安装后该资源仍是 `no_grant`。

## 部署后如何启用（一次性启动任务，fail-closed）

默认关闭。关闭时启动任务什么都不写，空的 `perm_role` 保持 `no_grant`。

打开窗口之前先核对 `platform.admin@demo.invalid` 没有被登录对账写成孤儿账号。该邮箱在 `iam_global_account` 上唯一；U3 固定账号是 `…0221`。若邮箱已挂在别的 id 上，按 id 的 `ON CONFLICT DO NOTHING` 不会改写那一行，随后插入 `…0221` 会因邮箱冲突失败。先处理孤儿，再打开本窗口。详见 [TC4](tc4-demo-u2-after-keycloak-checklist.md)。

1. 确认 `NEXUS_P1_DEMO_CONTEXT_ENABLED` 未设置或为 `false`。不要打开它，也不要靠演示身份头代替这次安装。
2. 将 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设为 `true`。
3. 用包含 ID1、已合入 `main` 的构建重启一次。启动任务先事务性插入或对齐 U3 三套固定身份，确保 person `…0222` 存在，随后才安装职责并赋为 `platform_system_admin`。不依赖 `NEXUS_DEMO_ENABLED`，无需回环数据库；保持该开关关闭。重复启动不重复插入身份或授权。邮箱冲突、固定行归属不符会诚实拒启，详见 [ID1 / ID3](id1-u2-identity-bootstrap.md)。
4. 验收平台只读列表的前提已满足后，把 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设回 `false` 再重启。已写入的角色和授权保留。AUTH6 自己的 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 仍按该卡单独开关，本任务不代开。

若两个开关同时为 `true`，进程拒绝启动，不安装职责，也不回退到演示上下文。错误只报变量名。

演示机只部署和测试这次合入的构建。不要在演示机上热修 jar、class 或 env 来改产品语义。
