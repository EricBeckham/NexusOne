<template>
  <main>
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="Webhook"
      description="自用事件订阅。投递记录不展示请求体。"
    >
      <template v-if="isDetail" #actions>
        <router-link class="ghost" to="/p5/webhooks">返回列表</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />

    <template v-else-if="isDetail">
      <NxEmpty v-if="loaded && !webhook" title="暂无" description="接口没有返回这个 Webhook。" />
      <template v-else-if="webhook">
        <section class="nx-card detail">
          <h2>{{ show(webhook.name) }}</h2>
          <dl>
            <div><dt>标识</dt><dd>{{ show(webhook.id) }}</dd></div>
            <div><dt>端点</dt><dd>{{ show(webhook.endpoint) }}</dd></div>
            <div><dt>事件</dt><dd>{{ webhook.eventTypes.length ? webhook.eventTypes.join("、") : "暂无" }}</dd></div>
            <div><dt>状态</dt><dd><NxStatusTag v-if="webhook.active != null" :status="webhook.active ? 'ACTIVE' : 'DISABLED'" /><template v-else>暂无</template></dd></div>
            <div><dt>创建时间</dt><dd>{{ show(webhook.createdAt) }}</dd></div>
            <div><dt>签名材料</dt><dd>暂无</dd></div>
          </dl>
        </section>
        <section class="deliveries">
          <h2>投递</h2>
          <NxErrorState v-if="deliveryError" :title="deliveryError" :trace-id="deliveryTrace" retry-label="重试" @retry="load" />
          <NxEmpty v-else-if="deliveries && !deliveries.length" title="暂无" description="还没有投递记录。" />
          <table v-else-if="deliveries?.length" class="nx-table">
            <thead>
              <tr><th>事件</th><th>类型</th><th>状态</th><th>尝试次数</th><th>下次尝试</th><th>送达时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in deliveryRows" :key="row.id || row.eventId">
                <td>{{ show(row.eventId) }}</td>
                <td>{{ show(row.eventType) }}</td>
                <td><NxStatusTag v-if="row.status" :status="row.status" :label="deliveryLabel(row.status)" /><template v-else>暂无</template></td>
                <td>{{ show(row.attempts) }}</td>
                <td>{{ show(row.nextAttemptAt) }}</td>
                <td>{{ show(row.deliveredAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </template>

    <template v-else>
      <NxStatCard label="Webhook" :value="count" unit="个" />
      <NxEmpty v-if="rows && !rows.length" title="暂无" description="当前租户还没有 Webhook。" />
      <table v-else-if="rows?.length" class="nx-table">
        <thead>
          <tr><th>名称</th><th>端点</th><th>事件</th><th>状态</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="row in listed" :key="row.id || row.endpoint">
            <td>{{ show(row.name) }}</td>
            <td>{{ show(row.endpoint) }}</td>
            <td>{{ row.eventTypes.length ? row.eventTypes.join("、") : "暂无" }}</td>
            <td><NxStatusTag v-if="row.active != null" :status="row.active ? 'ACTIVE' : 'DISABLED'" /><template v-else>暂无</template></td>
            <td><router-link v-if="row.id" :to="`/p5/webhooks/${row.id}`">查看</router-link><template v-else>暂无</template></td>
          </tr>
        </tbody>
      </table>

      <section class="nx-card issue">
        <h2>创建订阅</h2>
        <div class="nx-field"><label>名称</label><input v-model="name" /></div>
        <div class="nx-field"><label>HTTPS 端点</label><input v-model="endpoint" /></div>
        <div class="nx-field"><label>事件类型（逗号分隔）</label><input v-model="events" /></div>
        <p v-if="formError" class="nx-error">{{ formError }}</p>
        <button class="primary" type="button" :disabled="saving" @click="create">创建订阅</button>
        <p v-if="createdId" class="nx-muted">已创建：{{ createdId }}</p>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatCard, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";
import { asRows, deliveryFields, deliveryLabel, show, webhookFields } from "./connectDisplay";

const route = useRoute();
const rows = ref(null);
const detail = ref(null);
const deliveries = ref(null);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");
const deliveryError = ref("");
const deliveryTrace = ref("");
const name = ref("");
const endpoint = ref("");
const events = ref("");
const createdId = ref("");
const saving = ref(false);
const formError = ref("");

const isDetail = computed(() => route.name === "p5-webhook");
const count = computed(() => (rows.value && rows.value.length ? rows.value.length : null));
const listed = computed(() => (rows.value || []).map(webhookFields).filter(Boolean));
const webhook = computed(() => webhookFields(detail.value));
const deliveryRows = computed(() => (deliveries.value || []).map(deliveryFields).filter(Boolean));

async function load() {
  error.value = "";
  traceId.value = "";
  deliveryError.value = "";
  deliveryTrace.value = "";
  createdId.value = "";
  formError.value = "";
  loaded.value = false;
  if (isDetail.value) {
    rows.value = null;
    try {
      const [hookResult, deliveryResult] = await Promise.allSettled([
        p5.webhook(route.params.id),
        p5.webhookDeliveries(route.params.id),
      ]);
      if (hookResult.status === "rejected") {
        detail.value = null;
        deliveries.value = null;
        error.value = hookResult.reason?.message || "加载失败";
        traceId.value = hookResult.reason?.traceId || "";
      } else {
        detail.value = hookResult.value;
        if (deliveryResult.status === "rejected") {
          deliveries.value = null;
          deliveryError.value = deliveryResult.reason?.message || "投递记录加载失败";
          deliveryTrace.value = deliveryResult.reason?.traceId || "";
        } else {
          deliveries.value = asRows(deliveryResult.value);
          if (!deliveries.value) {
            deliveryError.value = "接口没有返回投递列表";
          }
        }
      }
    } finally {
      loaded.value = true;
    }
    return;
  }
  detail.value = null;
  deliveries.value = null;
  try {
    const body = await p5.webhooks();
    rows.value = asRows(body);
    if (!rows.value) {
      error.value = "接口没有返回 Webhook 列表";
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
  if (!name.value.trim() || !endpoint.value.trim()) {
    formError.value = "请填写名称和端点";
    return;
  }
  saving.value = true;
  formError.value = "";
  try {
    const created = await p5.createWebhook({
      name: name.value.trim(),
      endpoint: endpoint.value.trim(),
      eventTypes: events.value.split(",").map((item) => item.trim()).filter(Boolean),
    });
    createdId.value = created?.id || "";
    name.value = "";
    endpoint.value = "";
    events.value = "";
  } catch (err) {
    createdId.value = "";
    formError.value = err.message || "创建失败";
    return;
  } finally {
    saving.value = false;
  }
  try {
    const list = await p5.webhooks();
    rows.value = asRows(list);
    if (!rows.value) {
      formError.value = "接口没有返回 Webhook 列表";
    }
  } catch (err) {
    formError.value = err.message || "列表刷新失败";
  }
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.detail, .issue, .deliveries { margin-top: var(--nx-space-4); }
.detail h2, .issue h2, .deliveries h2 { margin-top: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-3); margin: 0; }
dl div { display: grid; grid-template-columns: 120px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
</style>
