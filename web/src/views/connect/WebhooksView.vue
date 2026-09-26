<template>
  <main class="webhooks-page">
    <NxPageHeader
      eyebrow="星联 / 事件订阅"
      title="Webhook"
      description="管理自用事件订阅与投递摘要；投递记录不展示请求体。"
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
            <div><dt>状态</dt><dd><NxStatusTag v-if="webhook.active != null" :status="webhook.active ? 'ACTIVE' : 'DISABLED'" :label="webhook.active ? '启用' : '停用'" /><template v-else>暂无</template></dd></div>
            <div><dt>创建时间</dt><dd>{{ show(webhook.createdAt) }}</dd></div>
            <div><dt>签名材料</dt><dd>暂无</dd></div>
          </dl>
        </section>
        <section class="deliveries">
          <h2>投递</h2>
          <NxErrorState v-if="deliveryError" :title="deliveryError" :trace-id="deliveryTrace" retry-label="重试" @retry="load" />
          <NxEmpty v-else-if="deliveries && !deliveries.length" title="暂无投递记录" description="事件触发并尝试投递后，这里会显示只读摘要。" compact />
          <div v-else-if="deliveries?.length" class="table-scroll"><table class="nx-table nx-table--compact" data-testid="webhook-deliveries-table">
            <thead>
              <tr><th scope="col">事件</th><th scope="col">类型</th><th scope="col">状态</th><th scope="col">尝试次数</th><th scope="col">下次尝试</th><th scope="col">送达时间</th></tr>
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
          </table></div>
          <div v-else-if="!loaded" class="loading-list"><NxSkeleton v-for="index in 2" :key="index" variant="list-row" label="正在加载投递记录" compact /></div>
        </section>
      </template>
    </template>

    <template v-else>
      <NxStatCard label="Webhook" :value="count" unit="个" />
      <NxEmpty v-if="rows && !rows.length" title="暂无 Webhook" description="创建事件订阅后，会在这里显示端点、事件类型和状态。">
        <template #next><a class="ghost" href="#create-webhook">创建订阅</a></template>
      </NxEmpty>
      <div v-else-if="rows?.length" class="table-scroll"><table class="nx-table nx-table--compact" data-testid="webhooks-table">
        <thead>
          <tr><th scope="col">名称</th><th scope="col">端点</th><th scope="col">事件</th><th scope="col">状态</th><th scope="col">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in listed" :key="row.id || row.endpoint">
            <td>{{ show(row.name) }}</td>
            <td>{{ show(row.endpoint) }}</td>
            <td>{{ row.eventTypes.length ? row.eventTypes.join("、") : "暂无" }}</td>
            <td><NxStatusTag v-if="row.active != null" :status="row.active ? 'ACTIVE' : 'DISABLED'" :label="row.active ? '启用' : '停用'" /><template v-else>暂无</template></td>
            <td><router-link v-if="row.id" :to="`/p5/webhooks/${row.id}`">查看</router-link><template v-else>暂无</template></td>
          </tr>
        </tbody>
      </table></div>
      <div v-else-if="!loaded" class="loading-list"><NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载 Webhook" compact /></div>

      <section id="create-webhook" class="nx-card issue">
        <h2>创建事件订阅</h2>
        <div class="nx-field"><label>名称</label><input v-model="name" /></div>
        <div class="nx-field"><label>HTTPS 端点</label><input v-model="endpoint" /></div>
        <div class="nx-field"><label>事件类型（逗号分隔）</label><input v-model="events" /></div>
        <p v-if="formError" class="nx-error">{{ formError }}</p>
        <button class="primary" type="button" :disabled="saving" @click="create">创建订阅</button>
        <section v-if="createdId" class="created-feedback" role="status" data-testid="webhook-created">
          <strong>订阅已创建</strong>
          <p>可在详情页查看订阅配置和后续投递记录。</p>
          <router-link class="ghost" :to="`/p5/webhooks/${createdId}`">查看订阅详情</router-link>
        </section>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatCard, NxStatusTag } from "../../components";
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
const count = computed(() => rows.value?.length ?? null);
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
.webhooks-page { display: grid; gap: var(--nx-space-4); }
.webhooks-page :deep(.page-header) { margin-bottom: 0; }
.table-scroll { overflow-x: auto; }
.table-scroll .nx-table { min-width: 700px; }
.loading-list { display: grid; gap: var(--nx-space-2); }
.created-feedback { display: grid; justify-items: start; gap: var(--nx-space-2); margin-top: var(--nx-space-4); padding: var(--nx-space-3); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); background: var(--nx-success-light); }
.created-feedback strong { color: var(--nx-success); }
.created-feedback p { margin: 0; color: var(--nx-text-secondary); }
.detail, .issue, .deliveries { margin-top: 0; }
.detail h2, .issue h2, .deliveries h2 { margin-top: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-3); margin: 0; }
dl div { display: grid; grid-template-columns: 120px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
</style>
