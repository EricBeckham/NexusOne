<template>
  <main class="operations-analytics" data-testid="operations-analytics">
    <NxPageHeader
      eyebrow="P4 · 星市"
      title="运营分析"
      description="轻量运营分析，非独立 BI。计数将来自已有上架 / 订阅 / 安装列表，不展示假 KPI。"
    >
      <template #actions>
        <router-link class="ghost" to="/p4/market">应用目录</router-link>
        <router-link class="ghost" to="/p4/entitlements">商业权益</router-link>
      </template>
    </NxPageHeader>

    <section class="ops-body">
      <NxEmpty
        data-testid="operations-empty"
        title="运营计数尚未接线"
        description="此为空壳页：无假 KPI、无假折线。上架 / 订阅 / 安装等真实计数将在后续卡从已有 list API 接入；无时间序列时不画趋势图。"
      >
        <template #action>
          <router-link class="primary" to="/p4/market">前往应用目录</router-link>
        </template>
      </NxEmpty>
    </section>
  </main>
</template>

<script setup>
import { NxEmpty, NxPageHeader } from "../../components";
</script>

<style scoped>
.operations-analytics {
  display: grid;
  gap: var(--nx-space-4);
}
.ops-body {
  display: grid;
  gap: var(--nx-space-3);
}
</style>
