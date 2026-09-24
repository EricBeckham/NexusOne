<template>
  <div>
    <div class="m-hero"><h2>星联</h2><p>NexusOne 自用连接器运行入口。</p></div>
    <p v-if="error" class="m-error">{{ error }}</p>
    <p v-if="loading" class="m-muted">正在加载…</p>
    <div v-for="item in connectors" :key="item.code" class="m-card">
      <h3>{{ item.name }}</h3>
      <p class="m-muted">{{ item.code }} · {{ item.type }}</p>
      <button class="m-btn primary" type="button" :disabled="running" @click="run(item.code)">运行自检</button>
    </div>
    <div v-if="lastRun" class="m-card"><h3>最近结果</h3><p class="m-ok">{{ lastRun.status }}</p><p class="m-muted">{{ lastRun.startedAt }}</p></div>
    <p class="m-muted">当前不提供第三方连接器市场或开发者门户。</p>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p5 } from "../../../../api/client";
const connectors=ref([]);const lastRun=ref(null);const loading=ref(true);const running=ref(false);const error=ref("");
async function load(){try{connectors.value=await p5.connectors();const runs=await p5.connectorRuns();lastRun.value=runs[0]||null;}catch(e){error.value=e.message;}finally{loading.value=false;}}
async function run(code){running.value=true;try{lastRun.value=await p5.runConnector(code);}catch(e){error.value=e.message;}finally{running.value=false;}}
onMounted(load);
</script>
