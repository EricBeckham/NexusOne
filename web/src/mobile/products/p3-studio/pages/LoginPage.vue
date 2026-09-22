<template>
  <div>
    <div class="m-hero">
      <h2>登录回跳</h2>
      <p>移动浏览器首个宿主。登录后回到安全 Return URL，不把 Token 或 tenant_id 写入地址栏。</p>
    </div>
    <div class="m-card">
      <h3>回跳目标</h3>
      <p class="m-muted" data-testid="mobile-return-url">{{ returnTo }}</p>
      <p v-if="unsafeAttempt" class="m-error">已丢弃不安全的 return（外链 / Token / tenant_id）。</p>
    </div>
    <form class="m-card" @submit.prevent="onStubLogin">
      <h3>Stub 登录</h3>
      <p class="m-muted">与桌面同一 stub.&lt;base64&gt; 格式。不是 Keycloak PKCE 验收。</p>
      <div class="m-field">
        <label>邮箱</label>
        <input v-model="email" type="email" required autocomplete="username" data-testid="mobile-email" />
      </div>
      <div class="m-field">
        <label>显示名</label>
        <input v-model="name" required data-testid="mobile-name" />
      </div>
      <button class="m-btn primary" type="submit" :disabled="busy" data-testid="mobile-stub-login">
        {{ busy ? "登录中…" : "登录并回跳" }}
      </button>
    </form>
    <div class="m-card">
      <h3>授权码回跳契约</h3>
      <p class="m-muted">
        拉取 <code>/api/v1/auth/authorize-url</code>，把 code/state 落到
        <code>/m/callback</code>。Confidential client 不把 Access Token 回写 SPA。
      </p>
      <button class="m-btn ghost" type="button" :disabled="busy" data-testid="mobile-authorize" @click="onAuthorize">
        开始授权回跳
      </button>
    </div>
    <p v-if="error" class="m-error" data-testid="mobile-login-error">{{ error }}</p>
    <p v-if="session.accountId" class="m-ok">
      已登录 {{ session.displayName }} · context {{ session.contextId || "（待签发）" }}
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { stubAccessToken } from "../../../../api/client";
import { p2 } from "../../../../api/client";
import { loginWithAccessToken, persist, session } from "../../../../api/session";
import { isSafeMobileReturn, savePendingRedirect, safeMobileReturn } from "../../../auth/safeReturn";
import { postHost } from "../../../host/hostBridge";

const route = useRoute();
const router = useRouter();
const email = ref("h5-admin@example.com");
const name = ref("H5 管理员");
const busy = ref(false);
const error = ref("");

const rawReturn = computed(() => route.query.return);
const returnTo = computed(() => safeMobileReturn(rawReturn.value));
const unsafeAttempt = computed(() => Boolean(rawReturn.value) && !isSafeMobileReturn(String(rawReturn.value)));

onMounted(() => {
  postHost("login", { action: "request", returnTo: returnTo.value });
});

async function onStubLogin() {
  error.value = "";
  busy.value = true;
  try {
    const access = stubAccessToken({
      sub: `local-${email.value}`,
      email: email.value,
      username: email.value,
      name: name.value,
    });
    session.accessToken = access;
    session.email = email.value;
    session.displayName = name.value;
    persist();
    try {
      await loginWithAccessToken(access);
    } catch (err) {
      // Token + display name already persisted. Issued context needs the JAR.
      error.value = `${err.code || "LOGIN"} ${err.message} · 浏览器已持有 stub token`;
    }
    postHost("login", { action: "done" });
    await router.replace(returnTo.value);
  } catch (err) {
    error.value = `${err.code || "LOGIN"} ${err.message}`;
  } finally {
    busy.value = false;
  }
}

async function onAuthorize() {
  error.value = "";
  busy.value = true;
  try {
    const body = await p2.authorizeUrl();
    const state = body.state || "";
    savePendingRedirect({ state, returnTo: returnTo.value });
    const target = new URL(body.url, window.location.origin);
    const code = target.searchParams.get("code");
    if (code) {
      await router.replace({
        path: "/m/callback",
        query: { code, state: target.searchParams.get("state") || state },
      });
      return;
    }
    window.location.assign(body.url);
  } catch (err) {
    error.value = `${err.code || "AUTHORIZE"} ${err.message}`;
  } finally {
    busy.value = false;
  }
}
</script>
