# WEB 上线门禁任务卡（G 波）V0.1

- 基线：`main@08ef9db6`（P5 #415 已合；P 波 P1–P9 齐）
- 依据：`docs/planning/P5-手工冒烟记账.md`；上线口径=平台可配置产品 + 租户可订购使用 + 主路径界面好看 + 模板/流转 MVP 拖拽（非自由画布）
- 目标环境：**演示 / 内测可走通主路径**（非公网商用首发）
- 执行方：本机 Cursor / Codex / Claude（前缀与 worktree 同第五波 §六 / P 波）；NexusOne 协调

## 默认决策

1. **一卡一 PR**；PR 标题以 `G*` 开头；做完直接 squash 合并。
2. **本机路径**：主克隆 `D:\Projects\NexusOne` **只允许 fetch**；每人独立 worktree + 前缀分支（`cursor/` `codex/` `claude/`；NexusOne 用 `nexusone/`）。不要目录 junction / 链接。
3. **明确不做**：自由画布；分支/并行流转；H5；支付；高级 BPM；整站视觉体系重做；CI 复活；无关新功能。
4. **验收口径**：未合 **G1+G2** 前勿宣称租户订购主路径已通；未合 **G3+G4** 前勿宣称 MVP 拖拽已交付；**G5** 在 G1/G2 合入后重跑冒烟记账。

## 任务总览

| 卡 | 内容 | 类型 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|---|
| **G1** | 修演示 SKU entitlements 形状，使 `GET /api/v1/p4/catalog` 不再 500，目录能列出「演示数据 · 简单审批」 | 后端·种子 | Cursor | 无 | 1h |
| **G2** | 演示种子让林晓（或明确演示管理员）具备 `TENANT_ADMIN`（或产品认可等价岗位），订阅开通不再 403 | 后端·种子 | Cursor | 建议 G1 后同机复验 | 1h |
| **G3** | 平台表单模板编辑 MVP 拖拽：类型拖入 + 列表内排序 + 属性 + 预览（非自由画布） | 前端 | Claude | 无 | 1–2h |
| **G4** | 流转配置 MVP：顺序节点增删改 + 拖拽排序；主路径页观感薄片（非分支/并行/自由画布） | 前端 | Claude | 建议 G3 后 | 1–2h |
| **G5** | 按 P6 演示机清单起演示机；G1/G2 合后重跑订购→发起→审批→我的申请并记账 | 运维·验收 | Codex | **G1、G2** | 1–2h |

## 卡片正文

### G1 — 目录 entitlements 形状（阻塞订购）

**现象**（P5）：`GET /api/v1/p4/catalog` 返回 500，`invalid SKU entitlements`。演示种子 SKU JSON 为 `{"demo":true,"entitlements":{...}}`，读模型要 `Map<String, EntitlementSpec>`。整份目录列不出，「演示数据 · 简单审批」不出现。单条 `GET /api/v1/p4/skus/demo.simple-approval` 可为 200。

**要求**：

- 只改种子 / 映射所需最小面，使 catalog 列表成功且演示 SKU 可见。
- 本地空库 + demo 开关能验证 catalog 200。
- **禁止** `outOfOrder=true`；若需新迁移，版本号取全局最高已用版本 +1（V705 规则不变）。
- 一卡一 PR；不扩订阅权限或发起页。

### G2 — 演示租户管理员（阻塞订阅）

**现象**（P5）：林晓岗位为 `engineering_manager`，周然为 `software_engineer`；订阅要求任职岗位代码 `TENANT_ADMIN`，返回 403 `tenant administrator role is required`。

**要求**：

- 演示种子让林晓（或另设明确演示管理员账号并写清登录方式）具备 `TENANT_ADMIN`（或产品认可的等价岗位），使 `POST /api/v1/p4/tenants/{tenantId}/subscriptions` 能开通演示 SKU。
- 可本地空库点验：目录可点订阅开通。不扩发起页 UI。
- 一卡一 PR；种子改动写进 PR 说明。

### G3 — 模板 MVP 拖拽

**要求**：

- 平台表单模板编辑：左侧字段类型拖入、列表内上下拖排序、点选改属性、右侧预览。
- **不要**自由画布 / 栅格布局编辑器。
- 对照 `docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md`，界面要好看。
- 对接现有草稿 API；只动前端（除非发现阻塞的最小 API 缺口——若需另开卡说明）。
- 一卡一 PR。

### G4 — 流转 MVP 拖拽 + 主路径观感薄片

**要求**：

- 流转配置：顺序节点可增删改、拖拽排序。
- **不做**分支、并行、自由画布。
- 顺手收主路径相关页（目录 / 已开通 / 发起 / 待办 / 我的申请）明显丑陋点，薄片即可，不整站重做。
- 一卡一 PR。

### G5 — 演示机 + 主路径重冒烟

前置：**G1、G2 已合入 main**（可先按 P6 清单准备演示机，冒烟等前置合入）。

**要求**：

- 按 P6 演示机清单起一版（或明确卡在哪一步）。
- 重跑：订阅开通 → 发起 → 待办审批（含意见）→ 我的申请；断点只记账到 `docs/planning/`（建议 `G5-手工冒烟记账.md`）。
- 新建一次性库勿污染已有 `nexusone`；不改产品范围。
- 一卡一 PR。

## 三人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **G1 → G2** |
| **Claude** | **G3 → G4** |
| **Codex** | **G5**（冒烟必须等 G1、G2 合入；演示机准备可先做） |

**并行说明**：G1 与 G3、演示机准备可同时开；G2 建议在 G1 后；G4 建议在 G3 后；G5 冒烟挂起直到 G1+G2 合入。有人合入后由 NexusOne 在本聊天通知 Eric 再派下一张。

## 本机路径与分支前缀（强制）

| 人 | 分支前缀 | worktree |
|---|---|---|
| **Cursor** | `cursor/` | `D:\Projects\NexusOne-cursor` |
| **Codex** | `codex/` | `D:\Projects\NexusOne-codex` |
| **Claude** | `claude/` | `D:\Projects\NexusOne-claude` |
| **NexusOne** | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 本波不做 / 后置

- 自由画布表单编辑器；分支/并行 BPM
- H5 / 支付 / 高级审批模式
- CI 流水线复活、全量自动化 E2E
- 整站视觉 token / Skeleton 体系重做

## 修订记录

- 2026-09-25：初稿。P5 门禁未通；Eric 确认尽快上线，三人继续 Cursor/Claude/Codex；Claude 负责视觉+MVP 拖拽。

### 本机执行纪律（Eric-Desktop · 强制）

1. **所有文件只能落在自己的 worktree 内**（含源码、测试、临时脚本、补丁、日志、导出物）。禁止写到桌面、`Downloads`、`D:\temp`、用户临时目录（除系统/工具不可避免的缓存）、主克隆 `D:\Projects\NexusOne`、或其他 agent 的 worktree。需要脚本就写在本 worktree 的 `scripts/` 或卡内路径，用完可删，但**先落在树内**。
2. **禁止让 Eric 反复点 Run / 批准按钮**。终端命令、构建、测试、git 一律由你在 agent 会话内直接执行并读回结果；不要把命令甩出来让人手工跑，不要拆成「请点 Run」才能继续的步骤。若某次工具调用被环境拦下，改用 worktree 内等价方式重试；仍不行再**一次**说明阻塞，不要连环索权。
3. **不要**用「请 Eric 在外部终端执行」当默认路径；交付物是已推送的 PR / 已合并的 main，不是一串待点击命令。
