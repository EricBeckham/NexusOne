# WEB 上线收口任务卡（M 波）V0.1

- 基线：`main@ecc640b9`（J3 后空库复验记账 #453 已合；J1–J3 / #448–#452 已合）
- 依据：`docs/planning/NexusOne-J3后空库复验记账.md`、`docs/planning/I8-手工冒烟记账.md`、`docs/planning/I10-上线门禁复验小结.md`、`docs/planning/L5-视觉差距清单.md`、`docs/engineering/runbooks/demo-host-prod-deployment.md`
- 目标环境：**演示 / 内测可走通主路径**（非公网商用首发）
- 执行方：本机 **Cursor / Claude**（前缀与 worktree 同第五波 §六 / P / G / H / I / J 波）；NexusOne 协调
- **本波不派 Codex**（额度停用）；原 Codex 侧演示机/验收薄项改由 Cursor 做清单与模板，不宣称外网已部署

## 默认决策

1. **一卡一 PR**；PR 标题以 `M*` 开头；做完直接 squash 合并。
2. **本机路径**：主克隆 `D:\Projects\NexusOne` **只允许 fetch**；每人独立 worktree + 前缀分支（`cursor/` `claude/`；NexusOne 用 `nexusone/`）。不要目录 junction / 链接。
3. **明确不做**：自由画布；分支/并行流转；H5；支付；高级 BPM；整站视觉体系重做；CI 复活；无关新功能波；不派 Codex。
4. **验收口径**：双主路径本机空库已在 J 波后复验通过（见 J3 后记账）。本波只收口非阻断展示、观感薄片、门禁文档刷新、演示机/Keycloak **清单与模板**。未取得外网主机与真实密钥前，**勿宣称演示机已部署或真实 Keycloak 已验收**。

## 当前状态（开波前）

| 路径 | 结论 | 说明 |
|---|---|---|
| 平台配置主路径 | **本机通过** | 空库 create→validate→publish→bind；#451 种子流程可选（J3 后记账 / #453） |
| 租户订购使用主路径 | **本机通过** | 订阅→发起→审批含意见→我的申请（I8；J3 后 HTTP 复验） |
| I8 审批成功提示 `approval_status=—` | **非阻断展示** | 已办/我的申请状态已对；提示横线需修（M1） |
| #452 无会话流程草稿登录提示 | **未浏览器复验** | API 仍 401；前端提示需 M2 复验/修 |
| I10 门禁小结 | **过时** | 仍写平台路径未通（I9 时代）；需 M5 按 J 波结果改写 |
| 外网演示机 / 真 Keycloak | **未执行** | 本机 `prod` 等价启动已绿；真机缺主机与密钥（M6/M7 只做模板与清单） |

## 任务总览

| 卡 | 内容 | 类型 | 负责人 | 前置 | 估时 |
|---|---|---|---|---|---|
| **M1** | 审批成功提示显示真实 outcome/status（去掉 `approval_status=—`） | 前端 | Claude | 无 | 1h |
| **M2** | 浏览器复验 #452：无租户会话打开流程草稿给出登录提示；文案不对则修 | 前端 | Claude | 可与 M1 并行 | 1h |
| **M3** | 星市眉题统一（目录/首页仍见 `P4 · NEXUS MARKET` 的位置） | 前端·观感 | Claude | 建议 M1/M2 后 | 1h |
| **M4** | 待办空表改 NxEmpty（解释 + 下一步）；不改撤回行为 | 前端·观感 | Claude | 建议 M3 后 | 1h |
| **M5** | 重写/补记 I10 门禁小结：双路径已绿 + 边界（本地 demo/stub，非外网/真 Keycloak） | 文档 | Cursor | 无 | 1h |
| **M6** | 演示机 env **模板**（仅占位符）+ JAR SHA-256 校验脚本（对照 P6；不写密钥；不宣称已部署） | 运维·模板 | Cursor | 可与 M5 并行 | 1–2h |
| **M7** | Keycloak 演示验收清单：runbook 必做项 + 本机无法代做的外部卡点 | 文档 | Cursor | 建议 M6 后 | 1h |

## 卡片正文

### M1 — 审批成功提示真实状态

**现象**（I8）：周然点「同意」后成功提示附带 `approval_status=—`；已办列表与林晓「我的申请」状态已更新。非阻断展示异常。

**要求**：

- 找出提示文案数据来源；展示真实 outcome/status（如已同意 / APPROVED），不再显示无意义横线。
- 优先只动前端；若确认 approve API 未返回可用字段，在 PR 说明里写清缺口，**不要**在本卡扩后端（另开 Cursor 跟进卡由 NexusOne 协调）。
- 本机 demo 空库点验：同意后提示与已办状态一致。
- 一卡一 PR；不改审批业务语义。

### M2 — #452 无会话登录提示浏览器复验

**背景**：无租户 bearer 时 `GET /api/v1/process/drafts` 仍 401 `P2.IAM.UNAUTHORIZED`；#452 已合入前端提示。J3 后记账未跑浏览器。

**要求**：

- 浏览器：无租户会话打开会触发流程草稿读取的页面，应看到可读的租户登录/会话提示，而非裸 Internal error。
- 文案或交互不对则修前端；API 行为保持 401 即可。
- 一卡一 PR。

### M3 — 星市眉题统一

**依据**：L5 记账——管理页/发起页已是「星市」，首页/目录仍可能 `P4 · NEXUS MARKET`。

**要求**：

- 主路径可见的 Market 首页/目录等眉题统一为「星市」（与已开通/发起一致）。
- 薄片观感；不整站品牌体系重做；不改业务逻辑。
- 一卡一 PR。

### M4 — 待办空表 NxEmpty

**依据**：L5——`TodoInboxView.vue` 空表仍是单元格「暂无」；规范要解释 + 下一步。

**要求**：

- 空态改为 `NxEmpty`（或项目等价空态组件）：简短原因 + 下一步（例如去星市开通/发起）。
- **不**改「我发起」撤回行为（K18 范围外）。
- 一卡一 PR。

### M5 — 刷新 I10 门禁小结

**要求**：

- 更新 `docs/planning/I10-上线门禁复验小结.md`（或同目录新增注明替代关系的补记，二选一，PR 说明写清）：平台路径与租户路径均标为**本机通过**，引用 J1/J3/#448–#453 与 `NexusOne-J3后空库复验记账.md`。
- 明确边界：本地 demo / stub IdP；**不**宣称外网演示机或真实 Keycloak。
- 可保留本机 `prod` 等价启动已绿的叙述；外部卡点仍写清。
- 文档-only；一卡一 PR。

### M6 — 演示机 env 模板 + JAR 校验脚本

**依据**：`docs/engineering/runbooks/demo-host-prod-deployment.md`（P6）。

**要求**：

- 在仓库内增加**可填 env 模板**（仅变量名与占位符注释；**禁止**真实口令/密钥/secret）。
- 增加小脚本（落在本 worktree 约定路径，如 `scripts/`）：从当前树构建 `nexus-bootstrap` JAR 并打印 SHA-256，便于对照清单。
- README/注释指向 P6 清单；明确「模板 ≠ 已部署」。
- 不连接外网主机；不写入任何真实密钥。
- 一卡一 PR。

### M7 — Keycloak 演示验收清单

**要求**：

- 新建 `docs/planning/` 下清单文档：从 P6/L10 抽出 Keycloak 必做项（realm/client、issuer、redirect、受控演示账号等）。
- 单列「本机 agent 无法代做」的外部卡点（主机授权、DNS/TLS、密钥管理器注入等）。
- 不写真实 secret；不改产品代码。
- 一卡一 PR。

## 二人队列

| 人 | 顺序 |
|---|---|
| **Claude** | **M1 → M2 → M3 → M4**（M1/M2 可并行开 PR） |
| **Cursor** | **M5 → M6 → M7**（M5/M6 可并行；M7 建议 M6 后） |

**Codex**：本波不派。

有人合入后由 NexusOne 在本聊天通知 Eric；开局即每人队列（3–4 张），不必每次只派 1 张。

## 本机路径与分支前缀（强制）

| 人 | 分支前缀 | worktree |
|---|---|---|
| **Cursor** | `cursor/` | `D:\Projects\NexusOne-cursor` |
| **Claude** | `claude/` | `D:\Projects\NexusOne-claude` |
| **NexusOne** | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

（Codex worktree 本波不启用。）

## 本波不做 / 后置

- 自由画布；分支/并行 BPM；H5 / 支付 / 高级审批
- CI 复活、全量自动化 E2E
- 整站视觉 token / Skeleton 体系重做
- 外网真机部署与真实 Keycloak 联调（缺主机/密钥前只做 M6/M7）
- 新开产品功能波；派 Codex

## 修订记录

- 2026-09-26：初稿。双主路径本机已绿；Eric 确认开 M 波收口，仅 Claude + Cursor。

### 本机执行纪律（Eric-Desktop · 强制）

1. **所有文件只能落在自己的 worktree 内**（含源码、测试、临时脚本、补丁、日志、导出物）。禁止写到桌面、`Downloads`、`D:\temp`、用户临时目录（除系统/工具不可避免的缓存）、主克隆 `D:\Projects\NexusOne`、或其他 agent 的 worktree。需要脚本就写在本 worktree 的 `scripts/` 或卡内路径，用完可删，但**先落在树内**。
2. **禁止让 Eric 反复点 Run / 批准按钮**。终端命令、构建、测试、git 一律由你在 agent 会话内直接执行并读回结果；不要把命令甩出来让人手工跑，不要拆成「请点 Run」才能继续的步骤。若某次工具调用被环境拦下，改用 worktree 内等价方式重试；仍不行再**一次**说明阻塞，不要连环索权。
3. **不要**用「请 Eric 在外部终端执行」当默认路径；交付物是已推送的 PR / 已合并的 main，不是一串待点击命令。