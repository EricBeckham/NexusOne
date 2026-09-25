<template>
  <div class="shell" :class="{ collapsed, platform: session.platformMode }" :data-product="productId || undefined">
    <aside class="side">
      <ProductSidebar
        v-if="product"
        :product="product"
        :items="items"
        :collapsed="collapsed"
        @toggle="collapsed = !collapsed"
      />
      <div v-else class="picker-brand" data-testid="shell-brand">
        <span class="brand-mark" aria-hidden="true">星</span>
        <div v-if="!collapsed">
          <strong>星枢</strong>
          <div class="brand-en">NexusOne · 选择产品</div>
        </div>
      </div>
      <ProductSwitcher :current-id="productId" :collapsed="collapsed" />
    </aside>
    <div class="main">
      <GlobalHeader />
      <section class="content">
        <div class="content-inner">
          <router-view />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { session } from "../api/session";
import { collectLinks, navFor } from "../nav";
import { productById } from "../nav/products";
import GlobalHeader from "./GlobalHeader.vue";
import ProductSidebar from "./ProductSidebar.vue";
import ProductSwitcher from "./ProductSwitcher.vue";

const REQUIRED_SHELL_LINKS = [
  "/p3/notifications",
  "/p3/audit",
  "/p2/mfa/enroll",
  "/p2/mfa/recovery",
];

const route = useRoute();
const collapsed = ref(false);
const productId = computed(() => route.meta.product || "");
const product = computed(() => productById(productId.value));
const items = computed(() => navFor(productId.value));

const linked = collectLinks([...navFor("p2"), ...navFor("p3")]);
for (const path of REQUIRED_SHELL_LINKS) {
  if (!linked.includes(path)) {
    console.error(`AppShell menu missing ${path}`);
  }
}
</script>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--nx-sidebar) 1fr;
  min-height: 100%;
}
.shell.collapsed { --nx-sidebar: var(--nx-sidebar-collapsed); }
.shell.collapsed .side { padding-left: 8px; padding-right: 8px; }
.shell.platform { background: var(--nx-bg-page); }
.side {
  background: var(--nx-bg-sidebar);
  border-right: 1px solid var(--nx-border);
  padding: 8px 12px 16px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
}
.picker-brand { min-height: 72px; margin-bottom: 8px; display: flex; align-items: center; gap: 10px; }
.picker-brand strong { display: block; font-size: 16px; }
.brand-en { color: var(--nx-text-secondary); font-size: 12px; }
.brand-mark {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--nx-primary);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}
.main { display: flex; flex-direction: column; min-width: 0; }
.content { padding: 24px; }
/* Spec §3.1: desktop design width 1440–1920, master ≈1586px; wider screens center the page instead of stretching it. */
.content-inner { max-width: var(--nx-content-max, 1600px); margin: 0 auto; }
</style>
