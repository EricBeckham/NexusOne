<template>
  <nav v-if="items.length > 1" class="switcher" :class="{ collapsed }" aria-label="产品切换" data-testid="product-switcher">
    <p v-if="!collapsed">切换产品</p>
    <div class="row" :style="{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }">
      <router-link
        v-for="item in items"
        :key="item.id"
        :to="item.home"
        :title="item.name"
        :data-product="item.id"
        :class="{ current: item.id === currentId }"
        :aria-current="item.id === currentId ? 'true' : undefined"
      >
        <span class="mark"><NxIcon :name="item.icon" :size="18" /></span>
        <span class="name">{{ item.name }}</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup>
import { computed } from "vue";
import { session } from "../api/session";
import { visibleProducts } from "../nav/productAccess";
import NxIcon from "../icons/NxIcon.vue";

// PV3: only the products this person may enter; a single product needs no switcher.
const items = computed(() => visibleProducts(session.products));

defineProps({
  currentId: { type: String, default: "" },
  collapsed: { type: Boolean, default: false },
});
</script>

<style scoped>
/* A compact product row, not a second menu (O8): five marks with short names under a divider. */
.switcher { margin-top: var(--nx-space-4); padding-top: var(--nx-space-3); border-top: 1px solid var(--nx-border); }
.switcher p {
  margin: 0 10px 8px;
  font-size: 12px;
  color: var(--nx-text-secondary);
}
.row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 2px; }
.switcher a {
  display: grid;
  justify-items: center;
  gap: 2px;
  padding: 6px 0;
  border-radius: 8px;
  color: var(--nx-text-secondary);
  font-size: 11px;
}
.switcher a:hover { background: var(--nx-bg-page); color: var(--nx-text-primary); }
.switcher a.current { color: var(--nx-product); font-weight: 600; }
.mark {
  width: 28px;
  height: 28px;
  border-radius: var(--nx-radius-sm);
  display: grid;
  place-items: center;
  background: var(--nx-bg-page);
  color: var(--nx-text-secondary);
}
.switcher a.current .mark { background: var(--nx-product-light); color: var(--nx-product); }
.collapsed .row { grid-template-columns: 1fr; }
.collapsed .name { display: none; }
</style>
