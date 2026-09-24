<template>
  <div>
    <div class="nx-page-header">
      <h1>星联</h1>
      <p>内部 API、可靠 Webhook 与 NexusOne 自用连接器。当前为 P5 薄切片，不开放第三方开发者生态。</p>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div class="nx-grid kpi summary">
      <article class="nx-card"><strong>{{ connectors.length }}</strong><span>可用连接器</span></article>
      <article class="nx-card"><strong>{{ runs.length }}</strong><span>最近运行</span></article>
      <article class="nx-card"><strong>5</strong><span>Webhook 最大尝试次数</span></article>
    </div>
    <div class="nx-grid panels">
      <section class="nx-card">
        <h2>服务身份</h2>
        <div class="nx-field"><label>名称</label><input v-model="clientName" /></div>
        <div class="nx-field"><label>Scope（逗号分隔）</label><input v-model="scopeText" /></div>
        <button class="primary" type="button" @click="issueClient">签发一次性密钥</button>
        <div v-if="issued" class="secret">
          <b>请立即保存，之后不再显示</b>
          <code>{{ issued.secret }}</code>
          <small>{{ issued.id }} · {{ issued.scopes.join(", ") }}</small>
        </div>
      </section>
      <section class="nx-card">
        <h2>Webhook 订阅</h2>
        <div class="nx-field"><label>名称</label><input v-model="webhook.name" /></div>
        <div class="nx-field"><label>HTTPS 端点</label><input v-model="webhook.endpoint" placeholder="https://..." /></div>
        <div class="nx-field"><label>事件类型</label><input v-model="webhook.events" /></div>
        <button class="primary" type="button" @click="subscribe">创建订阅</button>
        <p v-if="subscription" class="nx-muted">已创建：{{ subscription.id }}</p>
      </section>
    </div>
    <section class="nx-card connector-card">
      <h2>自用连接器</h2>
      <div v-for="item in connectors" :key="item.code" class="connector-row">
        <div><b>{{ item.name }}</b><div class="nx-muted nx-code">{{ item.code }} · {{ item.type }}</div></div>
        <button class="ghost" type="button" :disabled="running" @click="run(item.code)">立即运行</button>
      </div>
      <table v-if="runs.length" class="nx-table">
        <thead><tr><th>时间</th><th>连接器</th><th>状态</th></tr></thead>
        <tbody><tr v-for="item in runs" :key="item.id"><td>{{ item.startedAt }}</td><td>{{ item.connectorCode }}</td><td>{{ item.status }}</td></tr></tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { p5 } from "../../api/client";

const connectors=ref([]); const runs=ref([]); const issued=ref(null); const subscription=ref(null);
const error=ref(""); const running=ref(false); const clientName=ref("nexus-internal"); const scopeText=ref("integration:call");
const webhook=reactive({name:"internal-events",endpoint:"https://example.invalid/nexus-events",events:"record.changed"});
async function load(){error.value="";try{[connectors.value,runs.value]=await Promise.all([p5.connectors(),p5.connectorRuns()]);}catch(e){error.value=e.message;}}
async function issueClient(){try{issued.value=await p5.issueClient({name:clientName.value,scopes:scopeText.value.split(",").map(v=>v.trim()).filter(Boolean),rateLimitPerMinute:60});}catch(e){error.value=e.message;}}
async function subscribe(){try{subscription.value=await p5.createWebhook({name:webhook.name,endpoint:webhook.endpoint,eventTypes:webhook.events.split(",").map(v=>v.trim()).filter(Boolean)});}catch(e){error.value=e.message;}}
async function run(code){running.value=true;try{await p5.runConnector(code);runs.value=await p5.connectorRuns();}catch(e){error.value=e.message;}finally{running.value=false;}}
onMounted(load);
</script>

<style scoped>
.summary{margin-bottom:16px}.summary article{display:flex;flex-direction:column;gap:6px}.summary strong{font-size:28px;color:var(--nx-product)}
.panels{grid-template-columns:repeat(auto-fit,minmax(320px,1fr));margin-bottom:16px}.panels h2,.connector-card h2{margin-top:0;font-size:16px}
.secret{display:flex;flex-direction:column;gap:8px;margin-top:14px;padding:12px;border-radius:8px;background:var(--nx-primary-light)}.secret code{overflow-wrap:anywhere}
.connector-row{display:flex;justify-content:space-between;align-items:center;padding:12px 0;border-top:1px solid var(--nx-divider)}.nx-table{margin-top:14px}
</style>
