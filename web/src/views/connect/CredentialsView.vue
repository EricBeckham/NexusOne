<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · 自用凭证"
      title="凭证"
      description="自用凭证元数据。密钥只写入密钥库，列表和详情不回显。"
    >
      <template v-if="isDetail" #actions>
        <router-link class="ghost" to="/p5/credentials">返回列表</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />

    <template v-else-if="isDetail">
      <NxEmpty v-if="loaded && !credential" title="暂无" description="接口没有返回这个凭证。" />
      <template v-else-if="credential">
        <section class="nx-card detail">
          <h2>{{ show(credential.name) }}</h2>
          <dl>
            <div><dt>提供方</dt><dd>{{ show(credential.provider) }}</dd></div>
            <div><dt>引用</dt><dd>{{ show(credential.secretName) }}</dd></div>
            <div><dt>最近轮转</dt><dd>{{ formatTime(credential.lastRotatedAt) }}</dd></div>
            <div><dt>到期</dt><dd>{{ formatTime(credential.expiresAt) }}</dd></div>
            <div><dt>状态</dt><dd><NxStatusTag v-if="credential.status" :status="credential.status" :label="statusLabel(credential.status)" /><template v-else>暂无</template></dd></div>
          </dl>
        </section>

        <section class="nx-card secret-form">
          <h2>{{ credential.status === "REVOKED" ? "已吊销" : secretConfigured ? "轮转" : "写入密钥" }}</h2>
          <p class="hint">测试只确认密钥库还能读出当前值，不访问外部系统。提交后输入框会清空，页面不保留密钥。</p>
          <p v-if="actionMessage" class="hint" role="status">{{ actionMessage }}</p>
          <p v-if="formError" class="nx-error">{{ formError }}</p>
          <template v-if="credential.status !== 'REVOKED'">
            <form @submit.prevent="submitSecret">
              <div class="nx-field">
                <label>{{ secretConfigured ? "新密钥" : "密钥" }}</label>
                <input ref="secretInput" v-model="secretValue" type="password" autocomplete="new-password" />
              </div>
              <button class="primary" type="submit" :disabled="busy">{{ secretConfigured ? "轮转" : "写入密钥" }}</button>
            </form>
            <div class="actions">
              <button class="secondary" type="button" :disabled="busy" @click="probe">测试</button>
              <button class="danger" type="button" :disabled="busy" @click="revoke">吊销</button>
            </div>
          </template>
          <button v-else class="secondary" type="button" :disabled="busy" @click="probe">测试</button>
        </section>
      </template>
    </template>

    <template v-else>
      <NxEmpty v-if="rows && !rows.length" title="暂无" description="当前租户还没有凭证。" />
      <table v-else-if="listed.length" class="nx-table">
        <thead>
          <tr><th>名称</th><th>提供方</th><th>引用</th><th>最近轮转</th><th>到期</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="row in listed" :key="row.id || row.name">
            <td>{{ show(row.name) }}</td>
            <td>{{ show(row.provider) }}</td>
            <td>{{ show(row.secretName) }}</td>
            <td>{{ formatTime(row.lastRotatedAt) }}</td>
            <td>{{ formatTime(row.expiresAt) }}</td>
            <td><NxStatusTag v-if="row.status" :status="row.status" :label="statusLabel(row.status)" /><template v-else>暂无</template></td>
            <td><router-link v-if="row.id" :to="`/p5/credentials/${row.id}`">查看</router-link><template v-else>暂无</template></td>
          </tr>
        </tbody>
      </table>

      <section class="nx-card create">
        <h2>新建</h2>
        <div class="nx-field"><label>名称</label><input v-model="name" maxlength="160" /></div>
        <div class="nx-field"><label>提供方</label><input v-model="provider" maxlength="100" /></div>
        <div class="nx-field"><label>到期时间</label><input v-model="expires" placeholder="留空表示暂无" /></div>
        <p v-if="formError" class="nx-error">{{ formError }}</p>
        <button class="primary" type="button" :disabled="busy" @click="create">保存元数据</button>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";
import { asRows, credentialFields, show } from "./connectDisplay";

const route = useRoute();
const rows = ref(null);
const detail = ref(null);
const secretStatus = ref(null);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");
const formError = ref("");
const actionMessage = ref("");
const busy = ref(false);
const name = ref("");
const provider = ref("");
const expires = ref("");
const secretValue = ref("");
const secretInput = ref(null);

const isDetail = computed(() => route.name === "p5-credential");
const listed = computed(() => (rows.value || []).map(credentialFields).filter(Boolean));
const credential = computed(() => credentialFields(detail.value));
const secretConfigured = computed(() => secretStatus.value?.configured === true);

function statusLabel(status) {
  if (status === "ACTIVE") return "有效";
  if (status === "REVOKED") return "已吊销";
  return status;
}

function formatTime(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : date.toLocaleString("zh-CN", { hour12: false });
}

function safeError(err) {
  const message = err?.message || "操作失败";
  if (secretValue.value && message.includes(secretValue.value)) {
    return "密钥操作失败";
  }
  return message;
}

function clearSecretInput() {
  secretValue.value = "";
  if (secretInput.value) {
    secretInput.value.value = "";
  }
}

async function load(options = {}) {
  error.value = "";
  traceId.value = "";
  formError.value = "";
  if (!options.keepMessage) {
    actionMessage.value = "";
  }
  loaded.value = false;
  if (isDetail.value) {
    rows.value = null;
    try {
      detail.value = await p5.credential(route.params.id);
      try {
        secretStatus.value = await p5.credentialSecretStatus(route.params.id);
      } catch {
        secretStatus.value = null;
      }
    } catch (err) {
      detail.value = null;
      secretStatus.value = null;
      error.value = err.message || "加载失败";
      traceId.value = err.traceId || "";
    } finally {
      loaded.value = true;
    }
    return;
  }
  detail.value = null;
  secretStatus.value = null;
  try {
    const body = await p5.credentials();
    rows.value = asRows(body);
    if (!rows.value) {
      error.value = "接口没有返回凭证列表";
    }
  } catch (err) {
    rows.value = null;
    error.value = err.message || "加载失败";
    traceId.value = err.traceId || "";
  } finally {
    loaded.value = true;
  }
}

async function create() {
  formError.value = "";
  if (!name.value.trim() || !provider.value.trim()) {
    formError.value = "请填写名称和提供方";
    return;
  }
  const body = { name: name.value.trim(), provider: provider.value.trim() };
  if (expires.value.trim()) {
    const parsed = new Date(expires.value.trim());
    if (Number.isNaN(parsed.getTime())) {
      formError.value = "到期时间无法识别";
      return;
    }
    body.expires_at = parsed.toISOString();
  }
  busy.value = true;
  try {
    await p5.createCredential(body);
    name.value = "";
    provider.value = "";
    expires.value = "";
  } catch (err) {
    formError.value = err.message || "保存失败";
    return;
  } finally {
    busy.value = false;
  }
  await load();
}

async function submitSecret() {
  formError.value = "";
  actionMessage.value = "";
  const value = secretValue.value;
  if (!value) {
    formError.value = "请填写密钥";
    return;
  }
  const id = credential.value?.id;
  busy.value = true;
  try {
    if (secretConfigured.value) {
      await p5.rotateCredentialSecret(id, value);
      actionMessage.value = "已提交轮转。";
    } else {
      await p5.writeCredentialSecret(id, value);
      actionMessage.value = "密钥已写入。";
    }
  } catch (err) {
    formError.value = safeError(err);
  } finally {
    clearSecretInput();
    busy.value = false;
  }
  if (!formError.value) {
    await load({ keepMessage: true });
  }
}

async function probe() {
  formError.value = "";
  actionMessage.value = "";
  busy.value = true;
  try {
    const result = await p5.probeCredentialSecret(credential.value.id);
    actionMessage.value = result?.status === "SUCCEEDED" ? "密钥可读" : "密钥不可读";
  } catch (err) {
    formError.value = safeError(err);
  } finally {
    busy.value = false;
  }
}

async function revoke() {
  if (!globalThis.confirm("确定吊销这个凭证的密钥吗？吊销后旧值不能再读取。")) {
    return;
  }
  formError.value = "";
  actionMessage.value = "";
  busy.value = true;
  try {
    await p5.revokeCredentialSecret(credential.value.id);
    actionMessage.value = "已吊销。";
  } catch (err) {
    formError.value = safeError(err);
    return;
  } finally {
    busy.value = false;
  }
  await load({ keepMessage: true });
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.detail, .create, .secret-form { margin-top: var(--nx-space-4); }
.detail h2, .create h2, .secret-form h2 { margin-top: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-3); margin: 0; }
dl div { display: grid; grid-template-columns: 120px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
.hint { color: var(--nx-text-secondary); font-size: 13px; }
.actions { display: flex; gap: 8px; margin-top: 12px; }
.danger { color: #b42318; }
</style>
