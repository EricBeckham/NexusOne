# WEB 上线补强任务卡（P 波）V0.1

- 基线：`main@33eeef5a`（`L12: prod profile with startup guard; fix IAM/permission jdbc boot (#404)`）
- 依据：L 波合入后仍挡演示 / 内测的缺口（见 L1 / L4 / L5 / L11 / L12）；与三人分工同 L 波 §「三人队列」
- 目标环境：**演示 / 内测**（非公网商用首发）
- 执行方：本机 Cursor / Codex / Claude（前缀与 worktree 同第五波 §六）；NexusOne 协调

## 默认决策

1. **一卡一 PR**；PR 标题以 `P*` 开头；做完直接 squash 合并。
2. **本机路径**：主克隆 `D:\Projects\NexusOne` **只允许 fetch**；每人独立 worktree + 前缀分支（`cursor/` `codex/` `claude/`；NexusOne 用 `nexusone/`）。不要目录 junction / 链接。
3. **明确不做**：H5；支付；高级 BPM；生产级多活 / HA 宣称；为种子把 V006 删掉的 `(template_id, field_key)` 唯一约束加回去；整页视觉大改；自动化冒烟全套；CI 复活（可另卡）。
4. **验收口径**：未合 **P1** 前勿宣称演示 E2E 已通；未合 **P2** 前勿宣称后端全量无错误。

## 任务总览

| 卡 | 内容 | 类型 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|---|
| **P1** | 修 K20 种子幂等：无 `(template_id, field_key)` 唯一约束时仍可插字段 | 后端 | Claude | 无 | 1h |
| **P2** | 修 P3 路径 IT：`ConditionalOnBean` / K15b 装配导致 16 错 | 后端·测试 | Claude | 无 | 1–2h |
| **P3** | L5 视觉清单小修 A（仅前端，1–2h 可收） | 前端 | Cursor | 无 | 1–2h |
| **P4** | L5 视觉清单小修 B（仅前端，另一薄片） | 前端 | Cursor | 无 | 1–2h |
| **P5** | P1 合入后重跑手工冒烟；更新 L4 记账 | 验收 | Cursor | **P1** | 1–2h |
| **P6** | 演示机部署清单（对齐 L10/L12：env、prod profile、密钥注入） | 文档·运维 | Codex | L10、L12 已合 | 1–2h |
| **P7** | 真实备份/恢复演练证据（按 L11 runbook 跑一次并记账） | 运维 | Codex | L11 已合 | 1–2h |
| **P8** | `.env.example` 持久化变量名对齐 `NEXUS_PERMISSION_PERSISTENCE` | 配置 | Codex | 无 | 0.5h |
| **P9** | prod 下收紧 actuator `metrics` 匿名可读（网关/配置二选一，小修） | 安全·配置 | Claude | L12 已合 | 1h |

> 编号刻意留空，便于插卡。CI 复活、自动化 smoke、Redis 口令等另开。

## 卡片正文

### P1 — K20 种子幂等（阻塞演示）

**现象**（L4）：`ApprovalProductDemoSeedConfiguration` 对 `platform.form_template_field` 使用 `ON CONFLICT (template_id, field_key)`，但 `V006` 已删除该唯一约束，进程退出码 1，浏览器路径走不到。

**要求**：

- 在**不加回** V006 所删唯一约束的前提下，让字段种子幂等（先查再插，或改用仍存在的主键/其它安全冲突键）。
- 本地空库 + `NEXUS_DEMO_ENABLED=true` 能完成种子且进程保持运行。
- 相关单测或至少一次本机启动验证写进 PR 说明。
- **禁止** `outOfOrder=true`；若需新迁移，版本号取全局最高已用版本 +1（当前从 V705 起规则不变）。

### P2 — P3 IT 装配 16 错

**现象**（L1）：`nexus-p3-studio` 16 个错误；根因是 `PinnedProductApplicationValidationService` 带 `@ConditionalOnBean({IdentityRepository, FileObjectRepository})`，`G18PathApplication` 无 file bean 导致跳过，而 K15b Service/Controller 无条件依赖。

**要求**：

- 上述 16 个测试转绿（`G18BusinessPathIT`、`G2SubmitStartFormWriteIT`、`G7*` 相关等）。
- `nexus-bootstrap` 装配与现有绿测不回归。
- 可选路径（自行核实后选最稳）：依赖方加同样条件、测试上下文补 file 相关 bean、或去掉脆弱的 `@ConditionalOnBean`。
- PR 说明附本机 `mvn test` 相关模块结果摘要。

### P3 — L5 视觉小修 A（前端）

从 `docs/planning/L5-视觉差距清单.md`「只记账，本卡不改」里挑 **一块** 能 1–2h 收口的前端薄片（建议优先：待办空表 `NxEmpty`，或「我的已开通」双 Primary 拆操作区之一）。

**要求**：只动 `web/`；对照 `docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md`；不做 H5；大的继续只记账。PR 说明写清改了哪几条清单项。

### P4 — L5 视觉小修 B（前端）

另选 **一块** 与 P3 不重叠的清单项（建议：目录 FilterBar 去大 Card，或品牌眉题 `NEXUS MARKET`→「星市」统一到演示路径相关页）。约束同 P3。

### P5 — 手工冒烟重跑（P1 后）

前置：**P1 已合入 main**。按 `docs/engineering/runbooks/demo-simple-approval.md` 与第五波 §七走通：订阅开通 → 发起 → 待办审批（含意见）→ 我的申请结果。

**要求**：

- 新建一次性库（勿污染已有 `nexusone`）；冒烟后可 DROP。
- 更新 `docs/planning/L4-手工冒烟记账.md`（或新增同目录 `P5-手工冒烟记账.md` 并在 L4 文首链过去）：写清基线 SHA、断点或「路径已通」、stub 登录与种子成员是否对齐。
- 断点只记账；除非是阻塞下一分钟的一行小修，否则不扩 scope。

### P6 — 演示机部署清单

把 L10 密钥名 + L12 `prod` 守卫项收成一份 **可照做的演示机 checklist**（放 `docs/engineering/runbooks/` 或 `docs/planning/`）。

**要求**：变量名无密文；写清 profile、DB、crypto master key、IAM、demo 必须关闭；本地 demo 与 prod 演示机的差异表；验收=按清单能在干净机起一次（或明确卡在哪一步）。

### P7 — 备份/恢复演练证据

按 `docs/planning/L11_备份恢复步骤.md` 对**真实产品库**（或专用演练库）做一次 backup → restore → 冒烟读回。

**要求**：在 planning 或 runbook 旁记证据（时间、库名、命令摘要、结果）；**禁止**把真实口令写入仓库；失败只记账并开最小修复卡建议。

### P8 — `.env.example` 变量名对齐

L12 已注明：示例写 `NEXUS_PERM_PERSISTENCE`，单体读 `NEXUS_PERMISSION_PERSISTENCE`。改示例（及若有文档引用）与代码一致；不改行为默认值除非必要。

### P9 — actuator metrics 收紧

L12 清单：prod 已收紧 health 细节，但 `metrics` 仍可能匿名可读。

**要求**：在配置或安全链上让 prod 下 `metrics`（及非必要端点）不对匿名开放；附最小测试或启动断言；不扩大成完整网关方案。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Claude** | **P1 → P2 → P9** |
| **Cursor** | **P3 → P4 → P5**（P5 必须等 P1 合入） |
| **Codex** | **P6 → P7 → P8** |

**并行说明**：P1 与 P3/P4、P6 可同时开；P5 挂起直到 P1 合入。有人合入后由 NexusOne 在本聊天通知 Eric 再派下一张。

## 本机路径与分支前缀（强制）

| 人 | 分支前缀 | worktree |
|---|---|---|
| **Cursor** | `cursor/` | `D:\Projects\NexusOne-cursor` |
| **Codex** | `codex/` | `D:\Projects\NexusOne-codex` |
| **Claude** | `claude/` | `D:\Projects\NexusOne-claude` |
| **NexusOne** | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 本波不做 / 后置

- H5 / 支付 / 高级审批模式
- CI 流水线复活、全量自动化 E2E
- Redis 口令、主密钥轮换工具、多活 HA
- 视觉规范里需动全局 token / 整站 Skeleton 的项

## 修订记录

- 2026-09-25：初稿。Eric 确认仍按 Cursor / Codex / Claude 三人分工；基线 L12 #404。


### 本机执行纪律（Eric-Desktop · 强制）

1. **所有文件只能落在自己的 worktree 内**（含源码、测试、临时脚本、补丁、日志、导出物）。禁止写到桌面、`Downloads`、`D:\temp`、用户临时目录（除系统/工具不可避免的缓存）、主克隆 `D:\Projects\NexusOne`、或其他 agent 的 worktree。需要脚本就写在本 worktree 的 `scripts/` 或卡内路径，用完可删，但**先落在树内**。
2. **禁止让 Eric 反复点 Run / 批准按钮**。终端命令、构建、测试、git 一律由你在 agent 会话内直接执行并读回结果；不要把命令甩出来让人手工跑，不要拆成「请点 Run」才能继续的步骤。若某次工具调用被环境拦下，改用 worktree 内等价方式重试；仍不行再**一次**说明阻塞，不要连环索权。
3. **不要**用「请 Eric 在外部终端执行」当默认路径；交付物是已推送的 PR / 已合并的 main，不是一串待点击命令。