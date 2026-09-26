<template>
  <main class="platform-services-page">
    <NxPageHeader
      eyebrow="星域 / 平台运维"
      title="平台服务态"
      description="只读探测运行依赖；探测不可用时如实显示 UNKNOWN，不代表 APM 或历史监控。"
      :crumbs="[{ label: '星域' }, { label: '平台服务态' }]"
    >
      <template #actions>
        <button class="ghost" type="button" :disabled="loading" @click="load">{{ loading ? "探测中…" : "重新探测" }}</button>
      </template>
    </NxPageHeader>

    <NxPanelCard title="组件探测" eyebrow="实时状态" description="每个组件独立探测，单项异常不会影响其它结果。">
      <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
      <div v-else-if="loading" class="service-skeleton" data-testid="services-loading" role="status">
        <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在探测平台服务" />
      </div>
      <NxEmpty v-else-if="snapshot && !services.length" title="暂无组件数据" description="服务状态接口没有返回组件。可重新探测或稍后再试。">
        <template #next>
          <button class="ghost" type="button" @click="load">重新探测</button>
        </template>
      </NxEmpty>
      <div v-else-if="services.length" class="service-grid" aria-live="polite">
        <article
          v-for="service in services"
          :key="service.code"
          class="service-card"
          :data-status="service.status"
          data-testid="service-card"
        >
          <div class="service-heading">
            <div>
              <p class="service-code">{{ service.code }}</p>
              <h2>{{ service.name }}</h2>
            </div>
            <NxStatusTag
              :status="service.status || 'UNKNOWN'"
              :label="statusLabel(service.status)"
              :tone="statusTone(service.status)"
            />
          </div>
          <p class="service-message">{{ service.message || "暂无状态说明" }}</p>
          <p class="service-time">探测时间：{{ formatTime(service.checkedAt) }}</p>
        </article>
      </div>
      <p v-if="snapshot?.checkedAt" class="snapshot-time">本次汇总：{{ formatTime(snapshot.checkedAt) }}</p>
    </NxPanelCard>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader, NxPanelCard, NxSkeleton, NxStatusTag } from "../../components";

const snapshot = ref(null);
const error = ref("");
const traceId = ref("");
const loading = ref(true);
const services = computed(() => (Array.isArray(snapshot.value?.services) ? snapshot.value.services : []));

function statusLabel(status) {
  return ({ UP: "正常", DOWN: "异常", UNKNOWN: "未知" })[status] || "未知";
}

function statusTone(status) {
  return ({ UP: "success", DOWN: "danger", UNKNOWN: "neutral" })[status] || "neutral";
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
.platform-services-page { display: grid; gap: var(--nx-space-4); }
.platform-services-page :deep(.page-header) { margin-bottom: 0; }
.service-skeleton { display: grid; gap: var(--nx-space-2); }
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: var(--nx-space-3);
}
.service-card {
  min-width: 0;
  padding: var(--nx-space-4);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius);
  background: var(--nx-surface);
}
.service-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nx-space-3);
}
.service-code {
  margin: 0 0 var(--nx-space-1);
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.service-heading h2 {
  margin: 0;
  color: var(--nx-text-primary);
  font-size: 16px;
  font-weight: 600;
}
.service-message {
  min-height: 42px;
  margin: var(--nx-space-4) 0 var(--nx-space-3);
  color: var(--nx-text-regular);
  line-height: 1.6;
}
.service-time,
.snapshot-time {
  margin: 0;
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.snapshot-time { margin-top: var(--nx-space-4); }
.ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--nx-control-height);
  padding: 0 var(--nx-space-3);
  border-radius: var(--nx-control-radius);
  color: var(--nx-text-regular);
  background: var(--nx-surface);
  border: 1px solid var(--nx-border-strong);
  cursor: pointer;
}
.ghost:disabled { opacity: .55; cursor: not-allowed; }
</style>
