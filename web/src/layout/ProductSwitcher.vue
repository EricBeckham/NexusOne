<template>
  <nav class="switcher" aria-label="产品切换">
    <p v-if="!collapsed">产品</p>
    <router-link
      v-for="item in PRODUCTS"
      :key="item.id"
      :to="item.home"
      :title="item.name"
      :data-product="item.id"
      :class="{ current: item.id === currentId }"
    >
      <span class="mark"><NxIcon :name="item.icon" :size="18" /></span>
      <span v-if="!collapsed">{{ item.name }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { PRODUCTS } from "../nav/products";
import NxIcon from "../icons/NxIcon.vue";

defineProps({
  currentId: { type: String, default: "" },
  collapsed: { type: Boolean, default: false },
});
</script>

<style scoped>
.switcher { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; }
.switcher p {
  margin: 8px;
  font-size: 11px;
  color: var(--nx-text-secondary);
}
.switcher a {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--nx-text-regular);
}
.switcher a.current {
  background: var(--nx-product-light);
  color: var(--nx-product);
  font-weight: 600;
}
.mark {
  width: 28px;
  height: 28px;
  border-radius: var(--nx-radius-sm);
  display: grid;
  place-items: center;
  background: var(--nx-product-light);
  color: var(--nx-product);
  flex: none;
}
</style>
