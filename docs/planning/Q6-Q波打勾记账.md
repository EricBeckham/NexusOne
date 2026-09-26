# Q6 Q 波打勾记账

日期：2026-09-26。基线：`origin/main@271e1873`（含 Q5 #494）。Docs-only。

## 结论

Q 波 **Cursor 共享基建已全部合入**（Q1–Q5）；Claude 页卡 **大部已合**（Q7 / Q9 / Q10 / Q11）。**Q8**（已开通管理页接 Q5 页眉）在收口时仍为本地 WIP 分支 `claude/q8-installed-management`，未见 PR 合入。主路径截图目录与文件名已约定；本卡未提交 PNG（本机未做浏览器截图像素验收）。

不宣称：外网、真 Keycloak、`prod`、H5、自由画布、分支/并行 BPM。

## 合入对照

| 卡 | 状态 | 链接 |
|---|---|---|
| Q1 | merged | https://github.com/EricBeckham/NexusOne/pull/485 |
| Q2 | merged | https://github.com/EricBeckham/NexusOne/pull/487 |
| Q3 | merged | https://github.com/EricBeckham/NexusOne/pull/488 |
| Q4 | merged | https://github.com/EricBeckham/NexusOne/pull/491 |
| Q5 | merged | https://github.com/EricBeckham/NexusOne/pull/494 |
| Q6 | this | 本文件 |
| Q7 | merged | https://github.com/EricBeckham/NexusOne/pull/486 |
| Q8 | pending | 分支 `claude/q8-installed-management`（WIP；待接 Q5 页眉后开 PR） |
| Q9 | merged | https://github.com/EricBeckham/NexusOne/pull/489 |
| Q10 | merged | https://github.com/EricBeckham/NexusOne/pull/490 |
| Q11 | merged | https://github.com/EricBeckham/NexusOne/pull/492 |

任务卡原文：`docs/planning/WEB租户主路径视觉任务卡_Q波_V0.1.md`（#484）。

## 主路径截图路径

目录：`docs/planning/screenshots/q-wave/`（见同目录 `README.md`）。

| 文件 | 页面 |
|---|---|
| `01-market-catalog.png` | 星市应用目录（Q7） |
| `02-installations.png` | 我的已开通（Q8，合入后补拍） |
| `03-product-start.png` | 发起申请（Q5 页眉 crumb + Q9） |
| `04-todo-inbox.png` | 待办箱 · 我的待办（Q3/Q4/Q10） |
| `05-my-requests.png` | 我的申请 Tab（Q11） |
| `06-initiated.png` | 我发起 Tab（Q11） |

## 建议下一波

NexusOne 开 **R 波**（平台配置台页）时：先等 **Q8** 合入或在 R 卡前置中注明未合项；截图可在 demo 空库补拍后追加同一目录。
