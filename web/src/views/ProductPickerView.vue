<template>
  <div>
    <div class="nx-page-header">
      <h1>选择产品</h1>
      <p v-if="memberHome">进入成员门户。</p>
      <p v-else-if="items.length">进入{{ items.map((item) => item.name).join("、") }}。</p>
      <p v-else data-testid="product-picker-empty">当前账号在所在租户中还没有可进入的产品，请联系管理员。</p>
    </div>
    <div class="picker">
      <router-link v-if="memberHome" to="/portal" class="nx-card card" data-testid="member-portal" data-product="portal">
        <span class="mark"><NxIcon name="layers" :size="22" /></span>
        <strong>门户</strong>
        <span class="nx-muted">办理入口</span>
      </router-link>
      <router-link v-for="item in memberHome ? [] : items" :key="item.id" :to="item.home" class="nx-card card" :data-product="item.id">
        <span class="mark"><NxIcon :name="item.icon" :size="22" /></span>
        <strong>{{ item.name }}</strong>
        <span class="nx-muted">{{ item.en }}</span>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { session } from "../api/session";
import { memberPortalHome, visibleProducts } from "../nav/productAccess";
import NxIcon from "../icons/NxIcon.vue";

// PV3: the server decides which products this person may enter (GET /api/v1/auth/products).
const items = computed(() => visibleProducts(session.products));
const memberHome = computed(() => memberPortalHome(session.products));
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
