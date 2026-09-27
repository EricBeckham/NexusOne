<template>
  <main class="login-page" data-product="p2" data-testid="product-login">
    <section class="nx-card login-card" aria-labelledby="login-title">
      <header class="login-brand">
        <h1 id="login-title">星枢 NexusOne</h1>
        <p>登录进入工作台</p>
      </header>
      <div class="login-actions">
        <template v-if="session.accountId">
          <p class="login-session" data-testid="signed-in">你已登录，可以继续进入工作台。</p>
          <router-link class="nx-btn nx-btn--primary" to="/" data-testid="goto-workspace">进入工作台</router-link>
        </template>
        <button v-else-if="idp.configured" class="nx-btn nx-btn--primary" type="button" data-testid="keycloak-login" @click="onKeycloakLogin">
          使用 Keycloak 登录
        </button>
        <p v-else class="login-unconfigured" role="status" data-testid="idp-unconfigured">登录服务暂未配置，请联系管理员。</p>
      </div>
      <div class="login-feedback" aria-live="polite" data-testid="login-feedback">
        <p v-if="error" class="nx-error" role="alert" data-testid="login-error">{{ error }}</p>
      </div>
      <footer class="login-secondary" data-testid="login-secondary">
        <p class="login-register" data-testid="goto-register">还没有租户？<router-link to="/register">提交自助注册申请</router-link></p>
        <router-link v-if="development" class="login-dev-link" to="/login/dev" data-testid="developer-login">开发者登录入口</router-link>
      </footer>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from "vue";
import { keycloakAuthorizeHref, keycloakPublicConfig } from "../auth/keycloakPublic";
import { session } from "../api/session";

const idp = computed(() => keycloakPublicConfig(import.meta.env));
const error = ref("");
const development = import.meta.env.DEV;
function onKeycloakLogin() {
  error.value = "";
  if (!idp.value.configured) return;
  try {
    window.location.assign(keycloakAuthorizeHref({
      issuer: idp.value.issuer,
      clientId: idp.value.clientId,
      redirectUri: `${window.location.origin}/callback`,
      state: "dpo-c1",
    }));
  } catch {
    error.value = "暂时无法打开登录服务，请稍后重试或联系管理员。";
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: var(--nx-space-6);
  background: var(--nx-bg-page);
}
.login-card {
  width: 100%;
  max-width: var(--nx-auth-panel-width);
  padding: var(--nx-space-8);
  display: grid;
  gap: var(--nx-space-6);
}
.login-brand { text-align: center; }
.login-brand h1 {
  margin: var(--nx-space-0);
  font-size: var(--nx-font-size-page-title);
  color: var(--nx-text-primary);
}
.login-brand p {
  margin: var(--nx-space-2) var(--nx-space-0) var(--nx-space-0);
  font-size: var(--nx-font-size-md);
  color: var(--nx-text-secondary);
}
.login-actions { display: grid; gap: var(--nx-space-3); }
.login-unconfigured { margin: var(--nx-space-0); color: var(--nx-text-secondary); text-align: center; }
.login-session { margin: var(--nx-space-0); color: var(--nx-text-regular); text-align: center; }
.login-register { margin: var(--nx-space-0); }
.login-secondary { display: grid; gap: var(--nx-space-3); }
.nx-error { margin: var(--nx-space-0); }
.login-secondary { text-align: center; font-size: var(--nx-font-size-md); }
.login-dev-link { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.login-feedback:empty, .login-secondary:empty { display: none; }
</style>
