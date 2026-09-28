# PLA9 · 组织与权限验收

对照 [PL 规划](PL-平台级与租户级功能重新规划_V0.1.md) 的 PL1，以及 PL4 的角色包落地、PL6 的菜单权限判定。本文只写勾选项，不代表已经在演示机上验收通过。

基线：`main` `6566a4a1`（#808 合入之后）。勾选由环境所有者在**这一 SHA 的已部署构建**上完成。不代勾，不登录演示机，不热修 jar / SPA / 库，不改 `NEXUS_*`，不记录口令、token 或人员主键原值。

审计按 PL0.2 暂缓：下面不把审计事件当作通过条件。`nexus.permission.member-access` 保持默认 `legacy`；切到 `position` 是部署配置，不在本卡、也不在演示机上改。

与本波无关、不作为否决项的已知失败：`SurfaceGuardBypassGateTest`、`XbWaveSchemaContractTest`（仍断言 V709）、`ProductConfigImport*`、`PlatformActivationPersistenceIT`、`G4P1StagingAuditEventsIT`。

## 0. 发布

- [ ] 记录发布 SHA，确认包含下表全部提交。
- [ ] jar 与 SPA 同一 SHA。没有在演示机热修。

| 卡 | 提交 | 要看的结果 |
|---|---|---|
| PLA1 #780 | `4dc2f457` | `PUT /api/v1/permission/positions/{id}/roles` 绑定本级角色 |
| PLA2 #781 | `7e24ba2e` | 岗位页可以绑定角色 |
| PLA3 #795 | `12de24e0` | `GET /api/v1/permission/effective/persons/{id}` 与 `.../positions/{id}` |
| PLA4 #796 | `b607c010` | `/p2/permissions/exceptions` 标注、筛选、撤销例外授权 |
| PLA5 #790 | `c6f98790` | 兼容开关默认 `legacy` |
| PLA6 #784 | `f97043b7` | `GET /api/v1/permission/entries/readable` |
| PLA6b #800 | `6b8eb01c` | 工作室门户按真实可读入口过滤 |
| PLA7 #786 | `cf78cad4` | `POST /api/v1/permission/role-packs/apply` 幂等落地，不绑定岗位 |
| PLA8 #804 | `77ba0dfd` | `nexus.demo.enabled=true` 时经身份与权限接口写入演示组织 |

## 1. 岗位绑定角色（PL1）

- [ ] 租户管理员在岗位页把 `tenant_member` 绑到普通岗位，把 `tenant_process_designer` 绑到租户管理员岗位。保存后重新打开，绑定仍在。
- [ ] 平台岗位只能绑平台角色，租户岗位只能绑租户角色。跨级绑定被拒绝。
- [ ] 改岗位上的角色后，该岗位在职人员的有效权限预览随之变化（人员页或岗位页的预览，来源标为岗位）。

## 2. 有效权限预览（PLA3）

- [ ] 打开一名演示人员的有效权限：能看到最终角色和授权，以及来源（岗位 / 个人例外）。
- [ ] 岗位预览只含岗位本身，不含个人例外授权。
- [ ] 预览与服务端 `GET /api/v1/permission/effective/persons/{id}` 一致，页面不另造一份权限。

## 3. 例外授权（D5，审计暂缓）

- [ ] `/p2/permissions/exceptions` 能按类型和关键字筛选直接挂在个人上的授权。
- [ ] 列表标明「例外」，与岗位授权区分开。
- [ ] 撤销一条个人授权后，该人员的有效权限预览不再包含它。系统维护的条目没有撤销按钮。
- [ ] 不因本卡补做或复验审计事件。

## 4. 兼容开关（PLA5）

- [ ] 部署配置里 `nexus.permission.member-access` 仍是 `legacy` 或未设置。
- [ ] `legacy` 下，已安装入口在没有明确拒绝时仍对成员可见。
- [ ] 不在本卡把开关改成 `position`。

## 5. 可读入口与角色包（PLA6 / PLA7）

- [ ] `GET /api/v1/permission/entries/readable` 只返回当前成员可读的入口键。
- [ ] 工作室门户（PLA6b）用这份结果过滤入口，不再把全部入口都显示出来。
- [ ] 角色包落地创建 `app_{应用}_{角色}`，重复调用不重复建角色，也不自动绑到岗位。

## 6. 演示组织（PLA8，仅演示库）

仅当该环境本来就允许 `nexus.demo.enabled=true` 且数据库在本机回环时勾选。生产环境本项记为不适用，不算失败。

- [ ] 演示租户里有组织、岗位、人员和任职，数据来自启动时的身份接口，不是手写 SQL。
- [ ] 普通岗位绑着 `tenant_member`，租户管理员岗位绑着流程设计角色。
- [ ] 用有效权限预览核对演示管理员与普通成员，结果与岗位绑定一致。

结论：________　执行人：________　日期：________　发布 SHA：________
