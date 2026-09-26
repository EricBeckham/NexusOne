# WEB 产品配置导入导出与对外契约（IM 波）V0.1

- **基线**：`origin/main` @ `2eb9b971`（2026-09-26）；PC1–PC5、PC6 已合；PC7+（就绪态 FE / 三样例）可能仍在飞
- **依据**：平台「表单 → 流程 → 产品定义 → 绑 SKU → 发布」可走通，但**缺少可搬运的产品配置包**与**对外系统只读/同步契约**规划；本波 docs + 薄卡，**不**在本 PR 实现
- **规范 / 反馈**：O/Q/R token + S 反馈壳（`NxEmpty` / Toast / `NxResult`）；错误面向运营可懂
- **执行方**：建议 **Cursor（FE 壳）** + **Claude（BE 契约）**；NexusOne 协调；**不派 Codex**
- **Flyway**：当前仓最高 **V705**；仅证明必须时专卡 **V706+**，`outOfOrder` **关闭**；默认优先现表 + 包内清单

## 1. 目标

交付「产品配置包」导入/导出的**契约与薄实现路径**，使平台配置可在环境间搬运，并给外部系统一份稳定的只读/同步形状——**不是**支付、不是完整打印设计器、不是重做 PC 向导壳。

## 2. 导出包内容（V0.1）

建议格式：`application/zip`，根目录 `nexusone-product-config.json` + 可选资源。

### 2.1 清单 `manifest`

```json
{
  "format": "nexusone.product-config",
  "formatVersion": "1.0",
  "exportedAt": "ISO-8601",
  "source": { "env": "demo|staging|prod-label", "productDefinitionId": "..." },
  "checksum": "sha256:..."
}
```

### 2.2 必含资产

| 资产 | 来源（现仓） | 包内键 | 说明 |
|---|---|---|---|
| 表单模板已发布版本 | `/api/v1/p1/platform/form-templates` 发布快照 | `formTemplates[]` | 含 field schema / version / code；不含运行时填报数据 |
| 流程定义已发布版本 | Process draft/publish 读模型 | `processDefinitions[]` | 节点/边/条件摘要；不含运行实例 |
| 产品定义 | `/api/v1/p4/product-definitions/{id}` | `productDefinition` | 绑定的模板版本 + 流程版本引用 |
| SKU 绑定 | `PUT .../skus/{skuCode}` 的读回 | `skuBindings[]` | skuCode + 权益声明引用；**不含**价格/支付 |

### 2.3 延后资产（包内可选占位，本波可不实现读写）

| 资产 | 状态 |
|---|---|
| 打印模板 | **later**：仅 `printTemplates: []` + `printSupport: "config+preview-later"`；完整设计器禁 |
| 台账/报表样例 | 交给 PC8–PC13；IM 包可引用 id，不嵌业务数据 |
| 租户开通/安装记录 | **禁止**进入配置包 |

## 3. 导入：校验 · 幂等 · 错误 UX

### 3.1 校验流水线

1. **格式**：`format` + `formatVersion` 白名单（仅 `1.0`）
2. **完整性**：必含四类引用齐全；checksum 可选强制
3. **语义**：字段 code 唯一；流程节点引用存在；产品定义指向包内版本
4. **冲突策略**（导入对话框三选一，默认 **Skip existing**）：
   - `skip`：同 code 已存在则跳过
   - `new-version`：创建新版本号，不覆盖已发布
   - `fail`：任一冲突整包失败（事务）

### 3.2 幂等

- 导入请求 `Idempotency-Key`（头）+ 包 `checksum`；同一键 24h 内返回同一 `importJobId` 结果
- 作业态：`PENDING | VALIDATING | APPLYING | SUCCEEDED | FAILED | PARTIAL`
- **禁止**静默覆盖已发布版本

### 3.3 错误 UX（S 壳）

| 场景 | UI |
|---|---|
| 包损坏 / 版本不支持 | `NxErrorState` + 原因；禁半导入 |
| 校验失败列表 | 表格：资产 / code / 原因；`NxEmpty` 无错误时不出现 |
| 部分成功 | `NxResult` warning +「已导入 / 跳过 / 失败」计数；下一步 CTA |
| 成功 | Toast + `NxResult`「打开产品定义」 |

## 4. 对外系统契约（形状 · 版本 · 鉴权注记）

### 4.1 原则

- 对外只暴露 **NexusOne 公开 API 形状**，不直连 Keycloak / Flowable / MinIO 内部接口（规范 §0.1）
- **本波不发明 Keycloak 改造**：鉴权注记仅描述「沿用现有平台会话 / 服务账号模式（待环境手册）」；实现卡禁止塞入 IdP 重构

### 4.2 建议只读端点（形状草案，实现见卡）

| Method | Path | 用途 |
|---|---|---|
| `GET` | `/api/v1/p4/product-definitions/{id}/export` | 下载配置包（zip 或 JSON bundle） |
| `POST` | `/api/v1/p4/product-definitions/import` | 上传包 → 返回 `importJobId` |
| `GET` | `/api/v1/p4/product-definitions/import-jobs/{id}` | 作业状态 |
| `GET` | `/api/v1/p4/product-definitions/{id}/config-readiness` | 已有（PC6）；对外可复用为就绪探针 |

### 4.3 版本

- URL 前缀保持 `/api/v1`
- 包 `formatVersion` 与 API 响应头 `X-Nexus-Config-Format: 1.0` 对齐
- 破坏性变更 → `formatVersion 2.0` + 并行读一期；禁止默默改字段语义

### 4.4 鉴权注记（不实现）

- 平台管理员角色才能 export/import
- 服务账号：沿用现有 P5 client / 平台 credential 模式（若有）；**本波不新开 Keycloak realm/client 卡**
- 审计：导入导出写入现有审计链（trace id）；失败也记账

## 5. 与 PC 波关系

| PC | IM 关系 |
|---|---|
| PC1–PC5 向导壳 | **复用入口**：向导/产品定义页加「导出/导入」CTA；不重做向导 |
| PC6 就绪态 | 导出前可提示未就绪；不阻断（允许导出草稿包需显式勾选，默认禁） |
| PC7–PC15 样例 | 并行；IM 不实现台账/打印/报表引擎 |
| 打印 | IM 仅占位；CF/PC 打印预览之后再进包 |

## 6. 薄卡 IM1–IM8

| 卡 | 标题 | 建议方 | 前置 | 估时 |
|---|---|---|---|---|
| **IM1** | 配置包 `format 1.0` JSON Schema + 样例包（docs + `docs/planning/fixtures/`） | Cursor | 无 | 1–2h |
| **IM2** | 导出 FE 壳：产品定义页「导出配置」按钮 + 下载中/失败 `NxErrorState`（可先打静态样例） | Cursor | IM1 | 1–2h |
| **IM3** | 导入 FE 壳：上传 + 冲突策略单选 + 校验结果表 + `NxResult` | Cursor | IM1 | 1–2h |
| **IM4** | 导出 BE：组装 form/process/definition/sku → zip/json；无迁移优先 | Claude | IM1 | 1–2h |
| **IM5** | 导入 BE：校验 + `skip|new-version|fail` + Idempotency-Key | Claude | IM1 | 1–2h |
| **IM6** | 导入作业读口 + 契约测（成功/冲突/坏包） | Claude | IM5 | 1–2h |
| **IM7** | FE 接真 API；去掉静态样例；空错态 S 壳 | Cursor | IM2/3 + IM4/5 | 1–2h |
| **IM8** | IM 打勾记账 + 对外契约摘录（本文件勾选 + PR 链） | NexusOne / Claude | 大部合入后 | 1h |

≥5 张；Cursor 4 · Claude 3 · 收口 1。

## 7. 硬禁

- **真支付 / 计费 / 发票 / 优惠券**；SKU 绑定不含成交语义
- **完整打印设计器**；打印仅 later 占位
- **租户自助注册**、平台审批订阅产品化
- **Flyway** 除非证明现表不够 → 专卡 V706+ / outOfOrder off
- **Keycloak / IdP 重构**；不直连 Flowable Admin
- **H5**；不把开通/安装实例打进配置包
- 打开 `outOfOrder=true`、手改 schema history

## 8. 工作树

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

## 9. 修订记录

- 2026-09-26：V0.1 初稿。对照 ProductDefinition / FormTemplate / Process API；PC6 就绪口可复用；打印 later；Flyway V705 之上。
