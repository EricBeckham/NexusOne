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
      <p v-else class="nx-muted" data-testid="callback-stub-note">演示 IdP 未配置。这条回跳不是 Keycloak 真机验收。</p>
      <NxErrorState
        v-if="failure"
        :title="failure.title"
        :description="failure.description"
        data-testid="callback-failure"
      >
        <template #action>
          <router-link class="primary" to="/login" data-testid="callback-back-login">回登录</router-link>
          <button type="button" data-testid="callback-retry" @click="run">重试</button>
        </template>
      </NxErrorState>
      <NxErrorState
        v-else-if="contextMissing"
        title="缺少 TenantContext"
        description="身份已经回来，但会话里没有租户上下文。请回到登录页重新进入租户。这不是已进入业务页。"
        data-testid="tenant-context-missing"
      >
        <template #action>
          <router-link class="primary" to="/login" data-testid="callback-back-login">回登录</router-link>
          <button type="button" data-testid="callback-retry" @click="run">重试</button>
        </template>
      </NxErrorState>
      <template v-else>
        <p class="nx-muted" data-testid="pkce-callback-state">{{ state ? "已收到 state" : "没有 state" }}</p>
        <p v-if="status" class="nx-muted" data-testid="pkce-callback-status">{{ status }}</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p2 } from "../api/client";
import { applySessionView, autoSelectTenant, rememberTrace, session } from "../api/session";
import { DESKTOP_PKCE_AFTER, completeDesktopPkceCallback } from "../api/pkce-desktop";
import { keycloakPublicConfig } from "../auth/keycloakPublic";
import { callbackFailureCopy, sessionHasTenantContext } from "../auth/callbackHonesty";
import { NxErrorState } from "../components";

const idp = keycloakPublicConfig(import.meta.env);

const route = useRoute();
const router = useRouter();
const status = ref("");
const failure = ref(null);
const contextMissing = ref(false);
const state = ref(String(route.query.state || ""));

async function run() {
  failure.value = null;
  contextMissing.value = false;
  status.value = "";
  if (route.query.error) {
    failure.value = callbackFailureCopy({ queryError: String(route.query.error) });
    return;
  }
  status.value = "正在用授权码换会话…";
  try {
    const result = await completeDesktopPkceCallback({
      code: String(route.query.code || ""),
      state: state.value,
      login: p2.login,
      applySession: applySessionView,
      rememberTrace,
    });
    // AUTH5: several ACTIVE memberships and no context → enter demo_acme (or the first ACTIVE) automatically.
    if (!sessionHasTenantContext(result.view)) {
      await autoSelectTenant();
    }
    if (!session.contextId) {
      status.value = "";
      contextMissing.value = true;
      return;
    }
    const name = result.view?.display_name || result.view?.displayName || "";
    status.value = name ? `已建立租户上下文 · ${name}` : "已建立租户上下文";
    await router.replace({
      path: DESKTOP_PKCE_AFTER,
      query: { trace: result.pending.trace_id },
    });
  } catch (err) {
    status.value = "";
    failure.value = callbackFailureCopy({ message: err?.message });
  }
}

onMounted(run);
</script>
