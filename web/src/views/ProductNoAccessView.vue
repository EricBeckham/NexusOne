<template>
  <div class="no-access" data-testid="product-no-access">
    <div class="nx-page-header">
      <h1>无权访问该产品</h1>
      <p>{{ productName ? `当前账号在所在租户中没有「${productName}」的使用范围。` : "当前账号没有这个产品的使用范围。" }}如需开通，请联系管理员。</p>
    </div>
    <div v-if="items.length" class="choices">
      <router-link v-for="item in items" :key="item.id" :to="item.home" class="nx-card choice" :data-product="item.id">
        <NxIcon :name="item.icon" :size="18" />
        <span>进入{{ item.name }}</span>
      </router-link>
    </div>
    <p v-else class="nx-muted" data-testid="product-no-access-empty">当前账号还没有可进入的产品。</p>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { session } from "../api/session";
import { productById } from "../nav/products";
import { visibleProducts } from "../nav/productAccess";
import NxIcon from "../icons/NxIcon.vue";

const route = useRoute();
const productName = computed(() => productById(String(route.query.product || ""))?.name || "");
const items = computed(() => visibleProducts(session.products));
</script>

<style scoped>
.choices { display: flex; flex-wrap: wrap; gap: 12px; }
.choice { display: inline-flex; align-items: center; gap: 8px; color: var(--nx-text-primary); }
</style>
