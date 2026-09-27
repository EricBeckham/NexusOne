# PV1 · 平台运营 / 租户管理员 / 一般用户：产品可见性与权限规划

基线 `main@82671c26`。本文是设计与决策记录，不代表已部署或已验收；不含 token、口令、person id 原值。

## 1. 现状

- 产品切换器 `web/src/layout/ProductSwitcher.vue` 与首页 `web/src/views/ProductPickerView.vue` 直接遍历 `web/src/nav/products.js` 的固定五项（P1 星域、P2 星权、P3 星构、P4 星市、P5 星联），与身份、岗位、权益无关。一般用户也看到五个产品。
- 服务端拒绝（`no_grant` / `P1.CONTEXT.0001` / `AUTH6.PLATFORM_READ_DENIED`）才是边界；切换器只是导航。
- `TenantMemberGrantSeeder`（产品默认开启）在**每次**绑定签发的 TenantContext 时，把 `tenant_member` 角色挂给当前 person——平台运营、租户管理员、一般用户都会持有它。该角色的授权包括 `process.definition:write`。

## 2. 原则

1. **服务端授权是边界。** 隐藏入口不代替后端拒绝。
2. **可见产品由服务端算、前端只渲染。** 依据：当前 context 是平台租户还是业务租户、平台职责、租户内是否管理员、业务租户权益。
3. **不新造第二套角色存储。** 判定复用现有信号：平台职责（`PlatformDutyCatalog`）、租户管理员岗位（`TENANT_ADMIN` / `demo_tenant_admin`，与 `MarketService`、`AdminPrivilege` 同口径）、P1 权益投影。

## 3. 可见性矩阵（已确认）

| 产品 | 平台运营（平台租户，`platform_system_admin`） | 租户管理员（管理员岗位） | 一般用户 |
|---|---|---|---|
| P1 星域 | ✅ | ❌ | ❌ |
| P2 星权 | ✅ 仅平台租户自身的组织 / 人员 | ✅ 本租户 | ❌ |
| P3 星构 | ❌ | ✅ | ✅ 工作台与日常业务 |
| P4 星市 | ✅ 平台侧 | ✅ 租户侧 | ❌ |
| P5 星联 | ❌ | ✅ **仅当本租户权益开通**（见 §4） | ❌ |

平台租户的其他职责：安全管理员 → P2；审计管理员 → P1（审计中心在 P1 下）。无任何平台职责的平台租户成员 → 无产品。

一般用户只有 P3：登录后直接进 P3 工作台，不显示产品选择页。

## 4. 判定规则（PV2 实现）

```
context 不存在                         → []
context.tenant == 平台租户:
    SYSTEM_ADMIN                        → P1 P2 P4
    SECURITY_ADMIN                      → P2
    AUDIT_ADMIN                         → P1
    （多个职责取并集；无职责 → []）
业务租户:
    管理员岗位（TENANT_ADMIN / demo_tenant_admin，ACTIVE 任职）
                                        → P2 P3 P4，
                                          另加 P5 当权益投影 entitlements.connect.enabled = true
    其他成员                            → P3
```

权益键 `connect`：当前默认权益包只含 `apps`、`custom_ledgers`，**因此部署后所有业务租户的 P5 暂时隐藏**，直到 P4 订阅把 `connect` 写进投影。这是按「P5 只给已开通的租户」执行的结果，不是故障。

## 5. 授权收紧（PV4）

| 授权 | 实际控制 | 结论 |
|---|---|---|
| `form:write` | 业务路径 / H5 提交带表单的申请（`BusinessPathService`、`H5SurfaceGuard`） | 这是「填表提交」，一般用户**保留** |
| `process.definition:write` | 流程定义草稿保存、校验、发布（`ProcessDefinitionSurfaceGuard`） | 设计能力，**从 `tenant_member` 收回**，改给租户管理员 |

做法：

- `tenant_member` 授权清单去掉 `process.definition:write`；seeder 在绑定 context 时把已落库的这条授权软删除（`iam.perm_grant.deleted_at`，已有列，无需 migration）并提升策略版本。
- seeder 发现当前 person 在本租户任管理员岗位时，确保 `tenant_process_designer` 角色存在、持有 `process.definition:write`，并挂给该 person。
- 运行期自动生效，不需要再开 DEMO-U2 窗口。

## 6. 卡片

| 卡 | 内容 |
|---|---|
| PV1 | 本文 |
| PV2 | 后端：`GET /api/v1/auth/products` 按 §4 返回可见产品 |
| PV3 | 前端：切换器 / 产品选择页只渲染可见产品；单产品直达；越界产品 URL 显示无权访问页 |
| PV4 | 后端：按 §5 收紧 `tenant_member`，管理员获得流程设计 |
| PV5 | 验收：三人可见产品、越界拒绝；更新 DEMO-U7 |
