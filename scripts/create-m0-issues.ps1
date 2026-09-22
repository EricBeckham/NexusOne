$ErrorActionPreference = 'Stop'
$repo = 'EricBeckham/NexusOne'

gh label create 'phase:M0' --repo $repo --color '1d76db' --description 'First executable business slice' --force | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Failed to create M0 label' }

$existing = @(gh issue list --repo $repo --state all --limit 200 --json title | ConvertFrom-Json | ForEach-Object title)
if ($LASTEXITCODE -ne 0) { throw 'Failed to list issues' }

$tasks = @(
    @{ Id='M0-01'; Name='工程与 PostgreSQL 部署底座'; Depends='P0 版本冻结；可先设计模块与部署契约。'; Scope='Java 21/Spring Boot GA、Vue 3、模块化单体骨架、PostgreSQL、Flyway、Compose/systemd、健康/日志/Trace。'; Done='冻结的 PostgreSQL Profile 可安装、启动、升级、回滚及离线部署；G8/G13，G14 只记录 PostgreSQL 范围。' },
    @{ Id='M0-02'; Name='租户隔离内核'; Depends='P0-01 的 AST/Guard 契约。'; Scope='TenantContext 服务端签发；Query AST 与 Guard；PlatformContext 白名单审计；Job/Task/Outbox/缓存/文件边界；CI 静态检查。'; Done='双租户 UI/API/查询/文件/缓存/异步路径隔离，守卫拒绝在数据库执行前发生；G1。' },
    @{ Id='M0-03'; Name='P1 租户、平台范围与基础 Quota'; Depends='M0-01/02；P2 管理员初始化接口。'; Scope='创建/启停租户、管理员初始化、保留平台租户、基础配置、默认权益投影、应用数和自建台账数 Quota。'; Done='两个租户独立创建和启停；平台租户受保护；超额拒绝可审计。' },
    @{ Id='M0-04'; Name='P2 账号、人员、组织与岗位'; Depends='P0-03；M0-02。'; Scope='Keycloak 登录/登出/会话、人员、部门、岗位/任职、全局账号与租户成员、邀请和找回密码所需 SMTP 配置。'; Done='登录后由服务端签发租户上下文；P2 与 Keycloak 可对账。' },
    @{ Id='M0-05'; Name='P2 权限与 PolicySnapshot'; Depends='P0-02；M0-02/04。'; Scope='角色及用户/岗位/部门授权；本人/部门/下级 DataScope；基础字段权限；显式拒绝；PolicySnapshot。'; Done='UI/API/列表/详情/流程同源判定；版本轮询、缓存失效与故障拒绝；G2/G9。' },
    @{ Id='M0-06'; Name='P3 台账 Schema 与数据 CRUD'; Depends='P0-01/08；M0-03/05。'; Scope='应用、单个台账、基础字段、SchemaService 动态建表、CRUD/列表/详情/条件查询、Quota。'; Done='发布后可写可查，租户和 DataScope 在数据库返回前过滤，total/分页正确。' },
    @{ Id='M0-07'; Name='P3 基础表单与发布'; Depends='M0-06。'; Scope='基础表单及 Draft→Validate→Publish→Running 生命周期。'; Done='表单可填报；校验失败不污染运行版本，发布失败可回滚；G5。' },
    @{ Id='M0-08'; Name='顺序审批与待办'; Depends='P0-04；M0-05/06/07。'; Scope='Flowable 开始→人工审批→结束；人员/岗位/角色办理人；发起、同意/驳回、撤回、待办/已办/我发起。'; Done='业务数据与流程同库同事务；重复操作幂等；审批可审计。' },
    @{ Id='M0-09'; Name='Quartz、Task Queue 与 Outbox'; Depends='P0-07；M0-02。'; Scope='Quartz 定时、数据库 Task Queue、Outbox+Dispatcher、数据库幂等表、重试/失败池/对账。'; Done='事件含租户/trace，消费幂等；Outbox 开始投递 P95≤5 秒、>30 秒告警。' },
    @{ Id='M0-10'; Name='Redis、文件服务与站内通知'; Depends='P0-05/06；M0-02/09。'; Scope='Redis CachePort、S3 File Service 租户前缀/签名 URL 权限复核、站内通知模板与异步投递。'; Done='审批触发站内通知；跨租户文件访问在签名前拒绝。' },
    @{ Id='M0-11'; Name='审计、密码、Secret 与整库恢复'; Depends='P0-10；M0-02/04/06/08。'; Scope='登录/权限/管理/数据/审批/守卫审计，append-only 哈希链，CryptoPort 双套件，SecretPort，整库与对象恢复。'; Done='全链按 trace 可追溯、敏感值不入日志、恢复有实测记录；G4/G6/G17。' },
    @{ Id='M0-12'; Name='前端与端到端真实业务验收'; Depends='M0-03～11。'; Scope='租户管理、组织权限、台账/表单/待办/查询最小 UI；真实业务脚本与双租户对抗用例。'; Done='完成一条租户→权限→台账→表单→发布→数据→顺序审批→待办→查询→通知→审计闭环；G18。' }
)

foreach ($task in $tasks) {
    $title = "[$($task.Id)] $($task.Name)"
    if ($existing -contains $title) {
        Write-Output "Exists: $title"
        continue
    }
    $body = @"
## 范围

仅在 PostgreSQL 上实施与验证。来源：`docs/planning/实施任务拆解_V1.1.md` §3。M0 是 S0/S1 子集，正式 S1 的三类业务门禁另行验收。

**前置依赖：** $($task.Depends)

$($task.Scope)

## 完成判定

$($task.Done)

领取时补充负责人、目标 Profile、代码/证据位置、验收人和时间。当前仓库只有所有者可作 GitHub Assignee，此 Issue 先留待领取。
"@
    $path = Join-Path $env:TEMP "nexusone-$($task.Id)-issue.md"
    Set-Content -LiteralPath $path -Value $body -Encoding utf8
    try {
        gh issue create --repo $repo --title $title --body-file $path --label 'phase:M0' --label 'database:PostgreSQL' --label 'status:blocked-dependency'
        if ($LASTEXITCODE -ne 0) { throw "Failed to create $title" }
    }
    finally {
        Remove-Item -LiteralPath $path -ErrorAction SilentlyContinue
    }
}
