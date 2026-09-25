# WEB 上线断点任务卡（I 波）V0.1

- 基线：`main@3ad751cbc47c`（H12 #436 已合；H 波 H1–H12 齐）
- 依据：`docs/planning/H10-手工冒烟记账.md`、`docs/planning/H11-平台配置冒烟记账.md`
- 目标环境：**演示 / 内测可走通主路径**（非公网商用首发）
- 执行方：本机 Cursor / Codex / Claude（前缀与 worktree 同第五波 §六 / P / G / H 波）；NexusOne 协调

## 默认决策

1. **一卡一 PR**；PR 标题以 `I*` 开头；做完直接 squash 合并。
2. **本机路径**：主克隆 `D:\Projects\NexusOne` **只允许 fetch**；每人独立 worktree + 前缀分支（`cursor/` `codex/` `claude/`；NexusOne 用 `nexusone/`）。不要目录 junction / 链接。
3. **明确不做**：自由画布；分支/并行流转；H5；支付；高级 BPM；整站视觉重做；CI 复活；无关新功能波。
4. **验收口径**：未合 **I1** 前勿宣称「发起」已通；未合 **I2** 前勿宣称平台新建模板已通；**I8** 绿之前勿宣称租户主路径 E2E 已通；**I9** 绿之前勿宣称平台配置主路径已通。

## 当前硬阻塞（H10 / H11）

H 波修好了 SKU 绑定与目录字段后：

1. **租户发起**：订阅开通已过；发起提交失败。`JdbcProductApplicationStore.insertRunning` 把 `java.time.Instant` 直接丢给 JDBC，Postgres：`无法推测实例 java.time.Instant 的 SQL 类型`。审批 / 我的申请未执行。
2. **平台配模板**：种子模板可读；新建草稿与校验在 `FormTemplateDraftService` 同样死于 `Instant` JDBC 类型；发布与新产品绑定未完成。产品定义页「已发布模板版本」下拉为空需修完持久化后再复核。

根因同类：**Postgres JDBC 写 `timestamptz` 时未把 `Instant` 绑成明确 SQL 类型**（应用发起 + 表单模板草稿两条路径）。

## 任务总览

| 卡 | 内容 | 类型 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|---|
| **I1** | 修发起持久化 `Instant` JDBC（`JdbcProductApplicationStore.insertRunning`），空库 demo 发起提交成功；加回归 | 后端 | Cursor | 无 | 1–2h |
| **I2** | 修模板草稿新建/校验的 `Instant` JDBC（`FormTemplateDraftService`），空库可保存草稿并校验；加回归 | 后端 | Cursor | 可与 I1 并行 | 1–2h |
| **I3** | 收敛公共 Instant→JDBC 绑定（helper/转换），审计 p3/p4 同类写库点并修掉遗漏 | 后端 | Cursor | 建议 I1/I2 后 | 1–2h |
| **I4** | 否定/回归：发起与模板草稿路径再踩 `Instant` SQL 类型时测试失败；覆盖 H10/H11 复现点 | 后端·测试 | Cursor | I1、I2（建议 I3 后） | 1h |
| **I5** | 发起提交失败时前端展示真实错误（勿只 Internal error），可重试 | 前端 | Claude | 建议 I1 后；可先骨架 | 1h |
| **I6** | 平台模板保存草稿/校验/发布失败时展示后端明确错误 | 前端 | Claude | 建议 I2 后；可先骨架 | 1h |
| **I7** | 产品定义「已发布模板版本」下拉：I2 后仍空则修数据/API/字段对齐；空态可读 | 前端 | Claude | **I2** | 1–2h |
| **I8** | I1（+必要 I5）合入后重跑：订阅→发起→审批（含意见）→我的申请，写记账 | 运维·验收 | Codex | **I1** | 1–2h |
| **I9** | I2（+必要 I6/I7）合入后重跑：建模板→校验→发布→绑产品，写记账 | 运维·验收 | Codex | **I2** | 1–2h |
| **I10** | 双主路径复验小结 + 演示机等价启动再记一笔（断点只记账，不宣称外网已部署） | 运维·验收 | Codex | **I8、I9** | 1h |

## 卡片正文

### I1 — 发起持久化 Instant JDBC（阻塞租户主路径）

**现象**（H10）：发起提交 `Internal error`；`JdbcProductApplicationStore.insertRunning` INSERT `business_meta.product_application` 时 `PSQLException: 无法推测实例 java.time.Instant 的 SQL 类型`（`createdAt()` 直接入参）。

**要求**：

- 修发起写库路径的时间参数绑定（`setObject` 明确类型、`Timestamp.from`、或项目既有惯例），使空库 demo：订阅后发起提交成功并落库。
- 加回归测试锁住该复现点。
- 一卡一 PR；不扩审批 UI；不顺手大重构（公共 helper 放到 I3）。

### I2 — 模板草稿 Instant JDBC（阻塞平台配置）

**现象**（H11）：新建草稿 / 校验均 `Internal error`；`FormTemplateDraftService.create` / `validateDraft` 写 `platform.form_template`（含 `updated_at`）时同样 Instant SQL 类型失败。

**要求**：

- 修模板草稿新建与校验写库的时间绑定；空库可保存草稿并完成校验（为发布铺路）。
- 加回归测试。
- 一卡一 PR；发布/绑定浏览器验收归 I9；下拉空态归 I7。

### I3 — Instant JDBC 公共收敛 + 审计

**要求**：

- 抽出或复用统一的 Instant→JDBC 绑定方式，避免第三条路径再炸。
- 审计 `nexus-p3-studio` / 相关 market 持久化中同类 `Instant` 写库并修遗漏。
- 一卡一 PR；不改业务语义。

### I4 — 回归锁 H10/H11 复现点

**要求**：

- 自动化测试：发起写库、模板草稿新建/校验不再出现 Instant SQL 类型错误。
- 一卡一 PR。

### I5 — 发起错误展示

**要求**：

- 发起提交失败时展示后端可读错误（或映射文案），提供重试；勿只 Internal error。
- 一卡一 PR。

### I6 — 平台模板错误展示

**要求**：

- 保存草稿 / 校验 / 发布失败时展示后端明确错误。
- 一卡一 PR。

### I7 — 已发布模板版本下拉

**前置**：I2 已合。

**要求**：

- H11：产品定义页「已发布模板版本」为「暂无」、保存禁用。I2 后复核：若有已发布版本却仍空，修 API/字段/前端读取；若确实无版本，空态说明要可读。
- 一卡一 PR。

### I8 — 租户主路径重冒烟

**前置**：**I1 已合入 main**（I5 若已合更好）。

**要求**：

- 重跑：订阅开通 → 发起 → 待办审批（含意见）→ 我的申请。
- 记账 `docs/planning/I8-手工冒烟记账.md`；断点只记账不改产品代码。
- 一卡一 PR。

### I9 — 平台配置重冒烟

**前置**：**I2 已合入 main**（I6/I7 若已合更好）。

**要求**：

- 重跑：新建模板 → 校验 → 发布 → 绑到产品（或记清断点）。
- 记账 `docs/planning/I9-平台配置冒烟记账.md`。
- 一卡一 PR。

### I10 — 双路径小结 + 演示机再验

**前置**：**I8、I9**。

**要求**：

- 汇总租户主路径与平台配置是否已通；按 P6/H12 清单再记演示机等价启动；无外网真机写清卡点，不宣称已部署。
- 记账 `docs/planning/I10-上线门禁复验小结.md`。
- 一卡一 PR；Codex 本波队尾。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **I1 → I2 → I3 → I4**（I1/I2 可并行开 PR，建议都进 main 后再 I3） |
| **Claude** | **I5 → I6 → I7**（I5/I6 可与 Cursor 并行；I7 等 I2） |
| **Codex** | **I8 → I9 → I10**（I8 等 I1；I9 等 I2；I10 等 I8+I9） |

**并行说明**：I1 与 I2、I5、I6 可同时开；I8 挂起直到 I1；I9 挂起直到 I2。有人合入后由 NexusOne 在本聊天通知 Eric；开局即每人队列，不必每次只派 1 张。

## 本机路径与分支前缀（强制）

| 人 | 分支前缀 | worktree |
|---|---|---|
| **Cursor** | `cursor/` | `D:\Projects\NexusOne-cursor` |
| **Codex** | `codex/` | `D:\Projects\NexusOne-codex` |
| **Claude** | `claude/` | `D:\Projects\NexusOne-claude` |
| **NexusOne** | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 本波不做 / 后置

- 自由画布；分支/并行 BPM；H5 / 支付 / 高级审批
- CI 复活、全量自动化 E2E
- 整站视觉 token 重做；新开产品功能波

## 修订记录

- 2026-09-25：初稿。H10/H11 均断在 Instant→Postgres JDBC；Eric 确认开 I 波薄修卡。

### 本机执行纪律（Eric-Desktop · 强制）

1. **所有文件只能落在自己的 worktree 内**（含源码、测试、临时脚本、补丁、日志、导出物）。禁止写到桌面、`Downloads`、`D:\temp`、用户临时目录（除系统/工具不可避免的缓存）、主克隆 `D:\Projects\NexusOne`、或其他 agent 的 worktree。需要脚本就写在本 worktree 的 `scripts/` 或卡内路径，用完可删，但**先落在树内**。
2. **禁止让 Eric 反复点 Run / 批准按钮**。终端命令、构建、测试、git 一律由你在 agent 会话内直接执行并读回结果；不要把命令甩出来让人手工跑，不要拆成「请点 Run」才能继续的步骤。若某次工具调用被环境拦下，改用 worktree 内等价方式重试；仍不行再**一次**说明阻塞，不要连环索权。
3. **不要**用「请 Eric 在外部终端执行」当默认路径；交付物是已推送的 PR / 已合并的 main，不是一串待点击命令。
