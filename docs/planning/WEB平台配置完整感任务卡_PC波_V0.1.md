# WEB 平台配置完整感任务卡（PC 波）V0.1

- 基线：`main` @ `700adbe5`（XB1–XB9 已合；Eric 选定下一波 = **P1 平台配置完整感**，**非** P0 demo host/Keycloak）
- 依据：平台演示路径「新建产品 → 配表单/流程 → 绑 SKU → 发布」仍缺**连贯向导**与**产品维度**可打开的台账 / 打印模板 / 统计报表**最小样例**；R/S 已把配置台观感与反馈壳打底
- 规范：以仓库 `docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` 为准；空态/错误/文案对齐 **S 波反馈壳**（Toast / Result / `NxEmpty` 原因+下一步）与 O/Q/R token
- 目标：平台 demo **完整感**——一条可跟完的产品配置向导 + 绑定到**当前产品**的台账 / 打印模板 / 统计报表**最小可演示样例**（能打开、有样例数据或诚实空态；**不是**完整编辑器 / BI / 打印设计器）
- 执行方：**Cursor / Claude** 二人并行；**不派 Codex 队列**（配额空）；NexusOne 协调
- 现状速记（2026-09-26 扫 `origin/main` 确认）：
  - **产品定义绑定**：`/p4/product-definitions` → `ProductDefinitionBindingView.vue` 已有 4 步 journey（发布表单模板 → 发布流程 → 保存产品定义 → 绑定 SKU），可新建定义并绑 SKU；**尚无**独立「向导壳」页、无统一就绪态读口、发布步骤与样例资产未串成「当前产品」维度
  - **表单模板**：P1 `/p1/form-templates`（`FormTemplateListView` / `FormTemplateEditorView`）；流程：P3 `/p3/workflow`（`ProcessViewerView`）；发布中心：`/p3/publish-center`
  - **台账 / 报表**：P3 `LedgerListView` / `ReportView` 为**租户工作室**能力（创建台账、按台账查询导出），**未**以「当前产品定义」为锚做最小样例入口
  - **打印模板**：需求规格有「打印」能力点；仓库 **无** `PrintTemplate*` 视图/路由/BE——本波只做**最小样例**（打开 + 样例），不建完整设计器
  - **XB 并行风险**：XB1–XB9 已合；Claude **XB10–XB15 可能仍在飞**。PC 卡**禁止**随意改 `/p4/operations`、`OperationsAnalyticsView`、升级 stepper / `upgrade-steps` / upgrade facts、订阅表权益投影列、`CommercialEntitlements*`。优先落在**平台产品配置**面：`ProductDefinitionBindingView`、向导新壳、产品锚的样例入口
  - **明确不做 H5**：`web/src/mobile/**` 不在本波范围

## 默认纪律

1. **一卡一 PR**；标题以 `PC*` 开头；合并优先 **squash**。
2. 裸克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 干活见文末。
3. **明确不做 / 硬禁**：
   - **H5** 与 `web/src/mobile/**`
   - **真支付 / 计费 / 优惠券 / 发票**；不改商业成交语义
   - **平台审批订阅**（platform approve-subscription）产品化
   - **高级 BPM**（会签/或签/加签转交/子流程）——本波无关；流程侧只复用已有发布/绑定
   - **假 KPI / 假趋势曲线**；统计样例无时间序列 API 时用诚实空态/「样例暂无」文案，禁止 invent 折线
   - 完整台账编辑器、完整打印设计器、独立 BI Designer
   - CI 复活宣称、真 Keycloak / 外网终态宣称
   - 与 XB 抢路径：operations / stepper / 权益投影（见上）
4. **视觉与反馈**：空态/错误/成功走 **S 波反馈壳** + O/Q/R token；禁止另起视觉体系。
5. **薄片**：每卡 **1–2 小时**；一产品切片；**前端/后端能拆则拆**；**迁移 / 密钥单独成卡**；禁止一卡吞整向导引擎或整打印/报表产品。
6. **Flyway**：仅证明必须时由专卡新增；取**下一全局版本号**（当前仓最高 **V705** → 下一张为 **V706**）；`outOfOrder` **关闭**；优先证明现表/现 API + 静态样例足够、无需迁移。本波默认**不**开迁移卡，除非 Claude 盘点结论「必须」。
7. **验收**：本地打开对应页目检 + 截图进 PR；注明空态/样例/错误态；有测试则跑相关单测并在 PR 摘录。

## 阶段对照

| 阶段 | 内容 | 本卡地位 |
|---|---|---|
| R / S | 平台配置台视觉 + 反馈壳 / 去开发味 | **复用** |
| H8 / K 系列 | 模板发布 → 产品定义 → SKU 绑定可走通（已有页） | **加完整感**，不重做绑定语义 |
| XB | 星市运营分析 · 升级 stepper · 权益投影 | **并行避让**；PC 不碰其路径 |
| **PC** | 平台配置完整感：向导 · 台账/打印/报表最小样例 | **本波开启** |
| （后）P0 | demo host / Keycloak | **本波不开**（Eric 已明确选 P1） |

## PC 波任务总表（PC1–PC15）

| 卡 | 标题 | 建议方 | 前置 | 估时 |
|---|---|---|---|---|
| **PC1** | 产品配置向导页壳：步骤条（表单 → 流程 → 绑 SKU → 发布）+ 空壳/骨架；禁假进度勾选 | Cursor | 无 | 1–2h |
| **PC2** | 导航/入口：侧栏或产品定义页进向导；与 `/p1/form-templates`、`/p3/workflow`、`/p4/product-definitions` 互链 | Cursor | 建议 PC1 后 | ~1h |
| **PC3** | 向导空态/错误态对齐 S 反馈壳（`NxEmpty` 原因+下一步；失败 `NxErrorState`） | Cursor | 建议 PC1 后 | 1–2h |
| **PC4** | 产品绑定 UI 薄片：向导内/旁挂 SKU 绑定与已有定义列表入口（FE only；复用绑定 API） | Cursor | 建议 PC1/PC2 后 | 1–2h |
| **PC5** | 平台配置入口文案/空态薄收（择 `FormTemplateListView` 或 `PublishCenterView` 或向导入口页）+ Cursor 轴自检截图 | Cursor | 建议 PC3 后 | 1–2h |
| **PC6** | 向导就绪态 BE 读口：表单已发布 / 流程已发布 / 可绑 SKU / 产品已发布（或已绑）四态诚实返回；无数据不伪造完成 | Claude | 无 | 1–2h |
| **PC7** | 向导 FE 接 PC6：真态勾选/高亮；未完成保持诚实；去掉纯前端假 done | Claude | PC1 + PC6 后 | 1–2h |
| **PC8** | 台账最小样例 FE：当前产品维度「打开样例台账」入口/只读壳（非完整编辑器） | Claude | 建议向导壳可点后 | 1–2h |
| **PC9** | 台账最小样例 BE：产品可关联的样例读契约（静态样例或现有 ledger 薄投影）；禁 invent 账本引擎 | Claude | 可与 PC8 紧随 | 1–2h |
| **PC10** | 打印模板最小样例 FE：打开 + 预览壳（非完整设计器）；空态诚实 | Claude | 无（新面，避 XB） | 1–2h |
| **PC11** | 打印模板最小样例 BE：样例契约或内置静态样例；**优先无迁移** | Claude | 可与 PC10 紧随 | 1–2h |
| **PC12** | 统计报表最小样例 FE：产品维度打开样例；**禁假 KPI/假趋势** | Claude | 无 | 1–2h |
| **PC13** | 统计报表最小样例 BE：诚实样例计数或空数据契约；无时间序列则明文「趋势暂无」 | Claude | 可与 PC12 紧随 | 1–2h |
| **PC14** | 契约/薄测：向导就绪态非假完成；三样例读口形状稳定；PR 摘录测试命令 | Claude | 建议 PC6/PC9/PC11/PC13 后 | 1–2h |
| **PC15** | PC 波打勾记账（docs-only）：PC1–PC15 勾选 + PR 链接 + 截图/摘录；完整感结论与遗留 | Claude | 大部合入后 | 1h |

## 薄片要求

### Cursor PC1–PC5（向导壳 · 导航 · 空态 · 绑定 UI 薄）

- **PC1** 新建薄页（建议 `web/src/views/market/ProductConfigWizardView.vue` 或邻近命名）+ `web/src/router.js` 路由（建议 `/p4/product-config-wizard` 或挂在产品定义下的子路径；**PR 写明**）。步骤条固定四步文案：**配表单 → 配流程 → 绑 SKU → 发布**（可与现有 journey 语义对齐，但本卡要有独立向导壳）。默认步骤态未接 BE 时一律「未完成 / 待开始」，**禁止**为了好看默认全勾。`NxPageHeader` + 骨架可选。
- **PC2** 入口：在 `web/src/nav/p4.js` 和/或 `ProductDefinitionBindingView` / Studio 相关入口加链到向导；保留既有「产品定义」页，不删。互链到 `/p1/form-templates`、`/p3/workflow`、`/p4/product-definitions`。**不要**改 `operations` / 升级相关 nav。
- **PC3** 向导各步空态：无已发布模板 / 无已发布流程 / 无可绑 SKU / 未发布 —— 用 `NxEmpty`（原因 + 下一步 CTA）；加载失败 `NxErrorState` + 重试。文案去开发味，对齐 S。
- **PC4** FE only：向导内提供「去绑定 / 打开已有定义」薄面板或嵌入现有绑定交互的最小入口；调用已有产品定义 / SKU 绑定 API；**不**新造绑定语义；**不**碰 XB 投影列。
- **PC5** 择一平台配置入口页做文案/空态薄收（`FormTemplateListView`、`PublishCenterView`、或向导入口），与 S 壳一致；附 Cursor 轴 PC1–PC5 目检截图；不扩 scope 到完整编辑器。

### Claude PC6–PC15（就绪态 BE · 三样例 · 测 · 清单）

- **PC6** BE 读口（如 `GET .../product-definitions/{id}/config-readiness` 或平台产品配置就绪汇总；**PR 写明路径**）：返回四态布尔/枚举（formPublished / processPublished / skuBound / productPublished 或等价）；数据不足时全 false / NOT_READY，**禁止**伪造全部完成。单测锁「空库/无绑定 → 非全绿」。
- **PC7** 向导 FE 消费 PC6：替换 PC1 的纯前端默认；仅真就绪步骤可标完成；跳转 CTA 仍指向真实配置页。
- **PC8** FE：在向导或产品定义旁提供「样例台账」入口；打开只读/最小列表壳，标明绑定当前产品；**禁止**复制整份 `LedgerListView` 创建引擎；可深链到现有台账页并带产品上下文 query（若现 API 不够则等 PC9）。
- **PC9** BE：最小样例契约——当前产品可解析到的样例台账摘要（id/name/code 或「暂无样例」）；优先复用现有 P3 ledger/application 读模型；**禁止**新账本写入引擎；迁移仅当证明必须（默认不做）。
- **PC10** FE：新路由薄页（建议 `/p4/product-print-sample` 或产品配置子路径）；预览区展示静态/样例打印布局占位；空态诚实；**不是**拖拽打印设计器。
- **PC11** BE：返回一份内置样例打印模板 JSON/字段列表即可；**优先零迁移**（classpath/静态资源或现有表证明足够）；若必须 Flyway → 单卡说明 + V706 + `outOfOrder` off，且不夹带 UI。
- **PC12** FE：产品维度「统计报表样例」页；展示 0–少量诚实计数或空表；**禁止**假折线、假「使用趋势」、假 KPI 大盘。
- **PC13** BE：样例统计读口；无聚合则返回空列表/零 + 文案字段 `trendAvailable=false`；禁编造时间序列。
- **PC14** 契约/单测：PC6 非假完成；PC9/PC11/PC13 响应形状稳定；相关 `*Test` / HTTP 测；PR 摘录 `mvn -q -Dtest=... test` 或前端测命令。
- **PC15** 新建 `docs/planning/PC15-PC波打勾记账.md`（或同目录约定名）：PC1–PC15 勾选 + PR + 截图路径；写明向导是否可跟完、三样例是否可打开、是否开过迁移、与 XB 路径零冲突确认。

## 二人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **PC1 → PC2 → PC3 → PC4 → PC5**（一卡一 PR；**禁止中途停等 Eric 点 Run / Always Allow**——本批连续做完队列全部卡） |
| **Claude** | **PC6 → PC7 → PC8 → PC9 → PC10 → PC11 → PC12 → PC13 → PC14 → PC15**（PC7 依赖 PC1+PC6；样例 FE/BE 可成对紧随但仍一卡一 PR；同样连续跑完，不等 Run 点击） |
| **Codex** | **本波不派发**（配额空） |

每人 ≥5 张：Cursor 5 · Claude 10。

## 与 XB 波的路径避让（强制）

| 勿碰（XB / 已合或在飞） | PC 应落点 |
|---|---|
| `/p4/operations`、`OperationsAnalyticsView` | 产品配置向导、产品定义 |
| `P4ManagementView` 升级 stepper / upgrade-steps / facts | 不改安装升级 UI |
| 订阅表权益投影列、`CommercialEntitlements*`、XB9–15 投影相关 | 不改商业权益读模型 |
| 假运营 KPI/趋势 | 统计样例必须诚实空/零 |

## 延后（本波不做）

- P0 demo host / Keycloak
- H5 全文与 `web/src/mobile/**`
- 真支付、平台审批订阅产品化
- 高级 BPM；完整打印设计器；完整 BI
- 打开 `outOfOrder=true`、手改 schema history
- 密钥轮换（本波无密钥卡；发现需求则停并记账）

## 工作树路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |
| （参考）Codex | `codex/` | `D:\Projects\NexusOne-codex`（本波不用） |

### 本地执行纪律（Eric-Desktop · 强制）

1. 改动文件只落本 worktree；禁止写桌面/`Downloads`/裸克隆/他人目录。
2. 自己跑终端/构建/测试/git；**禁止等 Eric 点 Run / Always Allow**——批内连续做完队列全部卡，一卡一 PR，卡与卡之间不停顿等批准。worktree：`D:\Projects\NexusOne-{cursor,claude}`；分支前缀 `cursor/` · `claude/`。
3. 自己开 PR / 能合则 squash 合 main；不是只交半截补丁。
4. 禁止 junction；裸克隆只 fetch。
5. 密钥/迁移：默认无迁移；若必须则单卡 + V706 + outOfOrder off；本波无密钥卡；发现密钥需求则停并记账，勿塞进功能卡。

## 修订记录

- 2026-09-26：PC 波初稿。Eric 选定「P1 平台配置完整感」（非 P0）；对照产品定义 journey、P1 模板、P3 台账/报表、无打印面、XB1–XB9 已合且 XB10+ 可能在飞；Cursor 5 卡 / Claude 10 卡；不派 Codex；禁 H5/支付/平台审批订阅/高级 BPM/假 KPI；Flyway 仅证明必须（V705 之上）。
