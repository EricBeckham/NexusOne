<template>
  <div>
    <div class="m-card">
      <h3>登录回跳</h3>
      <p class="m-muted">code / state 只用于换会话，随后从地址栏去掉。PKCE verifier 只从 sessionStorage 取出。Token 不会写入 URL。</p>
      <p class="m-muted" data-testid="mobile-callback-state">state {{ state || "—" }}</p>
      <p v-if="status" class="m-ok" data-testid="mobile-callback-status">{{ status }}</p>
      <p v-if="error" class="m-error" data-testid="mobile-callback-error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { p2 } from "../../../../api/client";
import { persist, session } from "../../../../api/session";
import { DEFAULT_MOBILE_HOME } from "../../../auth/safeReturn";
import { consumePendingPkce } from "../../../auth/pkceSession";

const route = useRoute();
const router = useRouter();
const error = ref("");
const status = ref("正在处理回跳…");
const state = ref(String(route.query.state || ""));

onMounted(async () => {
  const code = String(route.query.code || "");
  const consumed = consumePendingPkce(state.value);
  if (!consumed.ok) {
    error.value = "state 不匹配，已拒绝回跳";
    status.value = "";
    return;
  }
  if (!code) {
    error.value = "缺少 authorization code";
    status.value = "";
    return;
  }
  try {
    const view = await p2.login({
      code,
      redirect_uri: `${window.location.origin}/m/callback`,
      ...(consumed.verifier ? { code_verifier: consumed.verifier } : {}),
    });
    session.accountId = view?.account_id || view?.accountId || session.accountId;
    session.email = view?.email || session.email;
    session.displayName = view?.display_name || view?.displayName || session.displayName;
    session.contextId = view?.context_id || view?.contextId || session.contextId;
    session.tenantId = view?.tenant_id || view?.tenantId || session.tenantId;
    session.personId = view?.person_id || view?.personId || session.personId;
    session.memberships = view?.memberships || session.memberships;
    if (consumed.verifier) {
      session.loginMethod = "pkce";
    }
    persist();
    status.value = consumed.verifier
      ? "PKCE 换票完成，正在回跳（verifier 未进 URL）"
      : session.accessToken
        ? "服务端会话已合并，正在回跳"
        : "服务端已签发上下文；浏览器会话仍持有原 stub token（confidential client 不回写 Access Token）";
    await router.replace(consumed.returnTo || DEFAULT_MOBILE_HOME);
  } catch (err) {
    error.value = `${err.code || "CALLBACK"} ${err.message}`;
    status.value = "";
  }
});
</script>
