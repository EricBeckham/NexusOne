<template>
  <div>
    <div class="m-hero">
      <h2>星市</h2>
      <p>发现并安装已审核发布的应用。</p>
    </div>
    <p v-if="loading" class="m-muted">正在加载目录…</p>
    <p v-if="error" class="m-error">{{ error }}</p>
    <div v-if="!loading && !items.length" class="m-card"><p class="m-muted">暂无已发布应用</p></div>
    <div v-for="item in items" :key="item.version.id" class="m-card">
      <h3>{{ item.app.name }}</h3>
      <p class="m-muted">{{ item.app.summary || item.app.code }} · {{ item.version.version }}</p>
      <button v-for="sku in item.skus" :key="sku.code" class="m-btn primary" type="button" @click="install(sku.code,item.version.id)">
        安装 {{ sku.name || sku.code }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p4 } from "../../../../api/client";
import { session } from "../../../../api/session";

const items = ref([]); const loading = ref(true); const error = ref("");
async function load(){try{items.value=(await p4.catalog())||[];}catch(e){error.value=e.message;}finally{loading.value=false;}}
async function install(skuCode,versionId){
  if(!session.tenantId){error.value="请先建立租户会话";return;}
  try{await p4.install(session.tenantId,{skuCode,versionId});alert("安装完成，订阅权益已投影");}catch(e){error.value=e.message;}
}
onMounted(load);
</script>
