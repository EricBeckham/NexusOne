# WEB 第四波任务卡（M 波 · N 波后续）V0.1

- 基线：`main@6056705`（`feat(p2): add identity integration page (#338)`；N1–N20 业务卡已合；N11 已合）
- 依据：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` §25–§29；`docs/planning/快进P4P5_范围冻结表_V0.1.md`；`docs/planning/WEB下一波任务卡_V0.1.md`（N 波已交付与「后续波」登记）；N9 #334 / N10 #336 安全审阅
- 范围：N 波之后的桌面 WEB 薄片 + **N10 安全加固优先组**。**本波明确不做 H5**（无 `/m/*`、无 `web/src/mobile/**`；仅在「不做」表登记一句）
- 执行方：本地 Codex / Cursor 按卡领取；NexusOne 只负责拆分与审阅

## 默认决策

1. **一卡一产品 + 一种资源类型或一个薄片**；后端 / 前端分卡；**新表 / Flyway 单独成卡**；**密钥 / Credential / SecretPort 安全敏感单独成卡**；**仅测试**可单独成卡。
2. **优先组 0 = N10 审阅跟进**（持久化密钥后端、否定测试、SSRF），再开业务薄片。
3. **先吃存货**：流程设计器在 N12/N13 只读之上薄切草稿/编辑/校验/发布；租户改挂在 N2/N3 只读之上；IdP 手动同步在 N8–N11 之上且**不宣称 SAML/LDAP**；P5 Credential 与 API Client 密钥分家。
4. **租户隔离硬约束**：每个后端读/写带租户边界 + 跨租户否定测试；平台级接口须平台职责。
5. **不编造数据**：空态「暂无」；演示数据只走 V-SEED（本波不新建 V-SEED 卡）。
6. **大件继续薄切 / 冻结后置**：发布中心真 Expand/Migrate/Switch/Contract、IdP 定时调度、S2 治理、备份恢复、开发者门户 — 本波不做。

## 所有任务通用约束

- 从最新 `origin/main` **新开分支 + 独立 worktree**；**禁止**在占用中的 `D:\NexusOne` 直接改；**禁止**动 `C:\Users\Administrator\.codex\worktrees\`。
- **一卡一 PR**；PR 只含本卡。
- UI 卡：只改 `web/src`，**排除** `web/src/mobile/**`。
- 后端 / 安全 / 测试卡：不改前端（除非卡面写明）。
- 迁移卡：只含 Flyway + 契约测试。
- 安全卡：密钥只经 Secret/加密端口；响应永不回显明文；写审计。
- Flyway **冲突区单写**：迁移卡串行合入。
- 交付门禁：
  - UI：`npm test`、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check`
  - 后端：相关模块测试绿；**含跨租户不可读/不可写测试**
- 原有深链保持可开。

---

## 一、分功能现状（已有 vs 缺失）

### 0) N10 密钥链路（优先）

| | 内容 |
|---|---|
| **已有** | N8–N11：IdP 元数据 CRUD、密钥 write/rotate/test-connection、身份集成页；`LocalEncryptedSecretAdapter`（进程内 `ConcurrentHashMap` 密文）+ `LocalThenEnvSecretPort`（本地覆盖优先，再 env）；密钥名形如 `iam.idp.{tenantId}.{id}.client-secret` |
| **风险** | 进程重启丢失租户 IdP client secret；`OidcClientCredentialsTester.safeHttpsPublicOrigin` 先解析 DNS 再 `openConnection`（TOCTOU）；write/rotate/testConnection 跨租户否定与非管理员 FORBIDDEN 覆盖不足（现状侧重 status 跨租户 + 未认证 PUT） |
| **缺失** | 可持久化密文后端；上述否定测试；连接后钉死/复核对端 IP |

### P1 租户改挂（§25.4，接 N2/N3）

| | 内容 |
|---|---|
| **已有** | `parent_tenant_id`；只读树/祖先/子节点 API + 关系页；Controller 明确不改挂 |
| **缺失** | 改挂写 API（环检测 + 审计）；FE 改挂交互 |

### P2 IdP 同步（§26.9，接 N8–N11）

| | 内容 |
|---|---|
| **已有** | 元数据含 `last_sync_*`；`IdentityReconcileController` 人员对账；诚实文档 **SAML/LDAP = false** |
| **缺失** | Provider 级「立即同步」与 FE 按钮；**不做**定时调度大件、不做 SAML/LDAP |

### P3 流程设计器（§27.6，接 N12/N13）

| | 内容 |
|---|---|
| **已有** | 类路径 BPMN 只读目录 + 只读查看器；实例/待办 API；`business_meta.process_*` 仅运行态 |
| **缺失** | 定义草稿表；草稿存取；节点编辑；Validate；Publish 定义（部署） |

### P3 发布中心 Expand/Migrate/…（§27.8）

| | 内容 |
|---|---|
| **已有** | 只读聚合 + 状态映射；表单 publish 事务 |
| **决策** | **本波不做**真 Schema Expand/Migrate/Switch/Contract（风险高、偏 S2）；继续后置 |

### P4 侧栏 later

| 项 | 决策 |
|---|---|
| 商业权益（后续） | **本波纳入**：只读投影（订阅/SKU→权益），不建计费 |
| 运营分析（后续） | **本波后置**：无聚合 API，避免假 KPI/假趋势 |

### P5 Credential（§29.7，侧栏 later）

| | 内容 |
|---|---|
| **已有** | `integration.api_client.secret_digest`（Client 签发）；目录无密钥 |
| **缺失** | 通用 Credential 元数据表 + SecretPort 引用 + Rotate/Revoke/Test UI（不明文） |

### 平台审计哈希链

| | 内容 |
|---|---|
| **已有** | N6/N7 基于 `audit_event` 检索；`platform.audit_record` + `AuditPort.verifyChain()`；P3 哈希链页 |
| **缺失** | 平台职责下的链完整性校验 HTTP；可选与 N7 页「校验」按钮 |

### 侧栏 `later: true` 全量（`web/src/nav/p*.js` @6056705）

| 产品 | id | 文案 | 本波 |
|---|---|---|---|
| P4 | entitlements | 商业权益（后续） | 纳入 M20/M21 |
| P4 | operations | 运营分析（后续） | **后置** |
| P5 | credentials | Credential（后续） | 纳入 M16–M19 |
| P1–P3 | — | 无 later | — |

---

## 二、任务总览

| 编号 | 标题 | 产品 | 类型 | 依赖 | 可并行 | 预估 |
|---|---|---|---|---|---|---|
| **M1** | SecretPort 持久化密文表迁移 | 平台/安全 | 迁移 | 无 | Flyway 串行 | 1h |
| **M2** | 持久化加密 SecretPort 后端 | 平台/安全 | **安全** | M1 | 其后可开 M3/M4 | 1–2h |
| **M3** | IdP 密钥跨租户/权限否定测试 | P2 | **仅测试** | 无（建议 M2 后） | 是 | 1h |
| **M4** | test-connection SSRF 加固 | P2 | **安全** | 无 | 与 M3 并行 | 1–2h |
| M5 | P3 流程定义草稿表迁移 | P3 | 迁移 | 无 | Flyway 串行（与 M1/M16 排队） | 1h |
| M6 | P3 流程定义草稿读写 API | P3 | 后端 | M5 | 其后 M7 | 1–2h |
| M7 | P3 流程设计器节点编辑+存草稿 | P3 | 前端 | M6 | 是 | 1–2h |
| M8 | P3 流程定义校验 API | P3 | 后端 | M5 | 可与 M6 后并行 | 1h |
| M9 | P3 流程设计器校验 UX | P3 | 前端 | M8、M7 | 是 | 1h |
| M10 | P3 流程定义发布 API | P3 | 后端 | M8 | 其后 M11 | 1–2h |
| M11 | P3 流程设计器发布 UX | P3 | 前端 | M10、M9 | 是 | 1h |
| M12 | P1 租户改挂写 API | P1 | 后端 | 无（N1–N3 已合） | 是 | 1–2h |
| M13 | P1 租户改挂 UI | P1 | 前端 | M12 | 是 | 1–2h |
| M14 | P2 IdP 立即同步 API | P2 | 后端 | 无（N8–N11 已合） | 是 | 1–2h |
| M15 | P2 IdP 立即同步按钮 | P2 | 前端 | M14 | 是 | 1h |
| M16 | P5 Credential 元数据表迁移 | P5 | 迁移 | 无 | Flyway 串行 | 1h |
| M17 | P5 Credential 密钥写入/轮转/吊销 | P5 | **安全** | M16（建议 M2 已合） | 是 | 1–2h |
| M18 | P5 Credential 元数据 CRUD API | P5 | 后端 | M16 | 与 M17 协调 | 1–2h |
| M19 | P5 Credential 管理页 | P5 | 前端 | M17、M18 | 是 | 1–2h |
| M20 | P4 商业权益只读投影 API | P4 | 后端 | 无 | 是 | 1–2h |
| M21 | P4 商业权益页 | P4 | 前端 | M20 | 是 | 1–2h |
| M22 | 平台审计哈希链校验 API | P1/审计 | 后端 | 无 | 是 | 1h |
| M23 | 平台审计中心「校验哈希链」 | P1 | 前端 | M22 | 是 | 1h |

建议分支前缀：`mig/m1-…` / `sec/m2-…` / `test/m3-…` / `feat/m6-…` / `ui/m7-…`。

---

## 三、任务卡正文

### 优先组 0 — N10 审阅跟进

### M1 — SecretPort 持久化密文表迁移

- **类型**：迁移　**分支**：`mig/m1-secret-ciphertext-store`　**前置**：无
- **目标**：为 durable SecretPort 提供仅存**密文**的表（无明文列）。建议号段冲突区给定（如 `platform.secret_ciphertext`：`secret_name` PK、`version`、`ciphertext` bytea、`key_id`、`updated_at`）。
- **区域**：`nexus-crypto-core` 或平台迁移目录；Flyway 契约测试证明无 plaintext/password/client_secret 列名。
- **不做**：适配器实现（归 M2）；OpenBao/KMS 产品化宣称。
- **验收**：migrate 绿；契约测试绿。

### M2 — 持久化加密 SecretPort 后端（安全）

- **类型**：安全　**分支**：`sec/m2-durable-encrypted-secret-port`　**前置**：M1
- **目标**：替换/扩展 `LocalEncryptedSecretAdapter` 的易失存储：put 写持久密文；get 解密；rotate 升版本且旧值不可读；进程重启后 `iam.idp.{tenantId}.{id}.client-secret` 仍可用。继续经 `LocalThenEnvSecretPort` 本地优先、再 env。
- **区域**：`nexus-crypto-core`（新 Durable 适配器或改造 Local）；装配；重启可测的单测/IT（同 JVM 重建 Port 实例即可，不必真杀进程）。
- **不做**：OpenBao；把明文写入 IdP 元数据表；改 FE。
- **验收**：put→新 Port 实例 get 成功；rotate 后旧 version 不可用；响应/日志无明文；测试绿。

### M3 — IdP 密钥跨租户/权限否定测试（仅测试）

- **类型**：仅测试　**分支**：`test/m3-idp-secret-negatives`　**前置**：无（建议 M2 合入后跑，避免跟易失后端打架）
- **目标**：补强 N10：`IdpSecretServiceTest` / `IdentityApiTest`（或等价）对 **write / rotate / test-connection** 断言：跨租户 → NOT_FOUND；非管理员 → FORBIDDEN；status 已有覆盖可保留。
- **不做**：改业务代码（除非测试暴露真缺陷，PR 说明并最小修）；前端；迁移。
- **验收**：新测试绿；PR 写明「去掉权限/租户检查时会红」的验证方式；约 1h。

### M4 — test-connection SSRF 加固（安全）

- **状态**：已合并（PR #340），负责人 Cursor
- **类型**：安全　**分支**：`sec/m4-idp-test-connection-ssrf`　**前置**：无
- **目标**：修复 `OidcClientCredentialsTester.safeHttpsPublicOrigin` 的 DNS TOCTOU：解析后**钉死**用于连接的地址，或 connect 后复核 peer IP 仍为公共地址；私网/回环/链路本地/组播/CGNAT 拒绝；HTTPS-only 保持。
- **区域**：`nexus-p2-iam`…`OidcClientCredentialsTester`（及测试）；可补伪造解析/自定义 Socket 的单测。
- **笔记（可同卡顺手，勿膨胀）**：密钥勿长时间以 `String` 拷贝滞留；审计失败不应在 put 成功后无法解释（文档或最小一致化）；`LocalThenEnvSecretPort.delete` 仅删本地覆盖 — 在 PR 说明，完整 env 删除不在本卡。
- **不做**：新表；FE；放开 HTTP issuer。
- **验收**：针对私网/再绑定类用例的测试证明不会把 client_secret 发进内网；公共 issuer 路径仍可用；约 1–2h。

---

### P3 流程设计器薄切

### M5 — P3 流程定义草稿表迁移

- **类型**：迁移　**分支**：`mig/m5-process-definition-draft`　**前置**：无
- **目标**：`business_meta.process_definition_draft`（或等价）：tenant_id、key、name、bpmn_xml/json、version、updated_by/at；租户索引。
- **不做**：Flowable 引擎表；发布态表（可与草稿同表加 status，但发布语义归 M10）。
- **验收**：migrate 绿；契约测试绿。

### M6 — P3 流程定义草稿读写 API

- **类型**：后端　**分支**：`feat/m6-process-definition-draft-api`　**前置**：M5
- **目标**：租户内 list/get/put 草稿；跨租户否定；不部署引擎。
- **不做**：Validate/Publish（M8/M10）；改类路径只读目录语义。
- **验收**：读写绿 + 跨租户否定；零前端文件。

### M7 — P3 流程设计器节点编辑 + 存草稿

- **类型**：前端　**分支**：`ui/m7-process-designer-edit-draft`　**前置**：M6
- **目标**：在 N13 只读查看器上启用最小编辑：M0 节点 Start / User Task / End；可改 User Task 名称/审批人引用等已有字段；保存草稿；无假数据。
- **不做**：网关/并行等高级节点；Publish。
- **验收**：可保存并重新打开草稿；UI 门禁；纯前端。

### M8 — P3 流程定义校验 API

- **类型**：后端　**分支**：`feat/m8-process-definition-validate`　**前置**：M5
- **目标**：`POST .../definitions/{key}/validate`（或草稿 id）：结构校验（起止、孤边、User Task 必填）；返回错误列表；不部署。
- **验收**：合法/非法夹具测试；租户隔离。

### M9 — P3 流程设计器校验 UX

- **类型**：前端　**分支**：`ui/m9-process-designer-validate`　**前置**：M8、M7
- **目标**：Validate 按钮 + 结果展示；阻塞无结果时的发布入口（若发布按钮已存在则 Disabled）。
- **验收**：UI 门禁；纯前端。

### M10 — P3 流程定义发布 API

- **类型**：后端　**分支**：`feat/m10-process-definition-publish`　**前置**：M8
- **目标**：校验通过后发布：草稿→已发布；部署到引擎或平台认可的定义库；写审计；租户隔离。
- **不做**：迁移运行中实例；会签等高级模型。
- **验收**：发布后定义可读/可开实例（最小冒烟）；跨租户否定；失败不半发布。

### M11 — P3 流程设计器发布 UX

- **类型**：前端　**分支**：`ui/m11-process-designer-publish`　**前置**：M10、M9
- **目标**：Publish 确认 + 成功/失败态；深链保持。
- **验收**：UI 门禁；无假成功率。

---

### P1 租户改挂

### M12 — P1 租户改挂写 API

- **状态**：已合并（PR #342），负责人 Cursor
- **类型**：后端　**分支**：`feat/m12-tenant-reparent-api`　**前置**：无（N1–N3 已合）
- **目标**：`PATCH/PUT` parent（含置空）；**环检测**（不能挂到自己的后代）；平台职责；写审计；跨租户否定。
- **不做**：拖拽排序大件；跨租户业务数据下钻。
- **验收**：合法改挂成功；成环 4xx；跨租户否定；测试绿。

### M13 — P1 租户改挂 UI

- **状态**：已合并（PR #345），负责人 Cursor
- **类型**：前端　**分支**：`ui/m13-tenant-reparent`　**前置**：M12
- **目标**：关系页选择新父级 + 确认；失败展示服务端错误（含成环）。
- **验收**：UI 门禁；空「暂无」。

---

### P2 IdP 立即同步

### M14 — P2 IdP 立即同步 API

- **状态**：已合并（PR #343），负责人 Cursor
- **类型**：后端　**分支**：`feat/m14-idp-sync-now`　**前置**：无（N8–N11 已合）
- **目标**：`POST .../providers/{id}/sync`：仅 OIDC/Keycloak 类已宣称类型；触发既有对账/同步路径；更新 `last_sync_*` / fail_count；租户隔离。
- **不做**：SAML/LDAP；cron/定时调度产品化。
- **验收**：成功/失败元数据落库；跨租户否定；无用户密码明文。

### M15 — P2 IdP 立即同步按钮

- **状态**：已合并（PR #346），负责人 Cursor
- **类型**：前端　**分支**：`ui/m15-idp-sync-now`　**前置**：M14
- **目标**：身份集成卡「立即同步」+ 最近同步/失败数展示（§26.9）。
- **验收**：UI 门禁；纯前端。

---

### P5 Credential

### M16 — P5 Credential 元数据表迁移

- **类型**：迁移　**分支**：`mig/m16-p5-credential-metadata`　**前置**：无
- **目标**：Credential 元数据表（name/provider/status/last_rotated/expires/secret_name 引用）；**无**明文 secret 列。
- **验收**：migrate 绿；契约禁 secret 明文列。

### M17 — P5 Credential 密钥写入/轮转/吊销（安全）

- **状态**：已合并（PR #350），负责人 Cursor
- **类型**：安全　**分支**：`sec/m17-p5-credential-secrets`　**前置**：M16（建议 M2 已合）
- **目标**：经 SecretPort put/rotate/delete；API 不回显明文；写审计。
- **不做**：开发者门户；在目录 API 返回密钥。
- **验收**：轮转后旧值失效；GET 元数据无明文；测试绿。

### M18 — P5 Credential 元数据 CRUD API

- **状态**：已合并（PR #353），负责人 Cursor
- **类型**：后端　**分支**：`feat/m18-p5-credential-metadata-api`　**前置**：M16
- **目标**：list/get/create/update（元数据）；与 M17 密钥操作分离或明确子路径；租户隔离。
- **验收**：CRUD + 跨租户否定；响应无明文。

### M19 — P5 Credential 管理页

- **状态**：已合并（PR #355），负责人 Cursor
- **类型**：前端　**分支**：`ui/m19-p5-credentials`　**前置**：M17、M18
- **目标**：§29.7 列表/详情；Rotate/Revoke/Test；侧栏 `credentials` 去 later；密钥只写不回显。
- **验收**：UI 门禁；文案自用。

---

### P4 商业权益（只读）

### M20 — P4 商业权益只读投影 API

- **类型**：后端　**分支**：`feat/m20-p4-commercial-entitlement-read`　**前置**：无
- **目标**：基于已有订阅/SKU/安装读模型投影「商业权益」视图（租户隔离）；无则诚实空。
- **不做**：计费、优惠券、发票；P1 运行时投影编辑。
- **验收**：list/detail + 跨租户否定；无假金额。

### M21 — P4 商业权益页

- **类型**：前端　**分支**：`ui/m21-p4-commercial-entitlement`　**前置**：M20
- **目标**：侧栏 entitlements 去 later；只读展示。
- **验收**：UI 门禁；空「暂无」。

---

### 平台审计哈希链

### M22 — 平台审计哈希链校验 API

- **类型**：后端　**分支**：`feat/m22-audit-chain-verify`　**前置**：无
- **目标**：平台职责 `POST/GET .../audit/chain/verify`（或等价）调用 `AuditPort.verifyChain()`；返回 intact/checked/failure；不暴露敏感 payload。
- **不做**：改链；热清理任务。
- **验收**：完整链 OK、破坏夹具 FAIL；非平台职责拒绝。

### M23 — 平台审计中心「校验哈希链」

- **类型**：前端　**分支**：`ui/m23-audit-chain-verify`　**前置**：M22
- **目标**：N7 审计中心增加校验动作与结果展示；与 `/p3/audit` 职责不混。
- **验收**：UI 门禁；纯前端。

---

## 四、依赖关系图

```text
优先组 0（N10 跟进）:
  M1(mig) → M2(SEC)
  M3(test)          # 建议 M2 后
  M4(SEC)           # 独立，可与 M3 并行

M5(mig) → M6(BE) → M7(FE)
       ↘ M8(BE) → M9(FE) → M11(FE)
                ↘ M10(BE) ↗

M12(BE) → M13(FE)                 # 租户改挂
M14(BE) → M15(FE)                 # IdP 立即同步
M16(mig) → M17(SEC) ─┬→ M19(FE)   # Credential
           M18(BE)  ─┘
M20(BE) → M21(FE)                 # P4 商业权益只读
M22(BE) → M23(FE)                 # 审计哈希链校验

Flyway 串行合入：M1 → M5 → M16（开发可并行，合入排队）
```

---

## 五、建议第一批（当前 2 人）

| 批次 | 人 A | 人 B | 理由 |
|---|---|---|---|
| **Batch 0** | **M1 → M2** | **M4** | 先补 N10 安全洞；M4 无迁移依赖 |
| **Batch 0b** | **M3**（M2 合入后） | **M12** 或 **M14** | 否定测试跟上；另一人开无迁移业务后端 |
| **Batch 1** | M5→M6 | M16（等 M1 合入后）或 M20/M22 | 迁移排队后开流程草稿 / Credential / 只读投影 |
| **Batch 2** | M7/M8/M9… | M13/M15/M17… | FE/安全跟进 |

两人时 **不要**并行合入两个 Flyway PR。

---

## 六、本波不做 / 后置登记

| 项 | 处理 |
|---|---|
| **H5**（`/m/*`、`web/src/mobile/**`） | **整波不做**（Eric 明确）；仅登记 |
| S2 治理 | 冻结表后置 |
| 备份 / 恢复 / RecoveryPort 真恢复 | 冻结表整包后置 |
| 开发者门户 / 外部 OAuth / 第三方开放平台 | 冻结表整包后置 |
| IdP：SAML/LDAP 产品化 | 诚实文档未宣称；后置 |
| IdP：定时同步调度 | 后置（本波仅「立即同步」） |
| 发布中心：真 Expand/Migrate/Switch/Contract | 后置（只读中心已有） |
| P4 运营分析（侧栏 later） | 后置（无聚合 API，避免假 KPI） |
| 流程：网关/会签/或签等高级节点 | 后置 |
| OpenBao/云 KMS 作为 Secret 主后端 | 后置（本波只做持久化本地密文） |

---

## 七、修订记录

- 2026-09-24：NexusOne 草稿 V0.1。对照 `main@6056705`；纳入 N10 审阅跟进为优先组 0；N 波后续业务薄切；明确排除 H5。
- 2026-09-24：M4 已合并（PR #340）。测试连接在连上后再核对对端地址，内网地址会被拒绝。
- 2026-09-24：M12 已合并（PR #342）。平台管理员可改挂或清空上级；成环 409；租户上下文不能改挂。
- 2026-09-24：M14 已合并（PR #343）。OIDC/Keycloak 可立即同步，失败只记元数据，不含口令。
- 2026-09-24：M13 已合并（PR #345）。关系页确认后改挂，成环等服务端错误原文展示。
- 2026-09-24：M15 已合并（PR #346）。身份集成卡可立即同步，展示最近同步与失败数，不编造人数。
- 2026-09-25：M17 已合并（PR #350）。凭证密钥可写入、轮转和吊销，响应与审计不含明文。
- 2026-09-25：M18 已合并（PR #353）。凭证元数据可按租户增改查，响应只有引用、没有明文。
- 2026-09-25：M19 已合并（PR #355）。凭证页可轮转、吊销和测试，密钥不回显。