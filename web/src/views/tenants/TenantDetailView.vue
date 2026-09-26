<template>
  <main class="tenant-detail-page">
    <div v-if="loading" class="detail-skeleton" data-testid="tenant-detail-loading" role="status">
      <NxSkeleton variant="form-block" label="正在加载租户详情" />
    </div>
    <NxErrorState v-else-if="loadError" :description="loadError" retry-label="重试" @retry="reload" />
    <template v-else-if="tenant">
      <NxPageHeader
        eyebrow="星域 · 租户中心"
        :title="tenant.name || '暂无名称'"
        :description="`${tenant.code || '暂无编码'} · ${tenant.id}`"
        :crumbs="[
          { label: '租户目录', to: '/p1/tenants' },
          { label: tenant.name || '租户详情' },
        ]"
      >
        <template #actions>
          <NxStatusTag :status="tenant.status || ''" :label="statusLabel(tenant.status)" />
          <button
            v-if="tenant.status === 'ENABLED'"
            class="danger"
            type="button"
            data-testid="tenant-disable"
            :disabled="busy"
            @click="stop"
          >停用</button>
          <button
            v-else
            class="primary"
            type="button"
            data-testid="tenant-enable"
            :disabled="busy"
            @click="start"
          >启用</button>
        </template>
      </NxPageHeader>

      <p v-if="actionError" class="nx-error" role="alert">{{ actionError }}</p>

      <div class="detail-grid">
        <section class="nx-card section" data-testid="tenant-config-section">
          <header class="section-heading">
            <div>
              <h2>基础配置</h2>
              <p class="nx-muted">显示名与区域设置。保存只写配置接口，不改租户状态。</p>
            </div>
          </header>
          <div class="nx-field"><label for="tenant-display-name">显示名</label><input id="tenant-display-name" v-model="config.display_name" /></div>
          <div class="nx-field"><label for="tenant-locale">Locale</label><input id="tenant-locale" v-model="config.locale" /></div>
          <div class="nx-field"><label for="tenant-timezone">时区</label><input id="tenant-timezone" v-model="config.timezone" /></div>
          <div class="nx-field"><label for="tenant-currency">币种</label><input id="tenant-currency" v-model="config.currency" /></div>
          <div class="section-actions">
            <button class="primary" type="button" data-testid="tenant-save-config" :disabled="busy" @click="saveConfig">保存配置</button>
          </div>
        </section>

        <aside class="side-stack">
          <section class="nx-card section">
            <header class="section-heading">
              <div>
                <h2>权益投影</h2>
                <p class="nx-muted">只读投影；无数据时明确空态。</p>
              </div>
            </header>
            <NxEmpty v-if="!entitlement" title="暂无权益投影" description="当前租户没有可读的权益包投影。" compact />
            <dl v-else class="facts">
              <div><dt>来源</dt><dd>{{ text(entitlement.source) }}</dd></div>
              <div><dt>权益包</dt><dd>{{ text(entitlement.pack_code) }} {{ text(entitlement.pack_version) }}</dd></div>
            </dl>
          </section>
          <section class="nx-card section">
            <header class="section-heading">
              <div>
                <h2>配额</h2>
                <p class="nx-muted">用量与告警来自配额读接口。</p>
              </div>
              <router-link class="text-link" :to="`/p1/tenants/${tenant.id}/entitlement`">权益与配额</router-link>
            </header>
            <NxEmpty v-if="!quotas.length" title="暂无配额" description="当前没有配额指标可读。" compact />
            <ul v-else class="quota-list">
              <li v-for="q in quotas" :key="q.metric">
                <span>{{ q.metric }} {{ q.used }}/{{ q.limit }}</span>
                <NxStatusTag v-if="q.warning" status="PENDING" label="告警" tone="warning" />
              </li>
            </ul>
          </section>
        </aside>
      </div>

      <section class="nx-card section audit-section">
        <header class="section-heading">
          <div>
            <h2>审计摘要</h2>
            <p class="nx-muted">P1 暂存事件列表；完整平台审计见平台审计中心。</p>
          </div>
        </header>
        <NxEmpty v-if="!audit.length" title="暂无审计事件" description="该租户当前没有可展示的审计摘要。" compact />
        <div v-else class="table-scroll">
          <table class="nx-table nx-table--compact">
            <thead>
              <tr><th scope="col">时间</th><th scope="col">类型</th><th scope="col">结果</th><th scope="col">trace</th></tr>
            </thead>
            <tbody>
              <tr v-for="ev in audit" :key="ev.event_id || ev.eventId">
                <td>{{ text(ev.occurred_at || ev.occurredAt) }}</td>
                <td>{{ text(ev.event_type || ev.eventType) }}</td>
                <td><NxStatusTag :status="ev.outcome || ''" :label="text(ev.outcome)" /></td>
                <td class="nx-code">{{ text(ev.trace_id || ev.traceId) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { p1 } from "../../api/client";

const route = useRoute();
const tenant = ref(null);
const config = ref({});
const entitlement = ref(null);
const quotas = ref([]);
const audit = ref([]);
const loading = ref(true);
const busy = ref(false);
const loadError = ref("");
const actionError = ref("");

function text(value) {
  return value === null || value === undefined || value === "" ? "暂无" : String(value);
}

function statusLabel(status) {
  return ({ ENABLED: "启用", DISABLED: "停用", PENDING: "待处理" })[status] || status || "暂无";
}

async function reload() {
  loading.value = true;
  loadError.value = "";
  actionError.value = "";
  try {
    const id = route.params.id;
    tenant.value = await p1.getTenant(id);
    config.value = (await p1.getConfig(id)) || {};
    entitlement.value = await p1.entitlement(id);
    quotas.value = (await p1.quotas(id)) || [];
    audit.value = (await p1.audit(id)) || [];
  } catch (err) {
    tenant.value = null;
    loadError.value = `${err.code || ""} ${err.message || "无法读取租户详情"}`.trim();
  } finally {
    loading.value = false;
  }
}

onMounted(reload);

async function saveConfig() {
  busy.value = true;
  actionError.value = "";
  try {
    config.value = await p1.putConfig(route.params.id, config.value);
  } catch (err) {
    actionError.value = err.message || "保存配置失败";
  } finally {
    busy.value = false;
  }
}

async function stop() {
  busy.value = true;
  actionError.value = "";
  try {
    tenant.value = await p1.disable(route.params.id, "m0-12-ui");
  } catch (err) {
    actionError.value = `${err.code || ""} ${err.message || "停用失败"}`.trim();
  } finally {
    busy.value = false;
  }
}

async function start() {
  busy.value = true;
  actionError.value = "";
  try {
    tenant.value = await p1.enable(route.params.id);
  } catch (err) {
    actionError.value = `${err.code || ""} ${err.message || "启用失败"}`.trim();
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.tenant-detail-page { display: grid; gap: var(--nx-space-4); }
.tenant-detail-page :deep(.page-header) { margin-bottom: 0; }
.detail-skeleton { max-width: 720px; }
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr);
  gap: var(--nx-space-4);
  align-items: start;
}
.side-stack { display: grid; gap: var(--nx-space-4); }
.section { display: grid; gap: var(--nx-space-3); }
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nx-space-3);
}
.section-heading h2 { margin: 0; font-size: var(--nx-font-size-md); }
.section-heading .nx-muted { margin: var(--nx-space-1) 0 0; }
.section-actions { display: flex; justify-content: flex-start; }
.facts { display: grid; gap: var(--nx-space-2); margin: 0; }
.facts div { display: grid; grid-template-columns: 88px 1fr; gap: var(--nx-space-2); }
.facts dt { color: var(--nx-text-secondary); }
.facts dd { margin: 0; overflow-wrap: anywhere; }
.quota-list { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--nx-space-2); }
.quota-list li { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-2); }
.table-scroll { overflow-x: auto; }
.text-link { color: var(--nx-primary); text-decoration: none; font-size: var(--nx-font-size-sm); white-space: nowrap; }
.primary,
.danger,
.ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--nx-control-height);
  padding: 0 var(--nx-space-3);
  border-radius: var(--nx-control-radius);
  border: 1px solid transparent;
  cursor: pointer;
}
.primary { color: #fff; background: var(--nx-product); border-color: var(--nx-product); }
.danger { color: #fff; background: var(--nx-danger); border-color: var(--nx-danger); }
.primary:disabled,
.danger:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 900px) {
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
