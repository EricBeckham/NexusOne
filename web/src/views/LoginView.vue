<template>
  <div class="page">
    <div class="nx-page-header">
      <h1>会话与登录</h1>
      <p>本机 Stub IdP 或已有 access token。产品路径是 Keycloak 授权码；M0-12 不把 stub 当作正式验收登录。</p>
    </div>
    <div class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <form class="nx-card" @submit.prevent="onStubLogin">
        <h3>Stub 登录（NEXUS_IAM_IDP=stub）</h3>
        <p class="nx-muted">演示 token 带 <code>acr=mfa</code>（不是真实 TOTP）。管理员无 MFA 的产品门禁仍会拒绝签发特权会话。</p>
        <div class="nx-field">
          <label>邮箱</label>
          <input v-model="email" type="email" required />
        </div>
        <div class="nx-field">
          <label>显示名</label>
          <input v-model="name" required />
        </div>
        <button class="primary" type="submit" data-testid="stub-login">生成 token 并登录</button>
      </form>
      <form class="nx-card" @submit.prevent="onTokenLogin">
        <h3>粘贴 access token</h3>
        <div class="nx-field">
          <label>Bearer token</label>
          <textarea v-model="token" rows="5" />
        </div>
        <button class="primary" type="submit">登录</button>
      </form>
    </div>
    <div class="nx-card" style="margin-top:16px;">
      <h3>Authorization Code + PKCE（桌面 leftover）</h3>
      <p class="nx-muted">
        拉取 <code>/api/v1/auth/authorize-url?pkce=s256</code>，浏览器跳到 IdP 登录页，回跳
        <code>/callback</code> 时 POST <code>code</code> + <code>code_verifier</code>。
        不是 H5 <code>/m/login</code>，也不是产品 G4 关闭。默认授权 URL 仍是 confidential client。
      </p>
      <button class="primary" type="button" data-testid="pkce-login" @click="onPkceLogin">
        开始 PKCE 登录
      </button>
    </div>
    <div class="nx-card" style="margin-top:16px;">
      <h3>平台操作头（P1 租户 API）</h3>
      <p class="nx-muted">仅当 <code>NEXUS_P1_DEMO_CONTEXT_ENABLED=true</code>。生产入口必须剥掉客户端身份头。</p>
      <div class="nx-field">
        <label>X-Nexus-Demo-Actor-Id</label>
        <input v-model="session.demoActorId" @change="persist" />
      </div>
      <div class="nx-field">
        <label>X-Nexus-Demo-Reason</label>
        <input v-model="session.demoReason" @change="persist" />
      </div>
      <div class="nx-field">
        <label>X-Nexus-Bootstrap-Token（P2 管理员初始化）</label>
        <input v-model="session.bootstrapToken" type="password" autocomplete="off" @change="persist" />
      </div>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="session.accountId" class="nx-muted">
      已登录 {{ session.displayName }} / {{ session.email }}
      · context {{ session.contextId || "（无，需租户切换）" }}
    </p>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { p2, stubAccessToken } from "../api/client";
import { DESKTOP_PKCE_TRACE, startDesktopPkceLogin } from "../api/pkce-desktop";
import { loginWithAccessToken, persist, rememberTrace, session } from "../api/session";

const router = useRouter();
const email = ref("admin-a@example.com");
const name = ref("Admin A");
const token = ref("");
const error = ref("");

async function onStubLogin() {
  error.value = "";
  const access = stubAccessToken({
    sub: `local-${email.value}`,
    email: email.value,
    username: email.value,
    name: name.value,
  });
  token.value = access;
  await submit(access);
}

async function onTokenLogin() {
  await submit(token.value.trim());
}

async function onPkceLogin() {
  error.value = "";
  try {
    rememberTrace(DESKTOP_PKCE_TRACE);
    await startDesktopPkceLogin({
      authorizeUrl: p2.authorizeUrl,
      assignLocation: (url) => window.location.assign(url),
      origin: window.location.origin,
      traceId: DESKTOP_PKCE_TRACE,
    });
  } catch (err) {
    error.value = `${err.code || "PKCE"} ${err.message}`;
  }
}

async function submit(access) {
  try {
    await loginWithAccessToken(access);
    router.push("/");
  } catch (err) {
    error.value = `${err.code || "LOGIN"} ${err.message}`;
  }
}
</script>

<style scoped>
.primary {
  background: var(--nx-product);
  color: #fff;
  border: 0;
  border-radius: 6px;
  padding: 8px 14px;
  cursor: pointer;
}
</style>
