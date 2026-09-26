<template>
  <main class="review-page" data-testid="tenant-registration-review">
    <NxPageHeader
      eyebrow="星枢 · 平台"
      title="租户注册审订"
      description="审订待处理的租户自助注册申请。通过后创建租户并初始化管理员；无支付。"
      :crumbs="[{ label: '星枢' }, { label: '租户注册审订' }]"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </template>
    </NxPageHeader>

    <NxFilterBar label="注册申请筛选" testid="registration" @reset="statusFilter = ''; load()">
      <label class="nx-filter-field">
        状态
        <select v-model="statusFilter" data-testid="registration-status-filter" @change="load">
          <option value="">全部</option>
          <option value="PENDING">待审订</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
      </label>
      <template #summary>{{ loading ? "加载中…" : `共 ${rows.length} 条` }}</template>
    </NxFilterBar>

    <NxDataTable
      :loading="loading"
      :error="error"
      :empty="!rows.length"
      empty-title="暂无注册申请"
      empty-description="租户通过自助注册页提交后，会出现在这里供平台审订。"
      density="compact"
      testid="registration-table"
      @retry="load"
    >
      <template #header>
        <tr>
          <th>组织名称</th>
          <th>管理员</th>
          <th>邮箱</th>
          <th>状态</th>
          <th>提交时间</th>
          <th>操作</th>
        </tr>
      </template>
      <tr v-for="row in rows" :key="row.id" :data-testid="`registration-row-${row.id}`">
        <td><strong>{{ row.org_name }}</strong></td>
        <td>{{ row.admin_display_name }}</td>
        <td>{{ row.admin_email }}</td>
        <td><NxStatusTag :status="row.status" :label="statusLabel(row.status)" /></td>
        <td>{{ formatDate(row.created_at) }}</td>
        <td class="actions">
          <button class="text-action" type="button" @click="open(row)">详情</button>
        </td>
      </tr>
    </NxDataTable>

    <aside v-if="selected" class="drawer" data-testid="registration-detail" role="dialog" aria-modal="true">
      <header>
        <div>
          <span class="eyebrow">注册申请</span>
          <h2>{{ selected.org_name }}</h2>
        </div>
        <button type="button" class="close" aria-label="关闭" @click="selected = null">×</button>
      </header>
      <dl class="facts">
        <dt>状态</dt><dd><NxStatusTag :status="selected.status" :label="statusLabel(selected.status)" /></dd>
        <dt>管理员</dt><dd>{{ selected.admin_display_name }}</dd>
        <dt>邮箱</dt><dd>{{ selected.admin_email }}</dd>
        <dt>补充说明</dt><dd>{{ selected.contact_note || "暂无" }}</dd>
        <dt>提交时间</dt><dd>{{ formatDate(selected.created_at) }}</dd>
        <dt v-if="selected.resulting_tenant_id">已创建租户</dt>
        <dd v-if="selected.resulting_tenant_id"><code>{{ selected.resulting_tenant_id }}</code></dd>
        <dt v-if="selected.review_note">审订说明</dt>
        <dd v-if="selected.review_note">{{ selected.review_note }}</dd>
      </dl>
      <template v-if="selected.status === 'PENDING'">
        <label class="nx-field">
          审订说明（驳回时必填）
          <textarea v-model="note" rows="2" data-testid="registration-note" />
        </label>
        <div class="drawer-actions">
          <button class="nx-btn nx-btn--primary" type="button" :disabled="busy" data-testid="registration-approve" @click="approve">通过并创建租户</button>
          <button class="nx-btn nx-btn--default" type="button" :disabled="busy" data-testid="registration-reject" @click="reject">驳回</button>
        </div>
        <p v-if="actionError" class="field-error" data-testid="registration-action-error">{{ actionError }}</p>
      </template>
    </aside>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { NxDataTable, NxFilterBar, NxPageHeader, NxStatusTag } from "../../components";
import { p1 } from "../../api/client";
import { nxMessage } from "../../feedback/nxMessage";

const rows = ref([]);
const loading = ref(true);
const error = ref("");
const statusFilter = ref("PENDING");
const selected = ref(null);
const note = ref("");
const busy = ref(false);
const actionError = ref("");

function statusLabel(status) {
  return ({ PENDING: "待审订", APPROVED: "已通过", REJECTED: "已驳回" })[status] || status || "暂无";
}

function formatDate(value) {
  if (!value) return "暂无";
  try { return new Date(value).toLocaleString("zh-CN", { hour12: false }); }
  catch { return String(value); }
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    rows.value = await p1.listTenantRegistrations(statusFilter.value || undefined);
  } catch (err) {
    error.value = err.message || "无法加载注册申请";
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
    const updated = await p1.approveTenantRegistration(selected.value.id, note.value || null);
    nxMessage.success(`已通过「${updated.org_name}」并创建租户`);
    selected.value = updated;
    await load();
  } catch (err) {
    actionError.value = err.message || "通过失败";
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
    const updated = await p1.rejectTenantRegistration(selected.value.id, note.value.trim());
    nxMessage.success(`已驳回「${updated.org_name}」`);
    selected.value = updated;
    await load();
  } catch (err) {
    actionError.value = err.message || "驳回失败";
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.review-page { padding: var(--nx-space-4); }
.actions { white-space: nowrap; }
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