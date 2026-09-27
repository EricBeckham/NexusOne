# ID1 / ID3 · U2 窗口补齐 U3 固定身份

产品变更必须经 PR 合入 main、构建后再部署。本文与本机回归不是演示机验收；禁止热修 jar/SPA/class、手写 SQL 造身份，不存储口令、token 或 client secret。

## 开关与执行顺序

默认关闭。使用 [U2 一次性窗口](demo-u2-platform-duty-bootstrap.md)：

1. 核对三套 [U3 固定身份表](demo-u3-identity-packs.md) 的邮箱归属。孤儿邮箱必须已由另行授权的运维流程处理；本任务不删除、不合并、不抢绑账号。
2. 保持 `NEXUS_DEMO_ENABLED=false`、`NEXUS_P1_DEMO_CONTEXT_ENABLED=false`，仅开启 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true`，使用包含 ID1 的已合构建启动一次。无需回环数据库；不要为了插入身份打开本机 demo 模式。
3. U2 先在数据库事务内补齐账号 `…0221 / …0201 / …0202`、person `…0222 / …0203 / …0204`、成员资格、部门/岗位及主职并提交，再安装平台职责并把已存在的 `…0222` 挂到 `platform_system_admin`。IAM/权限可能使用不同数据源，不能假定跨数据源原子事务；身份先提交可避免权限安装失败时身份被回滚而留下悬空主体。不安装本机专用 `demo_operator` 角色。职责安装失败可按同一窗口重试，不能把失败启动算作通过。
4. 验收固定账号/person、U3 邮箱/username/岗位、一条 ACTIVE membership 和一条 ACTIVE PRIMARY 主职；再按 [TC5](../../planning/TC5-三人登录TenantContext验收清单.md) 检查真实授权码登录及 `context_id`。本任务不创建 Keycloak 用户，不代替真实登录验收。
5. 把 `NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED` 关闭再重启；已有身份和授权保留，后续启动不写入。AUTH6 的只读桥接开关单独按其 runbook 管理。

同时开启 U2 与 P1 demo context 会在写入之前拒启。配置的平台租户必须是 U3 保留租户 `…0001`，不向任意平台租户植入固定演示身份。

## 已有行与冲突策略

- 固定 id 不存在：插入 U3 行。已存在：对齐邮箱、username、显示名、岗位及主职；保留真实 `keycloak_user_id`，不覆盖 IdP subject。
- 仅处理三个固定账号；目标租户 membership 激活，其余未删除的 membership 置 SUSPENDED，因此每个固定账号恰好一条 ACTIVE。主职按 U3 固定岗位对齐，其他 ACTIVE 职务降为 SECONDARY；不处理无关用户。
- 邮箱若被其他账号占用（包括大小写变体或软删除行），抛出 `ID1 identity conflict`，不抢占邮箱、不打印身份凭证。固定账号已删除/停用、person 归属不符、固定成员资格或主职/岗位/部门已删除也拒绝；先查明归属再按独立流程处理。
- 插入中遇到唯一约束等数据库冲突，身份事务回滚，尚未安装任何新平台职责或授权；异常导致启动失败，不吞错。错误可能包含非秘密字段，外部日志仍须按常规脱敏。
- 重复启动使用固定 id/租户账号唯一键，不追加身份、成员资格或平台授权；标签无变化不更新账号版本。

## 本机回归范围（ID3 并入 ID1）

`U2IdentityBootstrapPostgresTest` 使用一次性 PostgreSQL、既有 V200 schema 和真实 JDBC，覆盖空库、重复启动、旧行 align、IdP subject 保留、三种孤儿邮箱冲突、错误 person 归属及中途唯一约束失败回滚。`PlatformDutyBootstrapTest` 继续覆盖关闭窗口、P1 demo context 拒绝、职责重复安装与不授予租户物理导出。没有新增 Flyway，没有演示机操作。

完整启动回归为 `U2IdentityBootstrapBootTest`：显式配置 Spring、IAM、权限三个 JDBC 连接到同一个一次性 PostgreSQL；demo 模式和 P1 demo context 均关闭，检查身份及无悬空平台授权的真实落库和重复运行。

### 本次执行偏差记录（不能当作隔离验证通过）

2026-09-27 首轮完整启动补测遗漏 IAM/权限 JDBC URL 的显式覆盖，模块 `application.yml` 默认值使权限路径连接了本机既有 `127.0.0.1:5432/nexusone`，不是演示机。启动测试的授权落库断言失败；只读核查该轮 14:06–14:07（Asia/Shanghai）窗口内存在 5 条角色更新、3 条授权新增。另有策略版本/授权规则的可能写入，不能据上述计数宣称仅影响这 8 行。未擅自回滚、删除或改写该库；处理该偏差需要独立确认和准确核查，不提供猜测性的还原 SQL。已在聊天报告。

测试现已显式覆盖 `nexus.iam.jdbc.*` / `nexus.permission.jdbc.*` 到一次性库并断言权限仓储为 JDBC；后续验证与该失败尝试分开记账。不把本机验证当作真实 Keycloak/演示机验收。
