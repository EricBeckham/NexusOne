<template>
  <div>
    <div class="nx-page-header nx-row" style="justify-content:space-between;">
      <div>
        <h1>站内通知</h1>
        <p>Live <code>GET /api/v1/notifications</code>。G18 审批通过 Outbox <code>notify.inbox</code> 投递。需要已签发 TenantContext。</p>
      </div>
      <button class="ghost" type="button" @click="reload">刷新</button>
    </div>
    <NxSourceBanner body="M0-10。列表为空不一定是故障：dispatcher 未投递或尚未走 submit-start。" />
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。请先登录并完成租户切换。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <table class="nx-table" data-testid="notify-table">
      <thead>
        <tr>
          <th>标题</th>
          <th>模板</th>
          <th>状态</th>
          <th>时间</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!rows.length">
          <td colspan="5" class="nx-empty">当前租户没有站内通知</td>
        </tr>
        <tr v-for="row in rows" :key="row.notification_id || row.id">
          <td>
            <strong>{{ row.title }}</strong>
            <div class="nx-muted">{{ row.body }}</div>
          </td>
          <td class="nx-code">{{ row.template_code }}</td>
          <td><NxStatus :label="row.status" :tone="row.status === 'DELIVERED' || row.status === 'READ' ? 'success' : 'warning'" /></td>
          <td>{{ row.created_at }}</td>
          <td>
            <router-link
              v-if="notificationTarget(row)"
              class="ghost"
              :to="notificationTarget(row).to"
              :data-testid="`notify-open-${row.notification_id || row.id}`"
              @click="markRead(row.notification_id || row.id, false)"
            >{{ notificationTarget(row).label }}</router-link>
            <button
              v-if="row.status !== 'READ'"
              class="ghost"
              type="button"
              :data-testid="`notify-read-${row.notification_id || row.id}`"
              @click="markRead(row.notification_id || row.id)"
            >已读</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import NxStatus from "../../components/NxStatus.vue";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { notify } from "../../api/client";
import { session } from "../../api/session";
import { notificationTarget } from "./notification-target";

const rows = ref([]);
const error = ref("");

async function reload() {
  error.value = "";
  const body = await notify.list();
  rows.value = Array.isArray(body) ? body : body?.items || [];
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function markRead(id, refresh = true) {
  try {
    await notify.read(id);
    if (refresh) await reload();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>
