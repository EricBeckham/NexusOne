import { keycloakPublicConfig } from "../auth/keycloakPublic";

/** Fixed read-only demo click path. Links only; this module does not call APIs. */
export const CLICK_PATH_STEPS = [
  { id: "login", title: "登录", to: "/login", note: "打开登录页。本指南不代登录。" },
  { id: "catalog", title: "目录", to: "/p4/market", note: "打开应用目录。本指南不请求目录接口。" },
  { id: "activate", title: "申请开通", to: "/p4/market", note: "在目录里选择 SKU 后点「申请开通」。本指南不代点。" },
  { id: "submit", title: "提交", to: "/p4/market", note: "提交发生在目录页。本指南不调用申请接口。" },
  { id: "approve", title: "审订", to: "/p4/activation-applications", note: "平台在开通申请审订页处理。本指南不代审。" },
  { id: "result", title: "结果", to: "/p4/installations", note: "通过后到「我的已开通」看安装结果。本指南不写结果。" },
];

export const CLICK_PATH_DOCS = [
  { id: "m7", label: "M7 验收清单（仓库文档，本页不勾选）", path: "docs/planning/M7-Keycloak演示验收清单.md" },
  { id: "runbook", label: "真机启动 runbook（仓库文档）", path: "docs/engineering/runbooks/demo-host-realhost-launch.md" },
];

export function clickPathMode(env = import.meta.env) {
  const configured = keycloakPublicConfig(env).configured;
  if (configured) {
    return {
      id: "keycloak",
      label: "Keycloak",
      note: "公开 issuer 已配置。标签只说明入口，不宣称真机已验收。",
    };
  }
  return {
    id: "stub",
    label: "本地 stub",
    note: "演示 IdP 未配置。当前走本地 stub，不是 Keycloak 验收。",
  };
}
