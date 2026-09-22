<template>
  <div>
    <div class="nx-page-header">
      <h1>管理员 TOTP 注册</h1>
      <p>
        用验证器应用绑定 6 位动态码。这是注册界面与错误路径 leftover，不是第二套认证栈，
        不宣称等保 / 密评 / 完整 G17。
      </p>
    </div>
    <NxSourceBanner
      body="本页只改善注册文案与错误路径。产品门禁仍是 P2 AdminMfaPolicy。Keycloak otp 凭据走 #118 HTTP 走通。默认 Stub 登录仍带 acr=mfa，不重开 G18。"
    />

    <div class="nx-card writeback" data-testid="totp-writeback-panel">
      <h3>Keycloak otp 写回桥（诚实 leftover）</h3>
      <p data-testid="totp-writeback-status">{{ writebackStatus }}</p>
      <p class="nx-muted" data-testid="totp-writeback-reason">{{ writeback.reason || "vue_confirm_is_local_only" }}</p>
      <p>
        本页 confirm 成功 ≠ 写入 Keycloak otp。活 IdP 不可用或没有产品写回接口时诚实 SKIP。
        不宣称等保 / 密评 / 完整 G17。恢复码 / 邮箱 MFA 不在本 leftover。
      </p>
    </div>

    <div v-if="reason === 'mfa_required'" class="nx-card reason" data-testid="enroll-mfa-reason">
      <h3>{{ MFA_REQUIRED_COPY.title }}</h3>
      <p>{{ MFA_REQUIRED_COPY.body }}</p>
      <p class="nx-muted">{{ MFA_REQUIRED_COPY.hint }}</p>
    </div>

    <ol class="steps nx-card">
      <li>打开 Google Authenticator、微软 Authenticator 或其它 TOTP 应用。</li>
      <li>手工输入下方密钥，或粘贴 otpauth URI。本页不生成可扫描二维码库依赖。</li>
      <li>输入验证器当前 6 位数字确认绑定。错误码会说明格式、过期、已注册或密钥过期。</li>
    </ol>

    <div class="nx-card" style="max-width:720px;">
      <div class="nx-row" style="margin-bottom:12px;">
        <button class="primary" type="button" data-testid="totp-start" @click="onStart">
          {{ session.status === "pending" ? "重新生成密钥" : "开始注册" }}
        </button>
        <router-link class="ghost" to="/login" data-testid="totp-back-login">返回登录</router-link>
      </div>

      <div v-if="session.status === 'pending' || session.status === 'enrolled'" data-testid="totp-pending">
        <div class="nx-field">
          <label>手工密钥（每 4 位一组）</label>
          <p class="nx-code secret" data-testid="totp-secret">{{ session.secretGrouped }}</p>
        </div>
        <div class="nx-field">
          <label>otpauth URI</label>
          <p class="nx-code secret" data-testid="totp-otpauth">{{ session.otpauth }}</p>
        </div>
        <div class="nx-row" style="margin-bottom:12px;">
          <button class="ghost" type="button" data-testid="totp-copy-secret" @click="copy(session.secret)">
            复制密钥
          </button>
          <button class="ghost" type="button" data-testid="totp-copy-otpauth" @click="copy(session.otpauth)">
            复制 otpauth
          </button>
        </div>
        <form @submit.prevent="onConfirm">
          <div class="nx-field">
            <label>6 位动态码</label>
            <input
              v-model="code"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="8"
              placeholder="123456"
              data-testid="totp-code"
            />
          </div>
          <button class="primary" type="button" data-testid="totp-confirm" @click="onConfirm">
            确认绑定
          </button>
        </form>
      </div>

      <p v-if="copied" class="nx-muted" data-testid="totp-copied">{{ copied }}</p>
      <p v-if="error" class="nx-error" data-testid="totp-error">{{ error }}</p>
      <p v-if="session.status === 'enrolled'" class="ok" data-testid="totp-success">
        本页确认成功。产品特权会话仍要求 access token 带 <code>acr=mfa</code> / <code>amr=otp</code>。
        本 leftover 没有把 otp 凭据写入 Keycloak。恢复码 / 邮箱 MFA 是 sibling leftover。
      </p>
      <div v-if="session.status === 'enrolled'" class="nx-row" style="margin-top:12px;">
        <router-link class="ghost" to="/p2/mfa/recovery" data-testid="goto-recovery-codes">
          去签发本页恢复码
        </router-link>
        <router-link class="ghost" to="/p2/mfa/email" data-testid="goto-email-mfa">
          邮箱 MFA leftover
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { MFA_REQUIRED_COPY, confirmEnrollment, startEnrollment } from "../../auth/totpEnrollment";
import { attemptVueOtpWriteback, formatWritebackStatus } from "../../auth/totpWriteback";

const route = useRoute();
const reason = computed(() => String(route.query.reason || ""));
const code = ref("");
const error = ref("");
const copied = ref("");
const session = ref({ status: "idle" });
const writeback = ref({
  result: "SKIP",
  reason: "vue_confirm_is_local_only",
  vueConfirmWritesKeycloak: false,
  otpCredentialStored: false,
});
const writebackStatus = computed(() => formatWritebackStatus(writeback.value));

function onStart() {
  error.value = "";
  copied.value = "";
  code.value = "";
  const started = startEnrollment({
    account: route.query.account || "admin-a@example.com",
    secret: route.query.secret || undefined,
  });
  if (!started.ok) {
    error.value = started.message;
    return;
  }
  session.value = started.session;
}

async function onConfirm() {
  const result = confirmEnrollment(session.value, code.value);
  if (!result.ok) {
    error.value = result.message;
    return;
  }
  error.value = "";
  session.value = result.session;
  writeback.value = await attemptVueOtpWriteback({ session: result.session });
}

async function copy(text) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    }
    copied.value = "已复制到剪贴板";
  } catch {
    copied.value = "复制失败，请手工选中文本";
  }
}
</script>

<style scoped>
.steps {
  margin: 0 0 16px;
  padding: 16px 16px 16px 36px;
}
.reason {
  margin-bottom: 16px;
  background: var(--nx-warning-light);
}
.secret {
  word-break: break-all;
  background: var(--nx-bg-page);
  padding: 8px 10px;
  border-radius: 6px;
}
.ok {
  color: var(--nx-success);
  font-size: 13px;
}
.writeback {
  margin-bottom: 16px;
}
</style>
