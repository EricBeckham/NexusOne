$ErrorActionPreference = 'Stop'

$repo = 'EricBeckham/NexusOne'
$labels = @(
    @{ Name = 'phase:P0'; Color = '5319e7'; Description = 'P0 proof of concept' },
    @{ Name = 'database:PostgreSQL'; Color = '0e8a16'; Description = 'PostgreSQL execution scope' },
    @{ Name = 'status:active-agent'; Color = 'fbca04'; Description = 'Codex sub-agent currently working' },
    @{ Name = 'status:ready'; Color = 'c2e0c6'; Description = 'Ready to claim or start when dependencies allow' }
)

foreach ($label in $labels) {
    gh label create $label.Name --repo $repo --color $label.Color --description $label.Description --force | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to create label $($label.Name)" }
}

$existing = @(gh issue list --repo $repo --state all --limit 200 --json title | ConvertFrom-Json | ForEach-Object title)
if ($LASTEXITCODE -ne 0) { throw 'Failed to list existing issues' }

$tasks = @(
    @{ Id='P0-01'; Title='PostgreSQL Query AST 与租户隔离守卫 PoC'; Status='active-agent'; Agent='Codex data_access'; Dependency='无'; Deliverable='Query AST、TenantInjector、TableAccessPlan Guard、NativeSqlPort 白名单、PostgreSQL Renderer、table_registry 原型；隔离与拒绝契约测试。'; Evidence='缺失 TenantContext、跨租户写入、非白名单 PlatformContext 在 SQL 执行前拒绝；CI 禁止业务代码直接 JDBC。' },
    @{ Id='P0-02'; Title='Permission Engine 授权语义与快照 PoC'; Status='active-agent'; Agent='Codex permission'; Dependency='与 P0-01 并行定义 DataScope 接口，集成依赖 P0-01。'; Deliverable='RBAC、岗位/部门授权、Explicit Deny、DataScope、FieldPolicy、PolicySnapshot、Redis 失效策略。'; Evidence='权限合并真值表、DataScope Golden Test、策略版本和故障拒绝测试、P95 基准。' },
    @{ Id='P0-03'; Title='Keycloak 与 PostgreSQL 版本组合验证'; Status='active-agent'; Agent='Codex keycloak'; Dependency='无'; Deliverable='可复现的 PostgreSQL 部署与测试资产；锁定版本。'; Evidence='建库/升级、登录/OIDC/SAML、Organizations/Federation、Session/MFA、备份恢复逐项记录实际结果。' },
    @{ Id='P0-04'; Title='Flowable 同进程同库独立 Schema 验证'; Status='ready'; Agent='待领取'; Dependency='无，可与 P0-01～03 并行。'; Deliverable='Flowable 与业务应用共享 PostgreSQL DataSource/事务，独立 Schema。'; Evidence='BPMN/DMN、Task、Async Executor、Schema 升级、应用回滚兼容；不得直接访问 Flowable 内部表。' },
    @{ Id='P0-05'; Title='Redis 版本授权与缓存契约验证'; Status='ready'; Agent='待领取'; Dependency='无。'; Deliverable='Redis 版本/授权记录和 CachePort 契约。'; Evidence='TTL、锁、原子操作、故障转移、租户 Key；关键幂等键保存在 PostgreSQL。' },
    @{ Id='P0-06'; Title='MinIO/AIStor 产品线与 S3 契约验证'; Status='ready'; Agent='待领取'; Dependency='无。'; Deliverable='产品线、版本、来源、授权记录及 File Service 契约。'; Evidence='上传/分片/下载、签名 URL 的租户校验、对象前缀、升级、备份恢复、目标 CPU。' },
    @{ Id='P0-07'; Title='Quartz 与 Flyway 的 PostgreSQL 验证'; Status='ready'; Agent='待领取'; Dependency='无。'; Deliverable='Quartz JDBC JobStore 与 Flyway 静态 Schema 迁移验证资产。'; Evidence='Cluster、Misfire、故障转移、迁移失败恢复、升级/回滚记录。' },
    @{ Id='P0-08'; Title='动态台账容量与 Quota PoC'; Status='ready'; Agent='待领取'; Dependency='依赖 P0-01 的 table_registry 与 PostgreSQL Renderer。'; Deliverable='SchemaService、动态表/索引/Catalog 压力脚本、SchemaDriftCheck、1000 租户共享表模型。'; Evidence='冻结自建台账数、集群物理表数、索引数、DDL P95 等量化阈值。' },
    @{ Id='P0-09'; Title='共享应用表版本兼容与租户回滚 PoC'; Status='ready'; Agent='待领取'; Dependency='依赖 P0-08 共享表模型。'; Deliverable='A=v2、B/C=v1 的 Expand→Migrate→Switch→Contract 场景。'; Evidence='旧版本继续运行，A 回滚到 v1 后数据无损，不回滚物理 Schema；Contract 清理条件明确。' },
    @{ Id='P0-10'; Title='PostgreSQL 部署 Profile、Crypto 与供应链验收'; Status='ready'; Agent='待领取'; Dependency='环境准备可并行；总验收依赖 P0-01～09 版本和结果。'; Deliverable='PostgreSQL 目标 OS/CPU/JDK Profile、CryptoPort 国际/国密双套件、离线部署包。'; Evidence='安装/升级/回滚、Crypto 契约、SBOM、许可证/CVE 扫描、镜像来源与离线包记录。' }
)

foreach ($task in $tasks) {
    $title = "[$($task.Id)] $($task.Title)"
    if ($existing -contains $title) {
        Write-Output "Exists: $title"
        continue
    }
    $body = @"
## 执行范围

仅在 PostgreSQL 上实施和验证。对应任务板：`docs/planning/实施任务拆解_V1.1.md`。

**当前执行者：** $($task.Agent)
**前置依赖：** $($task.Dependency)

## 交付物

$($task.Deliverable)

## 完成证据

$($task.Evidence)

提交代码、运行步骤、环境/版本、测试结果和未验证项。未经实测的项目不得标为通过。
"@
    $bodyFile = Join-Path $env:TEMP "nexusone-$($task.Id)-issue.md"
    Set-Content -LiteralPath $bodyFile -Value $body -Encoding utf8
    try {
        gh issue create --repo $repo --title $title --body-file $bodyFile --label 'phase:P0' --label 'database:PostgreSQL' --label "status:$($task.Status)"
        if ($LASTEXITCODE -ne 0) { throw "Failed to create $title" }
    }
    finally {
        Remove-Item -LiteralPath $bodyFile -ErrorAction SilentlyContinue
    }
}
