# VERIFY-T：空库租户主路径复验清单（V0.1）

> 日期：2026-09-26（Asia/Shanghai）。Worktree：`D:\Projects\NexusOne-nexusone`。  
> 目标路径：**应用目录 → 申请开通 → 平台审订 → 已开通可用**。  
> **不做**：真支付、发票、优惠券；不发明 Keycloak。

## 1. 环境与阻塞

| 项 | 状态 | 说明 |
|---|---|---|
| Docker / 空库 | **本会话阻塞** | 同 VERIFY-P；无 `docker`。 |
| 演示种子 | 可用（有库时） | `scripts/demo/*.sql` + P0 #582 去支付路径；stub 用户 `lin.xiao@demo.invalid`。 |
| 历史证据 | 可引用 | J3 空库租户路径：订阅→发起→审批→我的申请 **通过**（即时 `subscribe`）。 |
| 审订路径 | 产品已有 | `/p4/activation-applications`、租户注册审订 `/p1/tenant-registrations`（#601）；与「即时订阅」并存于演示。 |

## 2. 主路径步骤（租户）

| # | 步骤 | 路由 / API | 通过判据 | 断点 / 缺口 |
|---|---|---|---|---|
| T1 | 租户登录 | stub login | 拿到 tenant context | 真实 IdP **不在本卡范围** |
| T2 | 打开应用目录 | `/p4/market` | 可见已发布 SKU / 产品 | 无绑定 product definition 的 SKU → 开通失败/404（G5 曾记） |
| T3a | **演示快径**：直接开通 | 「开通」· `POST …/subscriptions` | 201 + installation | **无支付**；P0 明示去支付 |
| T3b | **审订路径**：申请开通 | 目录「申请开通」· activation application | 生成待审申请 | 须平台在 `/p4/activation-applications` 审过才有安装 |
| T4 | 平台审订 | `/p4/activation-applications` | 通过后产生 subscription/installation | 平台身份；拒绝须有原因 |
| T5 | 已开通可用 | `/p4/installations`（我的已开通） | 列表可见、可发起 | 空态文案「还没有已开通可用的产品」（S10） |
| T6 | （可选）发起/审批 | 安装内发起 → 待办 | 申请 RUNNING→APPROVED | 审批走 `/api/v1/process/tasks/.../approve`（J3 备注） |

## 3. 与「即时订阅」的诚实关系

- 演示默认仍可能走 **即时 subscribe**（无审订）。
- **申请开通 + 平台审订** 是并行产品能力；空库复验应**分别**勾选 T3a 与 T3b，勿把「目录一点即装」当成审订已测。
- 开通记录空态：`还没有开通记录`（TEST-S10 / #637）。

## 4. 本卡判定

| 项 | 判定 |
|---|---|
| 清单完备 | ✅ |
| 本机会话空库重跑 | ❌ 环境阻塞 |
| 引用历史租户路径 | ✅ J3（即时订阅全链路） |
| 审订链路专项空库 | ⏳ 待有 Docker/演示机时补跑 T3b–T4 |

## 5. 修订

- 2026-09-26：VERIFY-T V0.1 初稿。