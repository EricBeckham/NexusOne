# TC1：演示机「缺少 TenantContext」只读核实

只读结论。本卡不改产品、不改演示机 jar / SPA / 环境变量 / 数据库。

## 结论

主因是**身份种子未对齐**，不是 LOGIN6 回调失败，也不是「已有单成员却未签发 context」。

`platform.admin@demo.invalid` 登录时，IAM 按邮箱新建了孤儿账号（id 前缀 `01a0e15d`，用户名 `nexus-demo-platform`，状态 ACTIVE）。该账号 **0 个 person、0 条 membership**。`SessionService.loginWithAccessToken` 只在恰好 1 条 ACTIVE membership 时调用 `issue()`；0 条时 `context_id` 为空。前端 AUTH5 自动选租户只处理至少 2 条 ACTIVE，因此回调停在「缺少 TenantContext」。回调本身已完成登录。

`lin.xiao@demo.invalid`、`zhou.ran@demo.invalid` 在 IAM 中不存在。U3 固定账号 `…0221` / `…0201` / `…0202` 与固定 person `…0222` / `…0203` / `…0204` 均不存在。

演示机进程环境中没有 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED`（缺省 false），`NEXUS_P1_DEMO_CONTEXT_ENABLED=false`，`NEXUS_P1_PLATFORM_READ_CONTEXT_ENABLED` 同样未设置（缺省 false）。平台租户 `perm_role` 为 0。DEMO-U2 一次性没有跑过。

## 邮箱冲突

`iam_global_account.email` 唯一。U3 要把 `platform.admin@demo.invalid` 写到固定 id `…0221`，而该邮箱已被孤儿 `01a0e15d` 占用。种子语句按 id 做 `ON CONFLICT DO NOTHING`，不会把孤儿改写成 `…0221`。直接开 U2 窗口跑 U3 对齐会在插入固定账号时撞上这条邮箱。

须先处理孤儿，再按 `demo-u2-platform-duty-bootstrap.md` 打开一次 U2，然后做三人验收。本卡不执行这三步。

## 核实范围

2026-09-27 对演示机只读：进程环境变量只记录上述三个开关的有无与 true/false；SQL 只核对用户名、邮箱、状态、人数与成员数。未登录 Keycloak，未回显口令、token 或连接串。
