# WEB 上线演示任务卡（DL 波）V0.1

- **基线**：`origin/main` @ `f7fbda56`（DPO-L5 已合，#674）；**Flyway 当前最高版本 = V708**（`nexus-p4-market/.../V707__market_print_mapping.sql`、`V708__market_print_template.sql`）；`outOfOrder` 保持关闭；**下一空号 = V709**（仅 schema 专卡可占用）
- **依据**：
  - 上线演示主轴：[`M7-Keycloak演示验收清单.md`](M7-Keycloak演示验收清单.md)、[`demo-host-prod-deployment.md`](../engineering/runbooks/demo-host-prod-deployment.md)、[`demo-host-prod.env.template`](../engineering/runbooks/demo-host-prod.env.template)、[`scripts/demo/reset.ps1`](../../scripts/demo/reset.ps1)
  - 本地 stub 主路径已绿：[`I10-上线门禁复验小结.md`](I10-上线门禁复验小结.md)、[`N8-演示主路径冒烟记账.md`](N8-演示主路径冒烟记账.md)、[`J3-平台配置冒烟记账.md`](J3-平台配置冒烟记账.md)
  - DPO 预备（Axis A）：[`WEB演示部署打印星市加深任务卡_DPO波_V0.1.md`](WEB演示部署打印星市加深任务卡_DPO波_V0.1.md) — **预备 ≠ 真机验收**；M7 §1 真机勾选仍未完成
  - XB 规划 [#566](https://github.com/EricBeckham/NexusOne/pull/566) **已 MERGED**；升级步骤仍保持 **UNKNOWN**（禁止假绿 DONE）
- **执行方**：**Cursor ≥5**（FE）+ **Codex ≥5**（docs/scripts）；**Claude 本波不派 DL 实现卡**（先收口 DPO-L5/L6）；**NexusOne 只审查（review）**
- **工作树 / 分支前缀（实现时）**：Cursor → `cursor/`；Claude → `claude/`；Codex → `codex/`；本规划 docs PR → `nexusone/`
- **性质**：**docs-only**。本文件不改产品代码。

## 1. 目标（上线演示）

| 焦点 | 目标 | 不做 / 非首演阻塞 |
|---|---|---|
| **上线演示（Demo Launch）** | 在 DPO Axis A **预备**之上，把「真机演示可跑」所需的 **FE 诚实壳、runbook、prod-safe 内容手册、remoke 纪律、可跳过 smoke、Eric Go/No-Go 一页纸** 做成可合入薄卡；外部阻塞 E1–E7 显式列表，由 Eric/运维关闭 | **不**声称 Keycloak prod 已验收；**不**在 prod 推荐 `NEXUS_DEMO_ENABLED=true`；**不**假绿升级 DONE；DPO-L5/L6（打印加深）= **nice-to-have**，非首演阻塞 |
| **本地 stub** | I10 / N8 / J3 双主路径已绿，保持可回归；浏览器 smoke 无浏览器时 skip exit 0 | **不**用 stub 绿等价真机验收 |
| **prod / 演示机** | demo flags **OFF** 时无自动 seed → 需 **prod-safe 内容手册**（手工步骤或受控 bootstrap **变量名**） | **禁止**把 NEXUS_DEMO_* 真值 / secret 写入仓或日志 |

## 2. 边界与硬禁

### 2.1 硬禁（全波）

- **禁止 H5** / `web/src/mobile/**`
- **禁止假绿升级**：stepper / 升级步骤无真实运维记录时保持 **UNKNOWN**，不得标 DONE
- **禁止发明支付** / 计费 / 价格字段 / 假支付链路
- **Flyway**：当前最高 **V708**；下一空号 **V709**；仅 **schema-only 专卡**可占号；`outOfOrder` **关闭**；本波 Cursor/Codex 卡 **不写迁移**
- **禁止**在 FE / 日志 / PR / compose / 脚本输出中打印真实 secret
- **禁止**声称「演示机 prod Keycloak 已验收」——DPO Axis A 与本波均为预备/文档/壳；真机勾选见 M7 §1.5
- **禁止**在 prod 路径推荐开启 `NEXUS_DEMO_ENABLED=true`（或等价自动 seed）
- **FE ≠ BE**：FE 卡不夹带 BE 大改；BE 不在本波 DL 实现队列
- **NexusOne**：**只审查**，不领实现卡
- **`scripts/demo/reset.ps1`**：**loopback-only**；演示机 remoke 另有纪律（见 DL-X3）

### 2.2 外部阻塞 E1–E7（不可由 agent 消除）

| ID | 阻塞 | 说明 |
|---|---|---|
| **E1** | 真机主机 / SSH / 云账号 | 需 Eric / 运维；agent 无主机权 |
| **E2** | DNS + TLS（公网证书） | 需域名与证书私钥；禁止把私钥写入仓库 |
| **E3** | 密钥仓（secrets vault） | 真实 `CLIENT_SECRET` / `MASTER_KEY` / DB 密码等只存 vault；会话与 PR 只写**变量名** |
| **E4** | Keycloak realm / client / redirect | IdP 管理台配置；与 M7 §1.1–1.2 对齐 |
| **E5** | 受控演示账号 | 只记显示名 / subject；**不写口令** |
| **E6** | 反向代理（SPA + `/callback`） | 网关路由、TLS 终结、回调路径；见 DL-X6 可选笔记 |
| **E7** | JAR + SHA256 + M7 §1.5 | 制品校验与真机登录验收步骤；仅环境所有者勾选 |

> 关闭 E1–E7 之前，任何「真机演示已通过」表述均属违规。本地 stub 绿 ≠ E* 关闭。

### 2.3 Flyway 记账

| 项 | 值 |
|---|---|
| 扫库日 | 2026-09-27（Asia/Shanghai） |
| **当前最高版本** | **V708** |
| 已占文件 | `V707__market_print_mapping.sql`、`V708__market_print_template.sql`（`nexus-p4-market`） |
| 本波下一迁移 | **V709** — **仅** schema 专卡（本波默认不派；若 Claude 后期 DL-L1/L2 需要则另开 schema 卡） |
| outOfOrder | **off** |

## 3. 与既有波次关系

| 既有 | 本波态度 |
|---|---|
| DPO Axis A（演示预备） | **承接**：compose/守卫/登录壳/M7 链接等为预备；本波转向 **上线演示** 文档与 FE 诚实壳；**不**勾 M7 §1 |
| DPO-L5（#674 已合）/ DPO-L6 | **nice-to-have**；Claude **先收口**，非首演阻塞；本波 **不**派 DL 实现卡给 Claude |
| M7 Keycloak 清单 | **双向链接**加深；预备 ≠ 验收；§1 真机仍未勾 |
| I10 / N8 / J3 | 本地 stub 主路径 **已绿**；DL 浏览器 smoke / 点击路径须标注 stub vs Keycloak |
| XB / #566 | **已 MERGED**；非演示阻塞；升级步骤仍 **UNKNOWN**（无假绿） |
| `reset.ps1` | loopback-only；演示机 remoke → DL-X3 纪律 |

---

## 4. 薄卡总表

合计：**Cursor ≥5（C1–C5 必派，C6 可选）+ Codex ≥5（X1–X5 必派，X6 可选）**。Claude **本波无 DL 实现卡**。每张 = 一个薄切片 / 一个 PR / 约 1–2h；**FE ≠ BE**；无 Flyway 夹带。

### 4.1 Cursor（FE-heavy）— DL-C1…C6

---

#### DL-C1

| 字段 | 内容 |
|---|---|
| **id** | DL-C1 |
| **title** | 登录壳：issuer 已配置 → 主 CTA 走 Keycloak；Stub 入口降级/隐藏 |
| **owner** | Cursor |
| **axis** | 上线演示 / FE |
| **branch（实现时）** | `cursor/dl-c1-keycloak-primary-cta` |
| **in scope** | 当公开 issuer（如 `VITE_NEXUS_KEYCLOAK_ISSUER` 或等价）已配置：登录页 **主 CTA = Keycloak**；Stub 登录降为次要/折叠/隐藏（文案标明「仅本地 stub」）；issuer 未配置时保持 stub 可用并诚实说明 IdP 未配置；**零 secret 进 FE bundle** |
| **out of scope** | 真机验收；写入 client secret；改 BE；compose；宣称 Keycloak prod 已验收 |
| **acceptance** | ① issuer on → Primary CTA = Keycloak，Stub 非主按钮；② issuer off → stub 可用 + 诚实文案；③ vitest 覆盖两态；④ build 产物 grep 无 `CLIENT_SECRET` / 非 REPLACE_ 疑似密钥 |
| **likely paths** | `web/src/views/**/Login*`、相关 router/api、`*.test.js`、`web/.env*`（仅公开变量说明） |
| **deps** | 可接 DPO-C1 已合行为加深；无硬依赖 |
| **test plan** | vitest：issuer on/off；手动 stub 登录仍通（off 态） |

---

#### DL-C2

| 字段 | 内容 |
|---|---|
| **id** | DL-C2 |
| **title** | 只读点击路径指南壳（login→catalog→activate→submit→approve→result）；标注 stub vs Keycloak |
| **owner** | Cursor |
| **axis** | 上线演示 / FE |
| **branch** | `cursor/dl-c2-clickpath-guide-shell` |
| **in scope** | 只读「演示点击路径」壳/面板：固定顺序 login → catalog → activate → submit → approve → result；每步标明当前会话是 **stub** 还是 **Keycloak**；链到 runbook/M7 文档入口（公开 URL 或站内路由）；不执行写操作 |
| **out of scope** | 自动驱动浏览器验收；改 BE；假绿勾选 M7 §1 |
| **acceptance** | ① 六步顺序固定可见；② stub/Keycloak 标签随配置诚实切换；③ 只读无写 API；④ vitest 快照/结构测 |
| **likely paths** | `web/src/views/**/Demo*` 或帮助/运营只读页、router、vitest |
| **deps** | 建议 C1 后（登录态标签更准）；可与 X1 文案对齐 |
| **test plan** | vitest：步序 DOM；issuer on/off 标签 |

---

#### DL-C3

| 字段 | 内容 |
|---|---|
| **id** | DL-C3 |
| **title** | 本地 stub 路径浏览器 smoke harness；无浏览器 → skip exit 0；无 secrets |
| **owner** | Cursor |
| **axis** | 上线演示 / FE 测试 |
| **branch** | `cursor/dl-c3-stub-browser-smoke` |
| **in scope** | 针对 **本地 stub** 主路径的浏览器 smoke（Playwright/等价已有栈优先）；检测无浏览器二进制 / 显式 skip 变量 → **exit 0 skipped**；日志只含 host/状态，**禁止** token/口令 |
| **out of scope** | 强制 CI 连真机 Keycloak；存凭据；prod 自动 seed |
| **acceptance** | ① 无浏览器 → skipped 0；② 有浏览器+本地 stub 可达 → 最小路径绿或诚实失败（无 secret）；③ README/注释写明启用方式；④ 与 DL-X4 变量命名可并存但不冲突 |
| **likely paths** | `web/e2e/**` 或 `scripts/**`、`package.json` scripts、文档小节 |
| **deps** | 无；与 X4 分工：C3=浏览器/FE 侧，X4=post-login/URL 可跳过脚本 |
| **test plan** | 本地无浏览器跑 → 0；有浏览器跑 stub 最小路径 |

---

#### DL-C4

| 字段 | 内容 |
|---|---|
| **id** | DL-C4 |
| **title** | 申请开通 / 激活申请 empty + error FE 态（S-shell） |
| **owner** | Cursor |
| **axis** | 上线演示 / FE |
| **branch** | `cursor/dl-c4-activation-empty-error` |
| **in scope** | 申请开通 / Activation applications 列表与详情：空列表、加载失败、403/网络错 的 **诚实 S-shell** 空错态；文案可引导「见 prod-safe 内容手册」（不暗示已自动 seed） |
| **out of scope** | 新 BE API；自动 seed；支付；H5 |
| **acceptance** | ① 空列表有明确空态；② 错误态不吞异常、不露 secret；③ vitest mock 空/错；④ 语气对齐既有 S 壳 |
| **likely paths** | `web/src/views/**/*Activation*`、`*Application*`、相关 api client、tests |
| **deps** | 无 |
| **test plan** | vitest：empty / 5xx / 网络错 |

---

#### DL-C5

| 字段 | 内容 |
|---|---|
| **id** | DL-C5 |
| **title** | 缺失 TenantContext / 回调失败诚实壳 |
| **owner** | Cursor |
| **axis** | 上线演示 / FE |
| **branch** | `cursor/dl-c5-tenantcontext-callback-shell` |
| **in scope** | 当 TenantContext 缺失或 OIDC/回调失败：展示诚实错误壳（下一步建议：检查 redirect、issuer、反向代理 `/callback`）；**不**假装已登录；**不**把 error 里的 token 片段渲染到 UI |
| **out of scope** | 修 IdP 配置（外部 E4/E6）；改 BE token 校验；假登录绕过 |
| **acceptance** | ① 缺 TenantContext → 明确壳 + 可重试/回登录；② 回调失败 → 诚实原因类别（无 secret）；③ vitest；④ 与 C1 登录态一致 |
| **likely paths** | `web/src/views/**/Callback*`、auth/tenant context 相关、router guard、tests |
| **deps** | 建议 C1 后 |
| **test plan** | vitest：缺 context / 回调 error mock |

---

#### DL-C6（可选）

| 字段 | 内容 |
|---|---|
| **id** | DL-C6 |
| **title** | （可选）DemoDeployStatus 文案对齐 M7 presence（仍只读） |
| **owner** | Cursor |
| **axis** | 上线演示 / FE |
| **branch** | `cursor/dl-c6-demodeploy-m7-wording` |
| **in scope** | 若 DPO-C2 状态页已合：文案与 M7 条目 presence 对齐（「待外部 E* / 可本地」）；仍 **只读**；**永不渲染 secret 值** |
| **out of scope** | 勾选 M7 §1 已验收；写配置；管理 API |
| **acceptance** | ① 文案含预备≠验收；② 无 secret；③ vitest 无回归 |
| **likely paths** | `web/src/views/**/DemoDeploy*`、tests |
| **deps** | DPO-C2 或等价页存在；否则跳过本卡 |
| **test plan** | vitest 文案/presence |

---

### 4.2 Claude — 本波不派 DL 实现卡

**本波 Claude 队列：先完成 DPO 收口，不接 DL 实现。**

| 优先级 | 说明 |
|---|---|
| **先** | 收口 **DPO-L5**（#674 已合则核对账）与 **DPO-L6**（升级步骤诚实持久化；无运维则保持 UNKNOWN） |
| **后（可选，另波）** | **DL-L1**：prod-safe bootstrap API（仅受控、变量名驱动；**禁止**默认打开 `NEXUS_DEMO_ENABLED` on prod） |
| **后（可选，另波）** | **DL-L2**：演示机 remoke admin（若需 schema → **V709 专卡**，与行为分 PR） |

> 粘贴给 Claude 的开工指令：**不要**包含本文件 DL-C* / DL-X* 实现卡；仅 DPO-L5/L6 收口。

---

### 4.3 Codex（docs / scripts）— DL-X1…X6

---

#### DL-X1

| 字段 | 内容 |
|---|---|
| **id** | DL-X1 |
| **title** | 真机演示 runbook（与 stub 分离）；串联 M7；明确 预备 ≠ 验收 |
| **owner** | Codex |
| **axis** | 上线演示 / docs |
| **branch** | `codex/dl-x1-realhost-demo-runbook` |
| **in scope** | 新建或拆分 **真机演示 runbook**（与本地 stub 路径分章/分文件）；步骤链到 M7 §1 / §1.5 / E1–E7；文首醒目：**预备 ≠ 验收**；引用既有 `demo-host-prod-deployment.md` 不重复粘贴 secret |
| **out of scope** | 勾选真机已验收；写入真实 env 值；改产品代码 |
| **acceptance** | ① stub 与真机章节分离；② 双向链接 M7；③ 无 REPLACE 实填值；④ 审阅通过 |
| **likely paths** | `docs/engineering/runbooks/**`、`docs/planning/M7-*.md` |
| **deps** | 无 |
| **test plan** | docs-only 审阅 |

---

#### DL-X2

| 字段 | 内容 |
|---|---|
| **id** | DL-X2 |
| **title** | Prod-safe 演示内容手册（手工步骤或受控 bootstrap **变量名**）；禁止 prod 开 `NEXUS_DEMO_ENABLED=true` |
| **owner** | Codex |
| **axis** | 上线演示 / docs |
| **branch** | `codex/dl-x2-prod-safe-content-handbook` |
| **in scope** | 手册：prod/演示机在 **demo flags OFF** 时如何准备最小可演示内容（租户/目录/申请等）——优先 **手工步骤**；若提及受控 bootstrap，只列 **环境变量名** 与开关语义；醒目禁止：`NEXUS_DEMO_ENABLED=true`（及等价）作为 prod 推荐路径 |
| **out of scope** | 实现 bootstrap API（→ 后期 DL-L1）；真实数据/secret 入库 |
| **acceptance** | ① 明确 prod 不推荐 demo auto-seed；② 步骤可执行且无 secret 实值；③ 链到 X1 runbook 与 C4 空态文案 |
| **likely paths** | `docs/engineering/runbooks/**` 或 `docs/planning/*演示内容*` |
| **deps** | 可与 X1 同批 |
| **test plan** | docs 审阅；grep 禁止推荐 prod `NEXUS_DEMO_ENABLED=true` |

---

#### DL-X3

| 字段 | 内容 |
|---|---|
| **id** | DL-X3 |
| **title** | 演示机 remoke 纪律：无 `NEXUS_DEMO_HOST_ALLOW=1` + 确认则拒绝；链 `reset.ps1` 边界 |
| **owner** | Codex |
| **axis** | 上线演示 / docs+scripts |
| **branch** | `codex/dl-x3-demo-host-remoke-discipline` |
| **in scope** | 文档（+可选薄包装脚本）：演示机 remoke/重置须同时满足 `NEXUS_DEMO_HOST_ALLOW=1` **与**交互确认；否则拒绝；明确 [`scripts/demo/reset.ps1`](../../scripts/demo/reset.ps1) = **loopback-only**，不可当作真机 remoke |
| **out of scope** | 扩大 reset.ps1 到非 loopback；远程删库；写入 secret |
| **acceptance** | ① 缺 allow 或确认 → 拒绝且 exit 非 0（若有脚本）；② 文档写清 loopback 边界；③ 无 secret 日志 |
| **likely paths** | `scripts/demo/**`、`docs/engineering/runbooks/**` |
| **deps** | 无 |
| **test plan** | 无 allow 跑包装脚本 → 拒绝；docs 审阅 |

---

#### DL-X4

| 字段 | 内容 |
|---|---|
| **id** | DL-X4 |
| **title** | 可跳过 post-login / 浏览器 smoke（URL 未设 → skip 0）；日志无 token |
| **owner** | Codex |
| **axis** | 上线演示 / scripts |
| **branch** | `codex/dl-x4-skippable-postlogin-smoke` |
| **in scope** | 脚本：目标 URL / smoke URL 环境变量未设 → 打印 skipped 并以 **0** 退出；已设则最小 post-login 或 HTTPS 探活；日志禁止打印 Authorization / refresh / cookie 值 |
| **out of scope** | 强制 CI 连真机；存凭据；与 C3 抢同一浏览器大测（可互链） |
| **acceptance** | ① 默认无 URL → 0 skipped；② 失败只报状态码与 host；③ 文档写启用变量名 |
| **likely paths** | `scripts/**`、`.github/workflows/**`（若有）、短 README |
| **deps** | 可与 DPO-X3 / C3 并存；变量名文档统一 |
| **test plan** | 无 env → 0；假 URL 行为符合文档 |

---

#### DL-X5

| 字段 | 内容 |
|---|---|
| **id** | DL-X5 |
| **title** | Eric 离线 Go/No-Go 一页纸（E1–E7 + T-30）；签名栏空白；与 M7 双向链接 |
| **owner** | Codex |
| **axis** | 上线演示 / docs |
| **branch** | `codex/dl-x5-go-nogo-onepager` |
| **in scope** | 一页纸：E1–E7 勾选表 + **T-30**（演示前 30 分钟检查）清单；Go / No-Go 结论栏；**签名栏留空**（Eric 手签）；双向链接 M7 与 X1 runbook；不预勾任何 E* |
| **out of scope** | 代 Eric 签名；宣称已 Go；改产品代码 |
| **acceptance** | ① E1–E7 与本文 §2.2 一致；② T-30 可执行；③ 签名空白；④ M7 双向链 |
| **likely paths** | `docs/planning/*Go*No*Go*` 或 `docs/engineering/runbooks/**` |
| **deps** | X1 建议先或同批 |
| **test plan** | docs 审阅 |

---

#### DL-X6（可选）

| 字段 | 内容 |
|---|---|
| **id** | DL-X6 |
| **title** | （可选）VERIFY-T T3b–T4 + SPA 反向代理 `/callback` 笔记 |
| **owner** | Codex |
| **axis** | 上线演示 / docs |
| **branch** | `codex/dl-x6-verify-t-callback-notes` |
| **in scope** | 对照 [`VERIFY-T_空库租户主路径复验清单_V0.1.md`](VERIFY-T_空库租户主路径复验清单_V0.1.md)（或现行 VERIFY-T）补 T3b–T4 与 SPA 反代 `/callback` 注意事项；服务 E6；不勾真机已验 |
| **out of scope** | 改网关生产配置；实现代码 |
| **acceptance** | ① 笔记可执行；② 链 E6/M7；③ 无 secret |
| **likely paths** | `docs/planning/VERIFY-T*`、runbook 附录 |
| **deps** | 可选；不阻塞首演文档主链 |
| **test plan** | docs 审阅 |

---

## 5. NexusOne 审查清单（**仅 review，无实现卡**）

NexusOne 在每张实现 PR 上按下列项勾选，**不编写功能代码**：

| 审查项 | 要点 |
|---|---|
| **范围** | 单卡单 PR；未宣称 Keycloak prod 已验收；未把 stub 绿写成真机通过 |
| **Flyway** | 本波卡无迁移；若出现 V709 必须是 schema-only 专卡且 outOfOrder off |
| **诚实性** | 升级步骤无假 DONE；空错态诚实；预备≠验收文案在 |
| **Secrets** | 日志/FE/PR/compose/手册无真实 secret；仅变量名 |
| **Prod demo 开关** | 未推荐 prod `NEXUS_DEMO_ENABLED=true` |
| **Remoke** | reset.ps1 仍 loopback-only；真机 remoke 有 allow+确认 |
| **FE/BE 拆分** | FE 卡无 BE 大改；Claude 无本波 DL 实现卡误派 |
| **硬禁** | 无 H5、无假支付、无假绿 |
| **测试** | skip-0 / vitest / 文档验收项真实可执行 |
| **外部阻塞** | PR 未假装关闭 E1–E7 |

---

## 6. 建议并行队列

| 批 | Cursor | Codex | Claude |
|---|---|---|---|
| 1 | **C1**, **C4** | **X1**, **X2** | 收口 DPO-L5/L6（非 DL） |
| 2 | **C2**, **C5** | **X3**, **X5** | ↑ |
| 3 | **C3** | **X4** | ↑ |
| 4（可选） | C6 | X6 | 后期可选 DL-L1/L2（另波） |

**首演最小集**：C1–C5 + X1–X5。C6/X6/DL-L* 不挡首演文档与壳。

---

## 7. 工作树纪律

| 角色 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne（本规划 / review） | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

- **禁止**碰 `D:\Projects\NexusOne` 主克隆（除必要时 fetch-only）
- **禁止** junctions / 其它 agent 树 / Desktop/Downloads/temp
- 实现卡禁止改本规划文件范围外的无关波次
- Docs PR 优先 **squash-merge**

---

## 8. 修订记录

- 2026-09-27：V0.1 初稿（docs-only）。焦点 **上线演示**；Flyway max **V708**（下一空号 V709 仅 schema 专卡）；Cursor C1–C5（+C6 可选）+ Codex X1–X5（+X6 可选）；Claude 不派 DL 实现、先收口 DPO-L5/L6；#566 已 MERGED；E1–E7 外部阻塞；NexusOne 仅审查。
