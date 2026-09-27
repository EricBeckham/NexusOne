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

- 固定账号/person id 不存在：插入 U3 行。已存在：对齐邮箱、username、显示名及职务引用；保留真实 `keycloak_user_id`，不覆盖 IdP subject。ID9 起不再改写已有岗位标签，部门/岗位按租户内 code 复用整行。
- 仅处理三个固定账号；目标租户 membership 激活，其余未删除的 membership 置 SUSPENDED，因此每个固定账号恰好一条 ACTIVE。主职按 U3 岗位 code 解析的实际 id 对齐，其他 ACTIVE 职务降为 SECONDARY；不处理无关用户。
- 邮箱若被其他账号占用（包括大小写变体或软删除行），抛出 `ID1 identity conflict`，不抢占邮箱、不打印身份凭证。固定账号已删除/停用、person 归属不符、固定成员资格或主职已删除也拒绝；部门/岗位按下文 ID7/ID9 的租户/code 检查可用行，不依赖固定 UUID。软删除占用 code 仍拒启。先查明归属再按独立流程处理。
- 插入中遇到唯一约束等数据库冲突，身份事务回滚，尚未安装任何新平台职责或授权；异常导致启动失败，不吞错。错误可能包含非秘密字段，外部日志仍须按常规脱敏。
- 重复启动使用固定 id/租户账号唯一键，不追加身份、成员资格或平台授权；标签无变化不更新账号版本。

## 本机回归范围（ID3 并入 ID1）

### ID7 · 部门自然键冲突修复

基线 `07198104` / #720：固定部门只使用 `ON CONFLICT (id) DO NOTHING`，但 V200 的 `uk_iam_department_code` 唯一键为 `(tenant_id, code)`。租户内已有 `platform_ops` 且 id 不同，部门 INSERT 抛 `DuplicateKey` → 同事务已插入的账号/person/membership 回滚 → U2 runner 抛错、进程退出；职责安装位于身份事务提交之后，不会在本次失败后继续新增授权。此前已存在的授权不属于本次事务，也不会被删除。

ID7 对 `platform_ops`、`engineering`、`platform_team` 改用 `ON CONFLICT (tenant_id, code) DO NOTHING`，随后按同一租户/code 读回实际 id，部门不存在时继续插入固定 id。已有真实部门的 id、名称、parent/path、负责人、状态、版本均保留；新职务及 U3 主职对齐使用实际部门 id。新建 `platform_team` 的 parent/path 使用实际 `engineering` id，不指向未插入的固定 id。同 code 在其他租户的行不能被复用。

不删除部门、不抢固定 id、不恢复已软删除的部门。code 被软删除行占用时会报 `ID7 department code is unavailable` 并回滚；固定 id 被另一 code 占用仍诚实失败，须另行查明归属。没有 Flyway；不改变三人固定账号/person/目标 membership 与先提交 person `…0222`、再赋 `platform_system_admin` 的顺序。

可抛弃 PostgreSQL 中新增三个参数化回归先在基线上全部复现 `uk_iam_department_code`；修复后覆盖已有同 code/不同 id 的部门整行保留、职务实际关联、三套固定身份、一条 ACTIVE membership、其他租户不受影响及重复开窗。空库与真实 JDBC 完整启动回归继续执行，Spring/IAM/权限 JDBC 均显式指向新的一次性库，禁止使用本机已有 `nexusone` 或演示库。

部署前仍须 PR 合入 main 后构建。演示机只部署、测试该构建并通过 U2 窗口插入身份，保持 `NEXUS_DEMO_ENABLED=false` 与 `NEXUS_P1_DEMO_CONTEXT_ENABLED=false`；本文不授权修改演示机 env、热修或手写 SQL 插身份，不代表该演示机已验收。

`U2IdentityBootstrapPostgresTest` 使用一次性 PostgreSQL、既有 V200 schema 和真实 JDBC，覆盖空库、重复启动、旧行 align、IdP subject 保留、三种孤儿邮箱冲突、错误 person 归属及中途唯一约束失败回滚。`PlatformDutyBootstrapTest` 继续覆盖关闭窗口、P1 demo context 拒绝、职责重复安装与不授予租户物理导出。没有新增 Flyway，没有演示机操作。

完整启动回归为 `U2IdentityBootstrapBootTest`：显式配置 Spring、IAM、权限三个 JDBC 连接到同一个一次性 PostgreSQL；demo 模式和 P1 demo context 均关闭，检查身份及无悬空平台授权的真实落库和重复运行。

### ID9 · 岗位自然键冲突修复

复现基线为 `c3c432b8`（含 ID7 `665968f2` / #723、ID8 #724）。`ensureIdentities` 四处 `iam_position` INSERT 只 `ON CONFLICT (id) DO NOTHING`，但 V200 的 `uk_iam_position_code` 是 `UNIQUE (tenant_id, code)`。同租户同 code、不同 id → `DuplicateKey` → 同事务身份整体回滚 → U2 runner 抛错、进程退出；身份提交后才安装职责，所以失败不新增授权。四种一次性 PostgreSQL 回归在修复前均复现该唯一键错误，不需要触碰演示库。

ID9 对平台租户 `demo_platform_ops` 及租户侧 `demo_tenant_admin`、`demo_member`、`engineering_manager` 使用 `ON CONFLICT (tenant_id, code) DO NOTHING`，随后按同租户/code 读取未删除行的实际 id。无同 code 行仍插固定种子 id；已有岗位整行保留，不改名称/分类/职责/状态/版本，不删除、不抢写。其他租户同 code 行不能被复用。旧的按固定 UUID 改岗位标签逻辑已移除；旧 code 或被占用的固定 UUID 不能靠抢写改名修复，须另行核实归属。

职务 INSERT 与已有 PRIMARY/SECONDARY 引用对齐均使用实际岗位 id 和 ID7 实际部门 id；账号/person/目标 membership 的 U3 固定 id 不变。软删除行占用 code 时抛 `ID9 position code ... is unavailable`，不恢复，身份事务回滚、不新增职责或授权。先提交含 person `…0222` 的身份，再挂 `platform_system_admin` 的顺序保持不变。既有唯一键已覆盖需求，无需 Flyway。

回归覆盖四种岗位自然键复用、整行保留、跨租户隔离、已有职务引用纠正、重复开窗幂等及四种软删除 code 的回滚；保留 ID7 三种部门复用与部门软删除用例、空库固定岗位/三人身份、邮箱冲突、真实 JDBC Spring 启动回归。所有 PostgreSQL 数据/临时产物位于本 ID9 worktree，一次性随机端口 `/postgres`；Spring、IAM、权限 JDBC 显式钉死，禁止连接本机已有 `nexusone` 或演示库。

本卡不操作演示机。须 Eric 批准合入后再构建部署；仍只通过 U2 窗口插身份，不开启 `NEXUS_DEMO_ENABLED` 或 P1 demo context，不提供手写 SQL 造身份步骤。本机回归不等于演示机或 Keycloak 真机验收。

### 本次执行偏差记录（不能当作隔离验证通过）

2026-09-27 首轮完整启动补测遗漏 IAM/权限 JDBC URL 的显式覆盖，模块 `application.yml` 默认值使权限路径连接了本机既有 `127.0.0.1:5432/nexusone`，不是演示机。启动测试的授权落库断言失败；只读核查该轮 14:06–14:07（Asia/Shanghai）窗口内存在 5 条角色更新、3 条授权新增。另有策略版本/授权规则的可能写入，不能据上述计数宣称仅影响这 8 行。未擅自回滚、删除或改写该库；处理该偏差需要独立确认和准确核查，不提供猜测性的还原 SQL。已在聊天报告。

测试现已显式覆盖 `nexus.iam.jdbc.*` / `nexus.permission.jdbc.*` 到一次性库并断言权限仓储为 JDBC；后续验证与该失败尝试分开记账。不把本机验证当作真实 Keycloak/演示机验收。
