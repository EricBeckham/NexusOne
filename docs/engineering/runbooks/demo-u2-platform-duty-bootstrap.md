# DEMO-U2 · 平台职责一次性启动任务

平台租户的 `perm_role` 为空时，平台页和租户列表会得到 `no_grant`。AUTH6 只读列表还要求展开后的职责恰好是 `platform_system_admin`，并已有 `tenant:list`。本卡用已有的 `PlatformDutyService.install()` / `PlatformDutyCatalog` 写入这三份职责，并把平台运营 person `01900000-0000-7000-8000-000000000222` 挂到 `platform_system_admin`。

不打开 `NEXUS_P1_DEMO_CONTEXT_ENABLED`。不授予租户侧物理导出（`ledger.export.csv`）；导出归租户管理，本卡安装后该资源仍是 `no_grant`。

## 部署后如何启用（一次性启动任务，fail-closed）

默认关闭。关闭时启动任务什么都不写，空的 `perm_role` 保持 `no_grant`。

打开窗口之前**仍须先清孤儿**：核对三个 U3 邮箱（`platform.admin@` / `lin.xiao@` / `zhou.ran@demo.invalid`）都没有被登录对账写成固定 id 之外的账号。邮箱在 `iam_global_account` 上唯一，U3 固定账号是 `…0221` / `…0201` / `…0202`。含 ID1 的构建遇到被其他账号占用的邮箱（含大小写变体、软删除行）会抛 `ID1 identity conflict` 拒启，不抢占、不合并；孤儿须先由另行授权的运维流程处理，再打开本窗口。详见 [TC4](tc4-demo-u2-after-keycloak-checklist.md) 与 [ID1 / ID3](id1-u2-identity-bootstrap.md)。

1. 确认 `NEXUS_P1_DEMO_CONTEXT_ENABLED` 与 `NEXUS_DEMO_ENABLED` 未设置或为 `false`。不要打开它们（演示机禁止开 `NEXUS_DEMO_ENABLED`；含 ID1 的构建插入身份也不需要它或回环数据库），也不要靠演示身份头代替这次安装。
2. 将 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设为 `true`。
3. 用包含 ID1、已合入 `main` 的构建重启一次。启动任务先事务性插入或对齐 U3 三套固定身份，确保 person `…0222` 存在，随后才安装职责并赋为 `platform_system_admin`。不依赖 `NEXUS_DEMO_ENABLED`，无需回环数据库；保持该开关关闭。重复启动不重复插入身份或授权。邮箱冲突、固定行归属不符会诚实拒启，详见 [ID1 / ID3](id1-u2-identity-bootstrap.md)。
   顺序以 ID1 为准：先在身份事务里补齐 / 对齐账号 `…0221 / …0201 / …0202`、person `…0222 / …0203 / …0204`、成员资格、部门 / 岗位与主职并提交；再安装三份平台职责；最后把已存在的 `…0222` 挂到 `platform_system_admin`。身份事务失败时不安装任何新职责或授权；职责安装失败时身份已提交，可按同一窗口重试，失败的启动不算通过。
4. 验收平台只读列表的前提已满足后，把 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 设回 `false` 再重启。已写入的角色和授权保留。AUTH6 自己的 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 仍按该卡单独开关，本任务不代开。

若两个开关同时为 `true`，进程拒绝启动，不安装职责，也不回退到演示上下文。错误只报变量名。

演示机只部署和测试这次合入的构建。不要在演示机上热修 jar、class 或 env 来改产品语义。
