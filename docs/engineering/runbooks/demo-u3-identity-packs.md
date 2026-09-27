# DEMO-U3 · 三套演示身份

与 [DEMO-U4](demo-u4-keycloak-three-users.md) 对齐的冻结表。口令不在本仓库。Keycloak 若把邮箱当作 Username，登录名填邮箱；下表 Username 是清单标签，也是 IAM `username`。

| 身份 | 邮箱 `EMAIL_*` | Username | 岗位 code | 主职显示名 | 租户 |
|---|---|---|---|---|---|
| 平台运营 | `platform.admin@demo.invalid` | `nexus-demo-platform` | `demo_platform_ops` | 平台运营 | 平台租户 `…0001` |
| 租户管理（林晓） | `lin.xiao@demo.invalid` | `nexus-demo-tenant-admin` | `demo_tenant_admin` | 租户管理 | `demo_acme` `…0101` |
| 一般用户（周然） | `zhou.ran@demo.invalid` | `nexus-demo-member` | `demo_member` | 一般用户 | `demo_acme` `…0101` |

固定 UUID 与既有演示种子相同（账号 `…0221` / `…0201` / `…0202`，人员 `…0222` / `…0203` / `…0204`）。插入使用 `ON CONFLICT DO NOTHING`，已有行用按这些 id 的 UPDATE 改岗位 code、username 和主职。演示主职不再使用 `TENANT_ADMIN` / `PLATFORM_ADMIN`。产品初始化器仍可创建真实租户的 `TENANT_ADMIN`；订阅门禁同时接受 `demo_tenant_admin`。

同一账号不得同时在平台租户和 `demo_acme` 上保持 ACTIVE。平台侧已有 person 的账号，其 `demo_acme` 成员资格改为 `SUSPENDED`；只有 `demo_acme` person 的账号，其平台成员资格改为 `SUSPENDED`。

本地演示开关打开时，P2 种子会做上述对齐。演示机不打开 `NEXUS_DEMO_ENABLED` / `NEXUS_P1_DEMO_CONTEXT_ENABLED` 时，沿用 DEMO-U2 的一次性启动任务：`NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true` 且演示上下文保持 false，重启一次会安装平台职责并执行同样的身份对齐。没有 Flyway。
