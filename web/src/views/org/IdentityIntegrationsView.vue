<template>
  <NxPageHeader eyebrow="星权 · 身份集成" title="身份集成" description="管理当前租户的 OIDC 身份提供方。连接测试结果和同步统计仅来自服务端返回。">
    <template #actions>
      <button class="button secondary" type="button" :disabled="loading" @click="load">{{ loading ? "刷新中…" : "刷新" }}</button>
      <button class="button primary" type="button" @click="openCreate">添加身份提供方</button>
    </template>
  </NxPageHeader>

  <NxPanelCard title="身份提供方" eyebrow="OIDC" description="当前接口支持配置与连接测试，不提供手动同步操作。">
    <NxErrorState v-if="error" :title="error" :trace-id="traceId" @retry="load" />
    <p v-else-if="loading && !loaded" class="state-note" role="status">正在加载身份提供方…</p>
    <NxEmpty v-else-if="loaded && !providers.length" title="暂无身份提供方" description="添加 OIDC 身份提供方后，可写入密钥并测试连接。" />
    <div v-else class="provider-list" aria-live="polite">
      <article v-for="provider in providers" :key="provider.id" class="provider-card">
        <header class="provider-heading">
          <div class="provider-mark" aria-hidden="true">ID</div>
          <div class="provider-title">
            <span class="eyebrow">{{ provider.providerType || "OIDC" }}</span>
            <h2>{{ provider.displayName }}</h2>
            <p>{{ provider.issuer }}</p>
          </div>
          <span class="status-pill" :data-status="provider.status">{{ provider.status === "ENABLED" ? "已启用" : "已停用" }}</span>
        </header>

        <dl class="provider-facts">
          <div><dt>客户端 ID</dt><dd>{{ provider.clientId || "暂无" }}</dd></div>
          <div><dt>密钥状态</dt><dd>{{ secretLabel(provider.id) }}</dd></div>
          <div><dt>最近测试</dt><dd>{{ formatTime(provider.lastSyncAt) }}</dd></div>
          <div><dt>最近测试结果</dt><dd>{{ testLabel(provider.lastSyncOk) }}</dd></div>
          <div><dt>连接失败次数</dt><dd>{{ countLabel(provider.syncFailCount) }}</dd></div>
        </dl>

        <p v-if="actionMessages[provider.id]" class="action-message" role="status">{{ actionMessages[provider.id] }}</p>
        <footer class="provider-actions">
          <button class="button secondary" type="button" @click="openEdit(provider)">配置</button>
          <button class="button secondary" type="button" :disabled="!secretConfigured(provider.id) || busyId === provider.id" @click="openSecret(provider)">{{ secretConfigured(provider.id) ? "轮转密钥" : "写入密钥" }}</button>
          <button class="button secondary" type="button" :disabled="!secretConfigured(provider.id) || busyId === provider.id" @click="testConnection(provider)">测试连接</button>
          <button class="button secondary" type="button" :disabled="busyId === provider.id" @click="toggleStatus(provider)">{{ provider.status === "ENABLED" ? "停用" : "启用" }}</button>
          <button class="button danger" type="button" :disabled="busyId === provider.id" @click="removeProvider(provider)">删除</button>
        </footer>
      </article>
    </div>
  </NxPanelCard>

  <div v-if="providerDialog" class="dialog-backdrop" @click.self="closeProviderDialog">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="provider-dialog-title">
      <header class="dialog-header"><div><span class="eyebrow">OIDC 提供方</span><h2 id="provider-dialog-title">{{ editingId ? "配置身份提供方" : "添加身份提供方" }}</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="closeProviderDialog">×</button></header>
      <form class="form-grid" @submit.prevent="saveProvider">
        <label>显示名称<input v-model.trim="providerForm.displayName" required maxlength="120" autocomplete="off"></label>
        <label>客户端 ID<input v-model.trim="providerForm.clientId" required maxlength="200" autocomplete="off"></label>
        <label class="wide">Issuer URL<input v-model.trim="providerForm.issuer" type="url" required placeholder="https://id.example.com/tenant" autocomplete="url"></label>
        <p v-if="dialogError" class="form-error wide" role="alert">{{ dialogError }}</p>
        <footer class="dialog-actions wide"><button class="button secondary" type="button" @click="closeProviderDialog">取消</button><button class="button primary" type="submit" :disabled="saving">{{ saving ? "保存中…" : "保存配置" }}</button></footer>
      </form>
    </section>
  </div>

  <div v-if="secretDialog" class="dialog-backdrop" @click.self="closeSecretDialog">
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="secret-dialog-title">
      <header class="dialog-header"><div><span class="eyebrow">{{ secretDialog.configured ? "密钥轮转" : "首次写入" }}</span><h2 id="secret-dialog-title">{{ secretDialog.displayName }} · 客户端密钥</h2></div><button class="icon-button" type="button" aria-label="关闭" @click="closeSecretDialog">×</button></header>
      <p class="dialog-copy">密钥仅可写入，不会读取或回显已保存的内容。粘贴新密钥后提交；提交完成输入框会立即清空。</p>
      <form class="form-grid" @submit.prevent="saveSecret">
        <label class="wide">新客户端密钥<input ref="secretInput" v-model="secretInputValue" type="password" required maxlength="4096" autocomplete="new-password" :placeholder="secretDialog.configured ? '输入要轮转的新密钥' : '粘贴客户端密钥'"></label>
        <p v-if="secretDialogError" class="form-error wide" role="alert">{{ secretDialogError }}</p>
        <footer class="dialog-actions wide"><button class="button secondary" type="button" @click="closeSecretDialog">取消</button><button class="button primary" type="submit" :disabled="savingSecret">{{ savingSecret ? "安全写入中…" : secretDialog.configured ? "轮转密钥" : "写入密钥" }}</button></footer>
      </form>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p2 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard } from "../../components";

const providers = ref([]);
const secrets = ref({});
const error = ref("");
const traceId = ref("");
const loading = ref(false);
const loaded = ref(false);
const busyId = ref("");
const actionMessages = ref({});
const providerDialog = ref(false);
const secretDialog = ref(null);
const editingId = ref("");
const saving = ref(false);
const savingSecret = ref(false);
const dialogError = ref("");
const secretDialogError = ref("");
const secretInput = ref(null);
const secretInputValue = ref("");
const providerForm = ref({ displayName: "", clientId: "", issuer: "" });

function secretConfigured(id) { return secrets.value[id]?.configured === true; }
function secretLabel(id) {
  const value = secrets.value[id];
  if (!value) return "检查失败";
  return value.configured ? "已配置（不显示密钥）" : "未配置";
}
function countLabel(value) { return Number.isInteger(value) && value >= 0 ? String(value) : "暂无"; }
function testLabel(value) {
  if (value === true) return "成功";
  if (value === false) return "失败";
  return "暂无测试结果";
}
function formatTime(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : date.toLocaleString("zh-CN", { hour12: false });
}
function safeProviders(value) { return Array.isArray(value) ? value.filter((item) => item && item.id) : []; }

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const result = await p2.identityProviders();
    providers.value = safeProviders(result);
    const statuses = await Promise.all(providers.value.map(async (provider) => {
      try { return [provider.id, await p2.identityProviderSecretStatus(provider.id)]; }
      catch { return [provider.id, null]; }
    }));
    secrets.value = Object.fromEntries(statuses);
    loaded.value = true;
  } catch (err) {
    error.value = err?.message || "身份提供方加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = "";
  providerForm.value = { displayName: "", clientId: "", issuer: "" };
  dialogError.value = "";
  providerDialog.value = true;
}
function openEdit(provider) {
  editingId.value = provider.id;
  providerForm.value = { displayName: provider.displayName || "", clientId: provider.clientId || "", issuer: provider.issuer || "" };
  dialogError.value = "";
  providerDialog.value = true;
}
function closeProviderDialog() { if (!saving.value) providerDialog.value = false; }
async function saveProvider() {
  saving.value = true;
  dialogError.value = "";
  const body = { providerType: "OIDC", ...providerForm.value, status: providers.value.find((item) => item.id === editingId.value)?.status || "DISABLED" };
  try {
    if (editingId.value) await p2.updateIdentityProvider(editingId.value, body);
    else await p2.createIdentityProvider(body);
    providerDialog.value = false;
    await load();
  } catch (err) {
    dialogError.value = err?.message || "配置保存失败";
  } finally { saving.value = false; }
}

function openSecret(provider) {
  secretDialogError.value = "";
  secretInputValue.value = "";
  secretDialog.value = { id: provider.id, displayName: provider.displayName, configured: secretConfigured(provider.id) };
}
function closeSecretDialog() {
  if (savingSecret.value) return;
  secretInputValue.value = "";
  if (secretInput.value) secretInput.value.value = "";
  secretDialog.value = null;
}
async function saveSecret() {
  const dialog = secretDialog.value;
  if (!dialog || !secretInputValue.value) return;
  const id = dialog.id;
  savingSecret.value = true;
  secretDialogError.value = "";
  const value = secretInputValue.value;
  try {
    if (dialog.configured) await p2.rotateIdentityProviderSecret(id, value);
    else await p2.writeIdentityProviderSecret(id, value);
    secretDialog.value = null;
    actionMessages.value = { ...actionMessages.value, [id]: dialog.configured ? "新密钥已提交轮转。" : "密钥已写入。" };
    await load();
  } catch (err) {
    secretDialogError.value = err?.message || "密钥写入失败，请检查后重试。";
  } finally {
    secretInputValue.value = "";
    if (secretInput.value) secretInput.value.value = "";
    savingSecret.value = false;
  }
}

async function testConnection(provider) {
  busyId.value = provider.id;
  actionMessages.value = { ...actionMessages.value, [provider.id]: "正在测试连接…" };
  try {
    const result = await p2.testIdentityProviderConnection(provider.id);
    actionMessages.value = { ...actionMessages.value, [provider.id]: result?.status === "SUCCEEDED" ? `连接测试成功 · ${formatTime(result.testedAt)}` : `连接测试失败 · ${formatTime(result?.testedAt)}` };
    await load();
  } catch (err) {
    actionMessages.value = { ...actionMessages.value, [provider.id]: err?.message || "连接测试请求失败" };
  } finally { busyId.value = ""; }
}
async function toggleStatus(provider) {
  busyId.value = provider.id;
  try {
    await p2.updateIdentityProvider(provider.id, {
      providerType: provider.providerType, displayName: provider.displayName, clientId: provider.clientId,
      issuer: provider.issuer, status: provider.status === "ENABLED" ? "DISABLED" : "ENABLED",
    });
    await load();
  } catch (err) {
    actionMessages.value = { ...actionMessages.value, [provider.id]: err?.message || "状态更新失败" };
  } finally { busyId.value = ""; }
}
async function removeProvider(provider) {
  if (!globalThis.confirm(`确定删除身份提供方“${provider.displayName}”吗？此操作会停用并移除其密钥配置。`)) return;
  busyId.value = provider.id;
  try {
    await p2.deleteIdentityProvider(provider.id);
    await load();
  } catch (err) {
    actionMessages.value = { ...actionMessages.value, [provider.id]: err?.message || "删除失败" };
  } finally { busyId.value = ""; }
}

onMounted(load);
</script>

<style scoped>
.provider-list { display: grid; gap: 16px; }
.provider-card { min-width: 0; padding: 20px; border: 1px solid var(--nx-border, #e5eaf2); border-radius: 14px; background: var(--nx-surface, #fff); }
.provider-heading { display: flex; align-items: flex-start; gap: 14px; }
.provider-mark { display: grid; flex: none; width: 44px; height: 44px; place-items: center; border-radius: 12px; color: var(--nx-primary, #3267d6); background: var(--nx-primary-light, #edf3ff); font-weight: 700; }
.provider-title { min-width: 0; flex: 1; }
.eyebrow { color: var(--nx-text-secondary, #718096); font-size: 12px; }
.provider-title h2 { margin: 3px 0; color: var(--nx-text-primary, #172b4d); font-size: 17px; }
.provider-title p { margin: 0; color: var(--nx-text-secondary, #718096); overflow-wrap: anywhere; font-size: 13px; }
.status-pill { flex: none; padding: 5px 10px; border-radius: 999px; color: #7a4e00; background: #fff5d8; font-size: 12px; font-weight: 700; }
.status-pill[data-status="ENABLED"] { color: #18794e; background: #e7f7ee; }
.provider-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); gap: 14px; margin: 22px 0; padding: 16px 0; border-block: 1px solid var(--nx-divider, #edf0f4); }
.provider-facts div { min-width: 0; }
.provider-facts dt { margin-bottom: 5px; color: var(--nx-text-secondary, #718096); font-size: 12px; }
.provider-facts dd { margin: 0; color: var(--nx-text-primary, #344054); overflow-wrap: anywhere; font-size: 14px; }
.provider-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.button { min-height: 36px; padding: 7px 13px; border: 1px solid transparent; border-radius: 8px; font: inherit; font-size: 13px; cursor: pointer; }
.button:disabled { cursor: not-allowed; opacity: .55; }
.primary { color: #fff; background: var(--nx-primary, #3267d6); }
.secondary { color: var(--nx-text-primary, #344054); border-color: var(--nx-border, #d8dee9); background: var(--nx-surface, #fff); }
.danger { color: #b42318; border-color: #f0c7c3; background: #fff; }
.state-note,.action-message { color: var(--nx-text-secondary, #718096); font-size: 13px; }
.dialog-backdrop { position: fixed; z-index: 100; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 42%); }
.dialog { width: min(560px, 100%); max-height: min(90vh, 720px); overflow: auto; padding: 24px; border-radius: 16px; background: var(--nx-surface, #fff); box-shadow: var(--nx-shadow-dialog, 0 18px 60px rgb(0 0 0 / 24%)); }
.dialog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.dialog-header h2 { margin: 4px 0 0; font-size: 19px; }
.icon-button { width: 34px; height: 34px; border: 0; border-radius: 8px; background: transparent; color: var(--nx-text-secondary, #718096); font-size: 25px; cursor: pointer; }
.dialog-copy { color: var(--nx-text-secondary, #667085); font-size: 13px; line-height: 1.6; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 15px; }
.form-grid label { display: grid; gap: 6px; color: var(--nx-text-secondary, #667085); font-size: 13px; }
.form-grid input { width: 100%; min-height: 40px; box-sizing: border-box; padding: 8px 10px; border: 1px solid var(--nx-border, #d8dee9); border-radius: 8px; color: var(--nx-text-primary, #344054); background: var(--nx-surface, #fff); font: inherit; }
.wide { grid-column: 1 / -1; }
.form-error { margin: 0; color: #b42318; font-size: 13px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 6px; }
@media (max-width: 640px) { .provider-heading { flex-wrap: wrap; }.provider-title { flex-basis: calc(100% - 60px); }.status-pill { margin-left: 58px; }.form-grid { grid-template-columns: 1fr; }.wide { grid-column: auto; }.dialog-actions { grid-column: 1; } }
</style>
