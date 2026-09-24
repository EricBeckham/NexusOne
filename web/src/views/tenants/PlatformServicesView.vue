<template>
  <NxPageHeader eyebrow="星域 / 平台运维" title="平台服务态" description="只读探测运行依赖；探测不可用时如实显示 UNKNOWN，不代表 APM 或历史监控。">
    <template #actions>
      <button class="ghost" type="button" :disabled="loading" @click="load">{{ loading ? "探测中…" : "重新探测" }}</button>
    </template>
  </NxPageHeader>

  <NxPanelCard title="组件探测" eyebrow="实时状态" description="每个组件独立探测，单项异常不会影响其它结果。">
    <NxErrorState v-if="error" :title="error" :trace-id="traceId" @retry="load" />
    <NxEmpty v-else-if="snapshot && !services.length" title="暂无" description="服务状态接口没有返回组件数据。" />
    <div v-else-if="services.length" class="service-grid" aria-live="polite">
      <article v-for="service in services" :key="service.code" class="service-card" :data-status="service.status">
        <div class="service-heading">
          <div>
            <p class="service-code">{{ service.code }}</p>
            <h2>{{ service.name }}</h2>
          </div>
          <span class="status-pill" :data-status="service.status">{{ statusLabel(service.status) }}</span>
        </div>
        <p class="service-message">{{ service.message || "暂无状态说明" }}</p>
        <p class="service-time">探测时间：{{ formatTime(service.checkedAt) }}</p>
      </article>
    </div>
    <p v-else-if="loading" class="loading-note" role="status">正在探测平台服务…</p>
    <p v-if="snapshot?.checkedAt" class="snapshot-time">本次汇总：{{ formatTime(snapshot.checkedAt) }}</p>
  </NxPanelCard>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard } from "../../components";

const snapshot = ref(null);
const error = ref("");
const traceId = ref("");
const loading = ref(false);
const services = computed(() => (Array.isArray(snapshot.value?.services) ? snapshot.value.services : []));

function statusLabel(status) {
  return ({ UP: "正常", DOWN: "异常", UNKNOWN: "未知" })[status] || "未知";
}

function formatTime(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : date.toLocaleString("zh-CN", { hour12: false });
}

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  snapshot.value = null;
  try {
    const result = await p1.serviceStatus();
    snapshot.value = result && typeof result === "object" ? result : { services: [] };
  } catch (err) {
    error.value = err?.message || "平台服务状态加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.service-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr)); gap: 14px; }
.service-card { min-width: 0; padding: 18px; border: 1px solid var(--nx-border, #e5eaf2); border-radius: 14px; background: var(--nx-surface, #fff); }
.service-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.service-code { margin: 0 0 5px; color: var(--nx-text-muted, #718096); font-size: 12px; }
.service-heading h2 { margin: 0; color: var(--nx-text, #172b4d); font-size: 16px; }
.status-pill { flex: none; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #526176; background: #edf1f6; }
.status-pill[data-status="UP"] { color: #18794e; background: #e7f7ee; }
.status-pill[data-status="DOWN"] { color: #b42318; background: #fff0ee; }
.service-message { min-height: 42px; margin: 17px 0 12px; color: var(--nx-text, #344054); line-height: 1.6; }
.service-time, .snapshot-time, .loading-note { margin: 0; color: var(--nx-text-muted, #718096); font-size: 12px; }
.snapshot-time { margin-top: 16px; }
</style>
