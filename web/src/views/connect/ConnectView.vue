<template>
  <main class="connect-page">
    <NxPageHeader eyebrow="P5 · NEXUS CONNECT" title="接入总览" description="管理服务身份、Webhook 订阅与自用连接器。三类接入能力分别配置与查看。" />
    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <section v-else class="integration-grid" aria-label="接入能力">
      <article class="integration-card identity-card" data-testid="identity-card">
        <header class="card-heading"><div class="section-icon identity-icon" aria-hidden="true">ID</div><div><span class="eyebrow">身份与凭据</span><h2>服务身份</h2></div></header>
        <p class="section-description">为内部服务签发 API Client 凭据。密钥仅在签发成功后显示一次。</p>
        <label class="nx-field"><span>名称</span><input v-model="clientName" /></label>
        <label class="nx-field"><span>Scope（逗号分隔）</span><input v-model="scopeText" /></label>
        <button class="primary" type="button" :disabled="issuing" @click="issueClient">{{ issuing ? '签发中…' : '签发一次性密钥' }}</button>
        <section v-if="issued" class="one-time-secret" role="alert" data-testid="one-time-secret">
          <div class="secret-heading"><span class="warning-icon" aria-hidden="true">!</span><strong>请立即保存，之后不再显示</strong></div>
          <code>{{ issued.secret }}</code>
          <small>{{ issued.id }} · {{ (issued.scopes || []).join(", ") || '暂无 Scope' }}</small>
          <p>离开本页或刷新后不会再次提供密钥。请按组织的凭据保管规范保存。</p>
        </section>
      </article>

      <article class="integration-card webhook-card" data-testid="webhook-card">
        <header class="card-heading"><div class="section-icon webhook-icon" aria-hidden="true">↗</div><div><span class="eyebrow">事件投递</span><h2>Webhook 订阅</h2></div></header>
        <p class="section-description">配置接收端点与需要订阅的事件类型。</p>
        <label class="nx-field"><span>名称</span><input v-model="webhook.name" /></label>
        <label class="nx-field"><span>HTTPS 端点</span><input v-model="webhook.endpoint" type="url" placeholder="https://example.com/events" /></label>
        <label class="nx-field"><span>事件类型（逗号分隔）</span><input v-model="webhook.events" placeholder="record.changed" /></label>
        <button class="primary" type="button" :disabled="subscribing" @click="subscribe">{{ subscribing ? '创建中…' : '创建订阅' }}</button>
        <p v-if="subscription" class="result-note" role="status">已创建订阅：<code>{{ subscription.id }}</code></p>
      </article>

      <article class="integration-card connector-card" data-testid="connector-card">
        <header class="card-heading"><div class="section-icon connector-icon" aria-hidden="true">⎇</div><div><span class="eyebrow">自动化任务</span><h2>自用连接器</h2></div></header>
        <p class="section-description">运行连接器并查看最近执行结果。</p>
        <div v-if="loading" class="loading" role="status">正在加载连接器…</div>
        <NxEmpty v-else-if="!connectors.length" title="暂无连接器" description="当前没有可运行的自用连接器。" compact />
        <div v-else class="connector-list">
          <div v-for="item in connectors" :key="item.code" class="connector-row">
            <div class="connector-identity"><strong>{{ item.name || item.code }}</strong><span class="nx-code">{{ item.code }} · {{ item.type || '暂无类型' }}</span></div>
            <NxStatusTag :status="item.active ? 'ACTIVE' : 'DISABLED'" :label="item.active ? '启用' : '停用'" />
            <button class="secondary" type="button" :disabled="running" @click="run(item.code)">{{ runningCode === item.code ? '运行中…' : '立即运行' }}</button>
          </div>
        </div>
        <div class="runs-section"><h3>最近运行</h3>
          <NxEmpty v-if="!loading && !runs.length" title="暂无运行记录" description="连接器运行后，最近结果会显示在这里。" compact />
          <div v-else-if="runs.length" class="runs-list" data-testid="connector-runs">
            <div v-for="item in runs" :key="item.id" class="run-row"><span>{{ item.startedAt || item.started_at || '暂无时间' }}</span><span class="nx-code">{{ item.connectorCode || item.connector_code || '暂无连接器' }}</span><NxStatusTag :status="item.status" /></div>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";

const connectors = ref([]); const runs = ref([]); const issued = ref(null); const subscription = ref(null);
const error = ref(""); const loading = ref(true); const issuing = ref(false); const subscribing = ref(false); const running = ref(false); const runningCode = ref("");
const clientName = ref("nexus-internal"); const scopeText = ref("integration:call");
const webhook = reactive({ name: "internal-events", endpoint: "https://example.invalid/nexus-events", events: "record.changed" });

async function load() {
  loading.value = true; error.value = "";
  try { [connectors.value, runs.value] = await Promise.all([p5.connectors(), p5.connectorRuns()]); }
  catch (err) { error.value = err.message || "接入数据暂时不可用"; }
  finally { loading.value = false; }
}
async function issueClient() {
  issuing.value = true; error.value = "";
  try { issued.value = await p5.issueClient({ name: clientName.value, scopes: scopeText.value.split(",").map((value) => value.trim()).filter(Boolean), rateLimitPerMinute: 60 }); }
  catch (err) { error.value = err.message || "服务身份签发失败"; }
  finally { issuing.value = false; }
}
async function subscribe() {
  subscribing.value = true; error.value = "";
  try { subscription.value = await p5.createWebhook({ name: webhook.name, endpoint: webhook.endpoint, eventTypes: webhook.events.split(",").map((value) => value.trim()).filter(Boolean) }); }
  catch (err) { error.value = err.message || "Webhook 订阅创建失败"; }
  finally { subscribing.value = false; }
}
async function run(code) {
  running.value = true; runningCode.value = code; error.value = "";
  try { await p5.runConnector(code); runs.value = await p5.connectorRuns(); }
  catch (err) { error.value = err.message || "连接器运行失败"; }
  finally { running.value = false; runningCode.value = ""; }
}
onMounted(load);
</script>

<style scoped>
.connect-page { display: grid; gap: var(--nx-space-4); }
.integration-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); align-items: stretch; gap: var(--nx-space-4); }
.integration-card { display: flex; min-width: 0; flex-direction: column; gap: var(--nx-space-4); padding: var(--nx-space-5); background: var(--nx-surface); border: 1px solid var(--nx-border); border-radius: 12px; box-shadow: var(--nx-shadow-card); }
.card-heading { display: flex; align-items: center; gap: var(--nx-space-3); padding-bottom: var(--nx-space-3); border-bottom: 1px solid var(--nx-divider); }
.section-icon { display: grid; flex: 0 0 44px; width: 44px; height: 44px; place-items: center; border-radius: 12px; font-size: var(--nx-font-size-sm); font-weight: 700; }
.identity-icon { color: var(--nx-primary); background: var(--nx-primary-light); }.webhook-icon { color: var(--nx-info); background: var(--nx-info-light); }.connector-icon { color: var(--nx-success); background: var(--nx-success-light); }
.eyebrow { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }.card-heading h2 { margin: var(--nx-space-1) 0 0; font-size: var(--nx-font-size-md); }
.section-description { min-height: 42px; margin: 0; color: var(--nx-text-secondary); line-height: 1.5; }
.nx-field { display: grid; gap: var(--nx-space-1); }.nx-field label,.nx-field span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }.nx-field input { min-width: 0; width: 100%; min-height: 38px; padding: var(--nx-space-2) var(--nx-space-3); border: 1px solid var(--nx-border-strong); border-radius: 8px; }
.primary,.secondary { min-height: 38px; padding: 0 var(--nx-space-3); border: 1px solid var(--nx-product); border-radius: 8px; cursor: pointer; }.primary { color: #fff; background: var(--nx-product); }.secondary { color: var(--nx-product); background: var(--nx-surface); }.primary:disabled,.secondary:disabled { opacity: .55; cursor: not-allowed; }
.one-time-secret { display: grid; gap: var(--nx-space-2); margin-top: var(--nx-space-1); padding: var(--nx-space-3); border: 1px solid var(--nx-warning); border-radius: 10px; background: var(--nx-warning-light); }.secret-heading { display: flex; align-items: center; gap: var(--nx-space-2); color: var(--nx-text-primary); }.warning-icon { display: grid; width: 20px; height: 20px; place-items: center; color: #fff; background: var(--nx-warning); border-radius: 50%; font-weight: 700; }.one-time-secret code { display: block; padding: var(--nx-space-3); overflow-wrap: anywhere; color: var(--nx-text-primary); background: var(--nx-surface); border: 1px dashed var(--nx-warning); border-radius: 6px; font-size: var(--nx-font-size-sm); }.one-time-secret small,.one-time-secret p,.result-note { margin: 0; color: var(--nx-text-secondary); overflow-wrap: anywhere; }.one-time-secret p { font-size: var(--nx-font-size-xs); }
.connector-list { display: grid; gap: var(--nx-space-2); }.connector-row { display: grid; grid-template-columns: minmax(0,1fr) auto; align-items: center; gap: var(--nx-space-2); padding: var(--nx-space-3); border: 1px solid var(--nx-divider); border-radius: 8px; }.connector-identity { display: grid; min-width: 0; gap: var(--nx-space-1); }.connector-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.connector-identity span { overflow-wrap: anywhere; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }.connector-row :deep(.chip) { grid-column: 1; justify-self: start; }.connector-row .secondary { grid-column: 2; grid-row: 1 / span 2; }
.runs-section { margin-top: auto; padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-divider); }.runs-section h3 { margin: 0 0 var(--nx-space-2); font-size: var(--nx-font-size-sm); }.runs-list { display: grid; gap: var(--nx-space-2); }.run-row { display: grid; grid-template-columns: 1fr auto; gap: var(--nx-space-2); align-items: center; padding: var(--nx-space-2) 0; color: var(--nx-text-secondary); border-bottom: 1px solid var(--nx-divider); font-size: var(--nx-font-size-xs); }.run-row span:first-child { overflow-wrap: anywhere; }.run-row :deep(.chip) { grid-column: 1 / -1; justify-self: start; }
.loading { color: var(--nx-text-secondary); }
@media(max-width:1080px) { .integration-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }.connector-card { grid-column: 1 / -1; } }
@media(max-width:680px) { .integration-grid { grid-template-columns: minmax(0,1fr); }.connector-card { grid-column: auto; }.integration-card { padding: var(--nx-space-4); } }
</style>
