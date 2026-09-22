<template>
  <div class="page">
    <div class="nx-page-header">
      <h1>会话与登录</h1>
      <p>本机 Stub IdP 或已有 access token。产品路径是 Keycloak 授权码；M0-12 不把 stub 当作正式验收登录。</p>
    </div>
    <div class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <form class="nx-card" @submit.prevent="onStubLogin">
        <h3>Stub 登录（NEXUS_IAM_IDP=stub）</h3>
        <div class="nx-field">
          <label>邮箱</label>
          <input v-model="email" type="email" required />
        </div>
        <div class="nx-field">
          <label>显示名</label>
          <input v-model="name" required />
        </div>
        <button class="primary" type="submit">生成 token 并登录</button>
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
import { stubAccessToken } from "../api/client";
import { loginWithAccessToken, persist, session } from "../api/session";

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
