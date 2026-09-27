# TC4：U4 建用户之后必须做 U2 一次性

> ID1 / ID3 已合入 `main`（#720，`07198104`）：包含 ID1 的构建改为 U2 窗口先插入/对齐三套身份再挂职责，不依赖本机 demo 开关；见下文「一次性能写什么」与 [ID1 / ID3](id1-u2-identity-bootstrap.md)。「ID1 之前的行为」一节只作历史记录。
>
> **更正（ID8）**：只有在**同时包含 ID7（#723）**的构建上，「开 U2 窗口即可插入身份」才对演示库成立。纯 #720 的包遇到平台租户已有 `code=platform_ops`（id 与种子固定 id 不同）会撞部门唯一键、身份整体回滚、进程退出，见下文「部门 code 冲突（ID7）」。只有重新部署含 ID7 的包、再跑 U2 窗口并按 TC5 复验通过后才能关闭旧断点，本文不宣称演示机已修复。

> **补充（ID9）**：ID7 只解决部门自然键。已有同租户 `demo_platform_ops` 岗位且 id 不同时，含 ID7 的包仍会撞 `uk_iam_position_code`，身份回滚并退出；租户侧三个固定岗位 code 同理。必须使用包含已合 ID9 的新构建再按 U2/TC5 复验，不能把下文「含 ID7」理解为已经足以跨过岗位断点。ID9 让部门和岗位都按租户内 code 复用整行，职务关联读回的实际 id，软删除 code 仍拒启。详见 [ID9](id1-u2-identity-bootstrap.md#id9--岗位自然键冲突修复) 与 [U2](demo-u2-platform-duty-bootstrap.md)。ID9 未合前不宣称该断点已关闭。

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

因此：部署**含 ID7** 的构建 → 先清孤儿 → 开一次 U2 窗口 → 按 [TC5](../../planning/TC5-三人登录TenantContext验收清单.md) 复验三人登录带 `context_id` → 关窗口再重启。复验通过之前不宣称旧卡点已关闭。上面的 1–3 步在纯 #720 包上只对「部门 code 尚未被占用」的库成立。

## 部门 code 冲突（ID7）

### 失败模式（ID1 基线 #720）

身份事务里的三个固定部门 `platform_ops`、`engineering`、`platform_team` 在 #720 中用 `INSERT … ON CONFLICT (id) DO NOTHING` 插入，只防固定 id 重复。但 V200 另有唯一键 `uk_iam_department_code (tenant_id, code)`。演示库平台租户里**已有 `code=platform_ops` 的部门、且 id 不是种子固定 id** 时：

1. 部门 INSERT 撞 `uk_iam_department_code`，抛 `DuplicateKey`；
2. 同一身份事务回滚，本次已插入的账号 / person / membership 全部撤销；
3. U2 runner 抛错，进程退出；
4. 结果是三人账号插不进去，登录仍缺 TenantContext。职责安装在身份提交之后，因此这次失败不会新增平台职责或授权；此前已有的授权也不会被删除。

`engineering`、`platform_team` 已有同 code、不同 id 的行时同理。

### 修复后行为（ID7 #723）

- 部门按租户内自然键 `(tenant_id, code)` 处理：已有同 code 的行就**复用它的实际 id**，不删除、不改写这行真实部门（id、名称、parent/path、负责人、状态、版本都保留）。
- 租户内没有同 code 行时，仍插入固定种子 id。其他租户的同 code 行不会被复用。
- 职务和 U3 主职对齐使用解析后的实际部门 id。
- 新建的 `platform_team` 的 parent / path 跟随实际的 `engineering` 行。
- code 被**软删除**的行占用时，仍诚实拒启（`ID7 department code is unavailable`）并回滚，不当作空行，也不恢复那行。
- 三人账号 / person / membership 的规则不变；「先提交身份（含 person `…0222`），再把 `…0222` 挂到 `platform_system_admin`」的顺序不变。
- 没有 Flyway；演示机仍禁止打开 `NEXUS_DEMO_ENABLED`。

权威描述以 ID7 的 [ID1 / ID7 runbook](id1-u2-identity-bootstrap.md) 与 [DEMO-U2](demo-u2-platform-duty-bootstrap.md) 为准。ID7 未合入 `main` 前，以 PR #723 正文为准；发布 SHA 须晚于其合入提交。

### 历史：ID5 在演示机上的实测（不是操作步骤）

Cursor ID5 在纯 #720 包上开过一次 U2 窗口，结果如下：

- 失败点在部门插入（上面的失败模式）；
- 三人的固定账号 / person 仍是 0 行；
- 开关之后已关回。

这只是记录，说明为什么需要 ID7；不要照此重做。下一步是部署含 ID7 的包，再按本节和 TC5 走一遍。

## ID1 之前的行为（历史记录）

ID1 之前的启动任务在窗口打开时会：安装平台职责，并把 person `…0222` 挂到 `platform_system_admin`（人还不存在时只写下授权主体）；按固定 id 对齐已有账号的 username / 岗位 / 主职。三人账号、person、membership 的 **插入** 在 P2 演示种子里，只在 `NEXUS_DEMO_ENABLED=true` 且数据库为本机回环时执行。演示机不满足该条件，因此 **只开 U2 窗口不会凭空插入 `…0221` / `…0201` / `…0202`**。固定行还不存在时，U2 之后登录仍会再走出「缺少 TenantContext」。插入这三套身份需要单独的产品变更（经 PR 合入 `main` 后再部署），不能在演示机手写 SQL 或热修。
