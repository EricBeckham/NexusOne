<template>
  <div class="product-home" data-testid="product-home">
    <div class="nx-page-header hero">
      <p class="eyebrow">NEXUSONE</p>
      <h1>{{ greeting }}</h1>
      <p>选择要进入的产品。会话与租户上下文会在产品之间保持。</p>
    </div>
    <div class="product-grid" aria-label="产品入口">
      <router-link
        v-for="item in products"
        :key="item.id"
        class="product-card"
        :to="item.path"
        :data-testid="`${item.id}-entry`"
        :style="{ '--card-color': item.color }"
      >
        <span class="product-mark">{{ item.name.slice(0, 1) }}</span>
        <span class="product-copy">
          <strong>{{ item.name }}</strong>
          <small>{{ item.summary }}</small>
        </span>
        <span class="arrow" aria-hidden="true">→</span>
      </router-link>
    </div>
    <div class="session-line">
      <span>{{ session.email || "已建立的会话" }}</span>
      <span aria-hidden="true">·</span>
      <span>{{ session.tenantId ? `租户 ${session.tenantId}` : "租户上下文待签发" }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { session } from "../api/session";
import { products } from "../products/catalog";

const greeting = computed(() => session.displayName ? `你好，${session.displayName}` : "选择产品");
</script>

<style scoped>
.product-home { max-width: 1040px; margin: 36px auto; }
.hero { margin-bottom: 28px; }
.hero h1 { margin-bottom: 8px; font-size: 30px; }
.eyebrow { margin-bottom: 8px !important; color: var(--nx-primary) !important; font-size: 12px; font-weight: 700; letter-spacing: .14em; }
.product-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.product-card { min-height: 168px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 16px; padding: 24px; border: 1px solid var(--nx-border); border-top: 4px solid var(--card-color); border-radius: 16px; background: var(--nx-surface); box-shadow: var(--nx-shadow-card); color: var(--nx-text-primary); transition: transform .16s ease, box-shadow .16s ease; }
.product-card:hover { transform: translateY(-3px); box-shadow: 0 12px 28px rgba(31, 56, 88, .12); }
.product-mark { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; background: var(--card-color); color: #fff; font-size: 20px; font-weight: 700; }
.product-copy { display: flex; flex-direction: column; gap: 5px; }
.product-copy strong { font-size: 20px; }
.product-copy small { color: var(--nx-text-secondary); }
.arrow { color: var(--card-color); font-size: 24px; }
.session-line { display: flex; justify-content: center; gap: 8px; margin-top: 28px; color: var(--nx-text-secondary); font-size: 12px; }
@media (max-width: 900px) { .product-grid { grid-template-columns: 1fr; } .product-card { min-height: 120px; } }
</style>
