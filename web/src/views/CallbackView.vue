<template>
  <div class="page">
    <div class="nx-page-header">
      <h1>登录回调</h1>
      <p data-testid="callback-lead">正在完成登录并建立会话。不会在浏览器里保存客户端密钥。</p>
    </div>
    <div class="nx-card">
      <p v-if="idp.configured" class="nx-muted" data-testid="keycloak-callback-shell">
        Keycloak 授权码回跳。公开 issuer 已配置。
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
        :title="contextMissing.title"
        :description="contextMissing.description"
        :data-kind="contextMissing.kind"
        data-testid="tenant-context-missing"
      >
        <template #action>
          <button
            v-if="contextMissing.canRetry"
            type="button"
            class="primary"
            :disabled="retrying"
            data-testid="callback-retry-enter"
            @click="retryEnter"
          >{{ retrying ? "正在进入…" : "重试" }}</button>
          <router-link
            v-if="contextMissing.canSwitchManually"
            to="/"
            data-testid="callback-switch-manually"
          >去首页手动切换</router-link>
          <router-link :class="{ primary: !contextMissing.canRetry }" to="/login" data-testid="callback-back-login">回登录</router-link>
        </template>
      </NxErrorState>
      <template v-else>
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
import {
  DESKTOP_PKCE_AFTER,
  completeDesktopPkceCallback,
  readDesktopPkcePending,
} from "../api/pkce-desktop";
import { PRODUCT_AUTH_AFTER, completeProductAuthCodeCallback } from "../auth/productAuthCode";
import { keycloakPublicConfig } from "../auth/keycloakPublic";
import { callbackFailureCopy, sessionHasTenantContext, tenantContextMissingCopy } from "../auth/callbackHonesty";
import { isMfaRequiredError } from "../auth/totpEnrollment";
import { NxErrorState } from "../components";

const idp = keycloakPublicConfig(import.meta.env);

const route = useRoute();
const router = useRouter();
const status = ref("");
const failure = ref(null);
const contextMissing = ref(null);
const retrying = ref(false);
const state = ref(String(route.query.state || ""));
// Where to go once a tenant is entered; kept so "重试" only re-attempts the tenant entry (the code is single-use).
const afterEnter = ref({ path: "/", query: {} });

function showContextMissing(view) {
  const memberships = Array.isArray(session.memberships) && session.memberships.length
    ? session.memberships
    : view?.memberships;
  contextMissing.value = tenantContextMissingCopy(memberships);
}

async function retryEnter() {
  if (retrying.value) return;
  retrying.value = true;
  try {
    await autoSelectTenant();
    if (session.contextId) {
      contextMissing.value = null;
      await router.replace(afterEnter.value);
      return;
    }
    showContextMissing(null);
  } finally {
    retrying.value = false;
  }
}

async function run() {
  failure.value = null;
  contextMissing.value = null;
  status.value = "";
  if (route.query.error) {
    failure.value = callbackFailureCopy({ queryError: String(route.query.error) });
    return;
  }
  status.value = "正在用授权码换会话…";
  try {
    const code = String(route.query.code || "");
    const pending = readDesktopPkcePending();
    const usingDesktopPkce = Boolean(pending?.code_verifier);
    const result = usingDesktopPkce
      ? await completeDesktopPkceCallback({
          code,
          state: state.value,
          login: p2.login,
          applySession: applySessionView,
          rememberTrace,
        })
      : await completeProductAuthCodeCallback({
          code,
          redirectUri: `${window.location.origin}/callback`,
          login: p2.login,
          applySession: applySessionView,
          rememberTrace,
        });
    // AUTH5: several ACTIVE memberships and no context → enter demo_acme (or the first ACTIVE) automatically.
    const after = usingDesktopPkce ? DESKTOP_PKCE_AFTER : PRODUCT_AUTH_AFTER;
    const query = result.pending?.trace_id ? { trace: result.pending.trace_id } : {};
    afterEnter.value = { path: after, query };
    if (!sessionHasTenantContext(result.view)) {
      await autoSelectTenant();
    }
    if (!session.contextId) {
      status.value = "";
      showContextMissing(result.view);
      return;
    }
    const name = result.view?.display_name || result.view?.displayName || "";
    status.value = name ? `已进入租户 · ${name}` : "已进入租户";
    await router.replace(afterEnter.value);
  } catch (err) {
    status.value = "";
    if (isMfaRequiredError(err)) {
      await router.replace({ path: "/login", query: { reason: "mfa_required" } });
      return;
    }
    failure.value = callbackFailureCopy({ message: err?.message });
  }
}

onMounted(run);
</script>