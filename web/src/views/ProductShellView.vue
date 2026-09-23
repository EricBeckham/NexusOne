<template>
  <div class="product-shell" :data-testid="`${product.id}-product-shell`">
    <div class="product-badge" aria-hidden="true">{{ product.name.slice(0, 1) }}</div>
    <p class="eyebrow">{{ product.id.toUpperCase() }} PRODUCT SHELL</p>
    <h1>{{ product.name }}</h1>
    <p>{{ product.summary }}</p>
    <div class="shell-state"><span class="state-dot" />产品壳已接通，业务入口将在后续薄切任务中逐步开放</div>
    <router-link class="ghost" to="/">返回产品选择</router-link>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { productById } from "../products/catalog";

const route = useRoute();
const product = computed(() => productById(route.meta.product));
</script>

<style scoped>
.product-shell { min-height: 430px; display: grid; place-content: center; justify-items: center; text-align: center; border: 1px solid var(--nx-border); border-radius: 20px; background: radial-gradient(circle at 50% 0, var(--nx-primary-light), #fff 58%); }
.product-badge { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 18px; color: #fff; background: var(--nx-product); box-shadow: 0 14px 28px color-mix(in srgb, var(--nx-product) 25%, transparent); font-size: 28px; font-weight: 700; }
.eyebrow { margin: 22px 0 4px; color: var(--nx-product); font-size: 12px; font-weight: 700; letter-spacing: .12em; }
h1 { margin: 0; font-size: 34px; }
.product-shell > p:not(.eyebrow) { margin: 8px 0 20px; color: var(--nx-text-secondary); font-size: 16px; }
.shell-state { margin-bottom: 24px; padding: 10px 14px; border-radius: 999px; background: var(--nx-success-light); color: var(--nx-text-regular); }
.state-dot { display: inline-block; width: 8px; height: 8px; margin-right: 8px; border-radius: 50%; background: var(--nx-success); }
</style>
