<template>
  <div>
    <div class="nx-page-header">
      <h1>管理员恢复码</h1>
      <p>
        TOTP leftover 确认后签发一次性恢复码。这是 leftover 文案与核销路径，不是第二套认证栈，
        不宣称等保 / 密评 / 完整 G17。
      </p>
    </div>
    <NxSourceBanner
      body="本页只签发本页会话恢复码（SHA-256 哈希）。产品门禁仍是 P2 AdminMfaPolicy。Keycloak otp / recovery-authn-codes 走 #118 驱动 leftover。默认 Stub 登录仍带 acr=mfa，不重开 G18。"
    />

    <div class="nx-card reason" data-testid="recovery-reason">
      <h3>{{ RECOVERY_COPY.title }}</h3>
      <p>{{ RECOVERY_COPY.body }}</p>
      <p class="nx-muted">{{ RECOVERY_COPY.hint }}</p>
    </div>

    <div class="nx-card" style="max-width:720px;">
      <div class="nx-row" style="margin-bottom:12px;">
        <button class="primary" type="button" data-testid="recovery-issue" @click="onIssue">
          {{ RECOVERY_COPY.issueCta }}
        </button>
        <router-link class="ghost" to="/p2/mfa/enroll" data-testid="recovery-back-enroll">返回 TOTP 注册</router-link>
        <router-link class="ghost" to="/p2/mfa/email" data-testid="goto-email-mfa">邮箱 MFA leftover</router-link>
      </div>

      <div v-if="session.status === 'issued'" data-testid="recovery-issued">
        <p class="nx-muted">请立刻抄写。刷新后明文不再出现。哈希留在本页，不写 Keycloak。</p>
        <ol class="codes" data-testid="recovery-list">
          <li v-for="code in session.plaintext" :key="code">{{ code }}</li>
        </ol>
        <form @submit.prevent="onConsume">
          <div class="nx-field">
            <label>核销一枚恢复码</label>
            <input
              v-model="code"
              autocomplete="off"
              maxlength="12"
              placeholder="XXXX-XXXX"
              data-testid="recovery-code"
            />
          </div>
          <button class="primary" type="button" data-testid="recovery-consume" @click="onConsume">
            {{ RECOVERY_COPY.verifyCta }}
          </button>
        </form>
      </div>

      <p v-if="error" class="nx-error" data-testid="recovery-error">{{ error }}</p>
      <p v-if="success" class="ok" data-testid="recovery-success">{{ success }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import {
  RECOVERY_COPY,
  consumeRecoveryCode,
  issueRecoveryCodes,
} from "../../auth/recoveryCodes";

const code = ref("");
const error = ref("");
const success = ref("");
const session = ref({ status: "idle" });

function onIssue() {
  error.value = "";
  success.value = "";
  code.value = "";
  const issued = issueRecoveryCodes();
  session.value = issued.session;
}

async function onConsume() {
  const result = await consumeRecoveryCode(session.value, code.value);
  if (!result.ok) {
    error.value = result.message;
    success.value = "";
    return;
  }
  error.value = "";
  session.value = result.session;
  success.value = `已核销 ${result.session.lastUsed}。剩余 ${result.session.remaining.length} 枚。本 leftover 没有把恢复码写入 Keycloak。`;
}
</script>

<style scoped>
.reason {
  margin-bottom: 16px;
  background: var(--nx-warning-light);
}
.codes {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  background: var(--nx-bg-page);
  padding: 12px 12px 12px 32px;
  border-radius: 6px;
}
.ok {
  color: var(--nx-success);
  font-size: 13px;
}
</style>
