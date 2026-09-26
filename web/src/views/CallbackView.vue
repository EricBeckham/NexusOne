<template>
  <div class="page">
    <div class="nx-page-header">
      <h1>授权码回跳</h1>
      <p>
        桌面 Authorization Code + PKCE leftover。用 <code>code</code> + 本地
        <code>code_verifier</code> 换会话，随后从地址栏去掉。不是 H5
        <code>/m/callback</code>，也不是产品 G4 关闭。
      </p>
    </div>
    <div class="nx-card">
      <p v-if="idp.configured" class="nx-muted" data-testid="keycloak-callback-shell">
        Keycloak 授权码回跳壳。公开 issuer 已配置；不宣称真机已验收。
      </p>
      <p class="nx-muted" data-testid="pkce-callback-state">state {{ state || "—" }}</p>
      <p v-if="status" class="nx-muted" data-testid="pkce-callback-status">{{ status }}</p>
      <p v-if="error" class="nx-error" data-testid="pkce-callback-error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p2 } from "../api/client";
import { applySessionView, rememberTrace } from "../api/session";
import { DESKTOP_PKCE_AFTER, completeDesktopPkceCallback } from "../api/pkce-desktop";
import { keycloakPublicConfig } from "../auth/keycloakPublic";

const idp = keycloakPublicConfig(import.meta.env);

const route = useRoute();
const router = useRouter();
const error = ref("");
const status = ref("正在用 code + verifier 换会话…");
const state = ref(String(route.query.state || ""));

onMounted(async () => {
  try {
    const result = await completeDesktopPkceCallback({
      code: String(route.query.code || ""),
      state: state.value,
      login: p2.login,
      applySession: applySessionView,
      rememberTrace,
    });
    status.value = `已登录 ${result.view?.display_name || result.view?.displayName || ""} · LOGIN 已上链`;
    await router.replace({
      path: DESKTOP_PKCE_AFTER,
      query: { trace: result.pending.trace_id },
    });
  } catch (err) {
    error.value = `${err.code || "CALLBACK"} ${err.message}`;
    status.value = "";
  }
});
</script>
