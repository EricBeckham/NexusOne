<template>
  <div>
    <div class="nx-page-header">
      <h1>选择产品</h1>
      <p>进入星域、星权、星构、星市或星联。</p>
    </div>
    <div class="picker">
      <router-link v-for="item in PRODUCTS" :key="item.id" :to="item.home" class="nx-card card" :data-product="item.id">
        <span class="mark"><NxIcon :name="item.icon" :size="22" /></span>
        <strong>{{ item.name }}</strong>
        <span class="nx-muted">{{ item.en }}</span>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { PRODUCTS } from "../nav/products";
import NxIcon from "../icons/NxIcon.vue";
</script>

<style scoped>
.picker { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.card { display: flex; flex-direction: column; gap: 6px; color: var(--nx-text-primary); }
.mark {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--nx-product-light);
  color: var(--nx-product);
}
</style>
