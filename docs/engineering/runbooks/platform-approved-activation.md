# 平台批准后自动开通

产品规则：平台批准待办开通申请后，自动完成订阅、安装、默认流程/表单版本绑定和既有运行权限包下发，租户管理员无需再次确认。预备与本地测试不等于演示机验收；本变更不部署演示机。

## 安全路径与前置条件

- 依赖真实平台内容操作桥接（PR #758）：真实身份、未撤销/未过期平台 TenantContext、配置的平台租户、唯一 SYSTEM_ADMIN 职责及现有权限检查。不是 demo header 或 NEXUS_DEMO_ENABLED。
- 审批入口要求桥接生成的 P1 IsolationSnapshot 与 P2 当前 person/home tenant 匹配。ALL_DEMO、缺上下文、不同 actor 均拒绝。
- 服务在事务内 `SELECT ... FOR UPDATE` 锁定待审批申请。目标租户、SKU、版本仅从该记录取；不接受 HTTP 传入目标租户。
- 请求内 capability 绑定上下文及产品定义快照。专用仓储路径只 INSERT 目标记录，并核对申请、SKU、版本与 pin；普通租户写入的所属租户检查未改变。没有伪造目标 TenantContext。
- 默认权限包仍是既有六项流程运行能力，且只赋给目标租户 ACTIVE person；不解释任意输入的管理权限、不授予平台职责。既有 DENY 仍由权限引擎优先处理。
- 任一步失败不得显示 APPROVED；数据库订阅/安装/pin/审订记录在同一事务回滚。实际 JDBC 权限与审计仓储须使用应用同一事务数据源。审计保留真实平台 actor、home tenant、目标 tenant 和申请/安装关联。
- 重复批准已非 PENDING，拒绝而非再次安装；拒绝也锁定同一申请，避免与批准并发覆盖。

## 验证与限制

`PlatformActivationCommandTest` 覆盖权限边界、目标约束、请求生命周期、产品快照、固定权限包和赋权幂等。`MarketActivationApplicationTest` 覆盖服务编排。

`PlatformActivationPersistenceIT` 必须显式提供 `-Dnexus.test.activation.jdbc=jdbc:postgresql://127.0.0.1:<非5432端口>/test_<一次性库>`；不使用应用 datasource，也不允许已有 nexusone 业务库。用 V600/V601/V705/V706 建市场表，验证真实 JDBC 提交、失败回滚、重复批准和普通写入拒绝。测试仅以 mock 替代 IAM/权限包/审计以及 entitlement projection，未证明这些外部协作者、完整 HTTP 或浏览器验收。

合入并正规部署后，按真实账号复验：租户申请 → 平台批准 → 租户安装列表/权限可用 → 普通用户发起 → 审批含意见 → 我的申请。保留实际数据与失败 trace，不宣称未跑过的路径已通。平台默认流程与租户自己的版本配置分别依赖 #762/#765/#769；本 PR 不改变其规则。
