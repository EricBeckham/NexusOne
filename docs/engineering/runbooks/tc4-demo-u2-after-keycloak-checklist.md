# TC4：U4 建用户之后必须做 U2 一次性

> 下文记录 ID1 之前的部署断点。包含 ID1 的已合构建改为 U2 窗口先插入/对齐三套身份再挂职责，不依赖本机 demo 开关；新步骤和冲突策略见 [ID1 / ID3](id1-u2-identity-bootstrap.md)。只有部署并复验该构建后才能关闭旧断点，本文不宣称演示机已修复。

只改文档。不改演示机，不删库，不改 `NEXUS_*`。

## 预期卡点

DEMO-U4 只在 Keycloak 建三人。建完就登录、而 IAM 还没有对应 person 和恰好一条 ACTIVE membership 时，账号对账会按邮箱新建 global account。该账号 0 个 person、0 条 membership。`loginWithAccessToken` 只在恰好 1 条 ACTIVE membership 时签发 `context_id`，页面停在「缺少 TenantContext」。这是预期卡点，不是 LOGIN6 回调故障。

所以：**U4 建用户之后，必须按 [DEMO-U2](demo-u2-platform-duty-bootstrap.md) 做一次一次性启动任务**（`NEXUS_P1_DEMO_CONTEXT_ENABLED` 保持 false，临时打开 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED`，用已合入 `main` 的构建重启，验收后再设回 false 再重启）。不要改 `NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 来绕过这个卡点。不要打开 `NEXUS_DEMO_ENABLED`：演示机数据库不是本机回环，该开关会拒绝启动。

## 孤儿邮箱

`iam_global_account.email` 唯一。`platform.admin@demo.invalid` 若已被一次登录写成孤儿（固定 id `…0221` 之外的另一行，且 0 person / 0 membership），U2 窗口里的身份对齐只 `UPDATE` 固定 id，不会把孤儿改成 `…0221`。之后再插入固定账号会和孤儿抢同一邮箱。

打开 U2 之前，该邮箱在未删除的 `iam_global_account` 上必须是 0 行，或已经是固定 id `…0221`。删行属于运维动作，须单独确认后再做；本文不授权删除。

## 一次性能写什么

已合入的启动任务在窗口打开时会：安装平台职责，并把 person `…0222` 挂到 `platform_system_admin`（人还不存在时只写下授权主体）；按固定 id 对齐已有账号的 username / 岗位 / 主职。三人账号、person、membership 的 **插入** 在 P2 演示种子里，只在 `NEXUS_DEMO_ENABLED=true` 且数据库为本机回环时执行。演示机不满足该条件，因此 **只开 U2 窗口不会凭空插入 `…0221` / `…0201` / `…0202`**。固定行还不存在时，U2 之后登录仍会再走出「缺少 TenantContext」。插入这三套身份需要单独的产品变更（经 PR 合入 `main` 后再部署），不能在演示机手写 SQL 或热修。
