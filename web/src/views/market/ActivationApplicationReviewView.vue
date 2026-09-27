<template>
  <main class="review-page" data-testid="activation-review">
    <NxPageHeader
      eyebrow="P4 · 星市"
      title="开通申请审订"
      description="平台审订租户提交的产品开通申请。通过后完成订阅与安装；无支付。"
      :crumbs="[{ label: '星市' }, { label: '开通申请审订' }]"
    >
      <template #actions>
        <router-link class="ghost" to="/p4/market">应用目录</router-link>
        <button class="ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </template>
    </NxPageHeader>

    <NxDataTable
      :loading="loading"
      :error="error"
      :empty="!rows.length"
      empty-title="暂无开通申请"
      empty-description="列表是空的，不是已经自动准备好的演示数据。需要内容时见 prod-safe 内容手册，再手工申请开通。"
      density="compact"
      testid="activation-table"
      @retry="load"
    >
      <template #empty-next>
        <router-link class="primary" to="/p4/market">前往应用目录</router-link>
      </template>
      <template #header>
        <tr>
          <th>租户</th>
          <th>SKU</th>
          <th>版本</th>
          <th>状态</th>
          <th>申请时间</th>
          <th>操作</th>
        </tr>
      </template>
      <tr v-for="row in rows" :key="row.id" :data-testid="`activation-row-${row.id}`">
        <td><code>{{ shortId(row.tenant_id) }}</code></td>
        <td>{{ row.sku_code }}</td>
        <td><code>{{ shortId(row.version_id) }}</code></td>
        <td><NxStatusTag :status="row.status" :label="statusLabel(row.status)" /></td>
        <td>{{ formatDate(row.created_at) }}</td>
        <td><button class="text-action" type="button" @click="open(row)">详情</button></td>
      </tr>
    </NxDataTable>

    <aside v-if="selected" class="drawer" data-testid="activation-detail" role="dialog" aria-modal="true">
      <header>
        <div>
          <span class="eyebrow">开通申请</span>
          <h2>{{ selected.sku_code }}</h2>
        </div>
        <button type="button" class="close" aria-label="关闭" @click="selected = null">×</button>
      </header>
      <dl class="facts">
        <dt>状态</dt><dd><NxStatusTag :status="selected.status" :label="statusLabel(selected.status)" /></dd>
        <dt>租户</dt><dd><code>{{ selected.tenant_id }}</code></dd>
        <dt>版本</dt><dd><code>{{ selected.version_id }}</code></dd>
        <dt>申请时间</dt><dd>{{ formatDate(selected.created_at) }}</dd>
        <dt v-if="selected.resulting_installation_id">安装编号</dt>
        <dd v-if="selected.resulting_installation_id"><code>{{ selected.resulting_installation_id }}</code></dd>
        <dt v-if="selected.review_note">审订说明</dt>
        <dd v-if="selected.review_note">{{ selected.review_note }}</dd>
      </dl>
      <template v-if="selected.status === 'PENDING'">
        <label class="nx-field">
          审订说明（驳回时必填）
          <textarea v-model="note" rows="2" data-testid="activation-note" />
        </label>
        <div class="drawer-actions">
          <button class="nx-btn nx-btn--primary" type="button" :disabled="busy" data-testid="activation-approve" @click="approve">通过开通</button>
          <button class="nx-btn nx-btn--default" type="button" :disabled="busy" data-testid="activation-reject" @click="reject">驳回</button>
        </div>
        <p v-if="actionError" class="field-error" data-testid="activation-action-error">{{ actionError }}</p>
      </template>
    </aside>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { NxDataTable, NxPageHeader, NxStatusTag } from "../../components";
import { p4 } from "../../api/client";
import { nxMessage } from "../../feedback/nxMessage";

const rows = ref([]);
const loading = ref(true);
const error = ref("");
const selected = ref(null);
const note = ref("");
const busy = ref(false);
const actionError = ref("");

function statusLabel(status) {
  return ({ PENDING: "待审订", APPROVED: "已通过", REJECTED: "已驳回" })[status] || status || "暂无";
}
function shortId(id) {
  if (!id) return "暂无";
  const s = String(id);
  return s.length > 8 ? `${s.slice(0, 8)}…` : s;
}
function formatDate(value) {
  if (!value) return "暂无";
  try { return new Date(value).toLocaleString("zh-CN", { hour12: false }); }
  catch { return String(value); }
}

function publicFailure(err, fallbackTitle) {
  const status = Number(err?.status) || 0;
  if (status === 403) {
    return { title: "没有权限处理开通申请", description: "HTTP 403。请检查职责与租户上下文。没有自动准备演示内容。见 prod-safe 内容手册。" };
  }
  if (!status) {
    return { title: fallbackTitle, description: "网络不可达。列表不会被自动填上。见 prod-safe 内容手册。" };
  }
  return { title: fallbackTitle, description: `HTTP ${status}。没有自动准备演示内容。见 prod-safe 内容手册。` };
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const result = await p4.activationApplications(false);
    rows.value = Array.isArray(result) ? result : [];
  } catch (err) {
    error.value = publicFailure(err, "开通申请暂时无法加载");
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function open(row) {
  selected.value = row;
  note.value = "";
  actionError.value = "";
}

async function approve() {
  busy.value = true;
  actionError.value = "";
  try {
    const updated = await p4.approveActivation(selected.value.id, note.value || null);
    nxMessage.success(`已通过开通「${updated.sku_code}」`);
    selected.value = updated;
    await load();
  } catch (err) {
    actionError.value = publicFailure(err, "这次没有通过").description;
  } finally {
    busy.value = false;
  }
}

async function reject() {
  if (!note.value.trim()) {
    actionError.value = "驳回时请填写审订说明";
    return;
  }
  busy.value = true;
  actionError.value = "";
  try {
    const updated = await p4.rejectActivation(selected.value.id, note.value.trim());
    nxMessage.success(`已驳回「${updated.sku_code}」`);
    selected.value = updated;
    await load();
  } catch (err) {
    actionError.value = publicFailure(err, "这次没有驳回").description;
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.review-page { padding: var(--nx-space-4); }
.text-action { border: 0; background: none; color: var(--nx-product); cursor: pointer; }
.drawer {
  position: fixed; top: 0; right: 0; width: min(420px, 100%); height: 100%;
  background: var(--nx-surface, #fff); border-left: 1px solid var(--nx-border);
  padding: var(--nx-space-4); overflow: auto; z-index: 20;
  box-shadow: -8px 0 24px rgb(0 0 0 / 8%);
}
.drawer header { display: flex; justify-content: space-between; gap: var(--nx-space-3); margin-bottom: var(--nx-space-3); }
.eyebrow { color: var(--nx-muted); font-size: 12px; }
.close { border: 0; background: none; font-size: 22px; cursor: pointer; }
.facts { display: grid; grid-template-columns: 96px 1fr; gap: 8px 12px; margin: 0 0 var(--nx-space-3); }
.facts dt { color: var(--nx-muted); }
.drawer-actions { display: flex; gap: var(--nx-space-2); flex-wrap: wrap; }
.field-error { color: var(--nx-danger); }
</style>