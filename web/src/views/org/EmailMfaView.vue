<template>
  <div>
    <div class="nx-page-header">
      <h1>管理员邮箱 MFA</h1>
      <p>
        请求契约 leftover。未启用 SMTP 或没有投递主机时诚实 SKIP，不发送邮件，
        不宣称等保 / 密评 / 完整 G17。
      </p>
    </div>
    <NxSourceBanner
      body="产品 SMTP 仍是邀请 / 重置口令。本页不实现第二套邮件栈，不写回 Keycloak otp。默认 Stub 登录仍带 acr=mfa，不重开 G18。"
    />

    <div class="nx-card reason" data-testid="email-mfa-reason">
      <h3>{{ EMAIL_MFA_COPY.title }}</h3>
      <p>{{ EMAIL_MFA_COPY.body }}</p>
      <p class="nx-muted">{{ EMAIL_MFA_COPY.hint }}</p>
    </div>

    <div class="nx-card" style="max-width:720px;">
      <form @submit.prevent="onRequest">
        <div class="nx-field">
          <label>管理员邮箱</label>
          <input v-model="email" type="email" data-testid="email-mfa-address" />
        </div>
        <div class="nx-row">
          <button class="primary" type="button" data-testid="email-mfa-request" @click="onRequest">
            {{ EMAIL_MFA_COPY.cta }}
          </button>
          <router-link class="ghost" to="/p2/mfa/recovery" data-testid="goto-recovery-codes">恢复码 leftover</router-link>
          <router-link class="ghost" to="/p2/mfa/enroll" data-testid="email-back-enroll">返回 TOTP 注册</router-link>
        </div>
      </form>
      <p v-if="error" class="nx-error" data-testid="email-mfa-error">{{ error }}</p>
      <p v-if="skipped" class="skip" data-testid="email-mfa-skip">{{ skipped }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRoute } from "vue-router";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { EMAIL_MFA_COPY, requestEmailOtp } from "../../auth/emailMfa";

const route = useRoute();
const email = ref(route.query.account || "admin-a@example.com");
const error = ref("");
const skipped = ref("");

function onRequest() {
  const result = requestEmailOtp(email.value, {
    smtpEnabled: route.query.smtp === "1",
    smtpHost: String(route.query.host || ""),
    keycloakReachable: route.query.kc === "1",
  });
  if (result.skip) {
    error.value = "";
    skipped.value = `SKIP ${result.code}: ${result.message}`;
    return;
  }
  skipped.value = "";
  error.value = result.message;
}
</script>

<style scoped>
.reason {
  margin-bottom: 16px;
  background: var(--nx-warning-light);
}
.skip {
  color: var(--nx-text-regular);
  font-size: 13px;
}
</style>
