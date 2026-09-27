# TC4：U4 建用户之后必须做 U2 一次性

> ID1 / ID3 已合入 `main`（#720，`07198104`）：包含 ID1 的构建改为 U2 窗口先插入/对齐三套身份再挂职责，不依赖本机 demo 开关；见下文「一次性能写什么」与 [ID1 / ID3](id1-u2-identity-bootstrap.md)。「ID1 之前的行为」一节只作历史记录。只有按 TC5 复验通过后才能关闭旧断点，本文不宣称演示机已修复。

只改文档。不改演示机，不删库，不改 `NEXUS_*`。

## 预期卡点

DEMO-U4 只在 Keycloak 建三人。建完就登录、而 IAM 还没有对应 person 和恰好一条 ACTIVE membership 时，账号对账会按邮箱新建 global account。该账号 0 个 person、0 条 membership。`loginWithAccessToken` 只在恰好 1 条 ACTIVE membership 时签发 `context_id`，页面停在「缺少 TenantContext」。这是预期卡点，不是 LOGIN6 回调故障。

所以：**U4 建用户之后，必须按 [DEMO-U2](demo-u2-platform-duty-bootstrap.md) 做一次一次性启动任务**（`NEXUS_P1_DEMO_CONTEXT_ENABLED` 保持 false，临时打开 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED`，用包含 ID1 的已合 `main` 构建重启，验收后再设回 false 再重启）。不要改 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 来绕过这个卡点。不要打开 `NEXUS_DEMO_ENABLED`：演示机禁止开它（数据库不是本机回环，该开关会拒绝启动），含 ID1 的 U2 窗口插入身份也不需要它。

## 孤儿邮箱

`iam_global_account.email` 唯一。`platform.admin@demo.invalid` 若已被一次登录写成孤儿（固定 id `…0221` 之外的另一行，且 0 person / 0 membership），U2 窗口里的身份对齐只 `UPDATE` 固定 id，不会把孤儿改成 `…0221`。之后再插入固定账号会和孤儿抢同一邮箱。`lin.xiao@` / `zhou.ran@demo.invalid` 同理（固定 id `…0201` / `…0202`）。

含 ID1 的构建**仍须先清孤儿**：遇到被其他账号占用的 U3 邮箱（含大小写变体或软删除行）会抛 `ID1 identity conflict` 并拒启，不抢占、不合并、不打印凭证；身份事务回滚，也不会装上新的平台职责或授权。

打开 U2 之前，三个 U3 邮箱在 `iam_global_account`（含软删除行）上各自必须是 0 行，或已经是对应固定 id。删行属于运维动作，须单独确认后再做；本文不授权删除。

## 一次性能写什么（含 ID1 的构建，PR #720 起）

`main` 自 #720（`07198104`）起，U2 窗口按 ID1 顺序执行：

1. 身份事务：固定账号 `…0221 / …0201 / …0202` 不存在则插入，存在则对齐邮箱、username、显示名、岗位与主职（保留已绑定的 `keycloak_user_id`）；person `…0222 / …0203 / …0204`、目标租户成员资格、部门 / 岗位及 ACTIVE PRIMARY 主职一并补齐。每个固定账号恰好一条 ACTIVE membership，其余置 SUSPENDED。提交后才进入下一步。
2. 安装三份平台职责。
3. 把已存在的 person `…0222` 挂到 `platform_system_admin`。

这一切**不依赖 `NEXUS_DEMO_ENABLED`，也不需要回环数据库**。演示机仍禁止打开 `NEXUS_DEMO_ENABLED`（非回环库会拒启）与 `NEXUS_P1_DEMO_CONTEXT_ENABLED`（与 U2 同开会在写入前拒启）。配置的平台租户必须是 U3 保留租户 `…0001`。职责与授权顺序、冲突策略以 [ID1 / ID3](id1-u2-identity-bootstrap.md) 为准。

因此：部署含 ID1 的构建 → 先清孤儿 → 开一次 U2 窗口 → 按 [TC5](../../planning/TC5-三人登录TenantContext验收清单.md) 复验三人登录带 `context_id` → 关窗口再重启。复验通过之前不宣称旧卡点已关闭。

## ID1 之前的行为（历史记录）

ID1 之前的启动任务在窗口打开时会：安装平台职责，并把 person `…0222` 挂到 `platform_system_admin`（人还不存在时只写下授权主体）；按固定 id 对齐已有账号的 username / 岗位 / 主职。三人账号、person、membership 的 **插入** 在 P2 演示种子里，只在 `NEXUS_DEMO_ENABLED=true` 且数据库为本机回环时执行。演示机不满足该条件，因此 **只开 U2 窗口不会凭空插入 `…0221` / `…0201` / `…0202`**。固定行还不存在时，U2 之后登录仍会再走出「缺少 TenantContext」。插入这三套身份需要单独的产品变更（经 PR 合入 `main` 后再部署），不能在演示机手写 SQL 或热修。
