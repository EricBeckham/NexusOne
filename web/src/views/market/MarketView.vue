<template>
  <div>
    <div class="nx-page-header">
      <h1>星市</h1>
      <p>发现已审核发布的应用，为当前租户选择 SKU 并安装。</p>
    </div>
    <div class="nx-row" style="margin-bottom:16px">
      <button class="primary" type="button" @click="load">刷新目录</button>
      <span class="nx-muted">{{ items.length }} 个可用版本</span>
    </div>
    <p v-if="loading" class="nx-muted">正在加载…</p>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <div v-if="!loading && !items.length" class="nx-card empty">暂无已发布应用</div>
    <div class="nx-grid catalog">
      <article v-for="item in items" :key="item.version.id" class="nx-card app-card">
        <div class="badge">{{ item.app.code }}</div>
        <h2>{{ item.app.name }}</h2>
        <p>{{ item.app.summary || "暂无简介" }}</p>
        <p class="nx-muted">版本 {{ item.version.version }}</p>
        <div class="sku-list">
          <button v-for="sku in item.skus" :key="sku.code" class="ghost" type="button" @click="install(sku.code, item.version.id)">
            安装 {{ sku.name || sku.code }}
          </button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p4 } from "../../api/client";
import { session } from "../../api/session";

const items = ref([]);
const loading = ref(false);
const error = ref("");

async function load() {
  loading.value = true; error.value = "";
  try { items.value = (await p4.catalog()) || []; }
  catch (err) { error.value = err.message; }
  finally { loading.value = false; }
}

async function install(skuCode, versionId) {
  if (!session.tenantId) { error.value = "请先建立租户会话"; return; }
  try { await p4.install(session.tenantId, { skuCode, versionId }); alert("安装完成，订阅权益已投影"); }
  catch (err) { error.value = err.message; }
}

onMounted(load);
</script>

<style scoped>
.catalog { grid-template-columns: repeat(auto-fill,minmax(260px,1fr)); }
.app-card { min-height: 210px; display:flex; flex-direction:column; gap:8px; }
.app-card h2,.app-card p { margin:0; }
.badge { align-self:flex-start; padding:3px 8px; border-radius:999px; background:var(--nx-primary-light); color:var(--nx-product); font-size:12px; }
.sku-list { display:flex; flex-wrap:wrap; gap:8px; margin-top:auto; }
.empty { text-align:center; padding:48px; }
</style>
