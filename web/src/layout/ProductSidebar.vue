<template>
  <div class="side-head" :class="{ collapsed }">
    <span class="mark"><NxIcon :name="product.icon" :size="20" /></span>
    <div v-if="!collapsed">
      <strong>{{ product.name }}</strong>
      <div class="en">{{ product.en }}</div>
    </div>
    <button type="button" class="fold" :aria-label="collapsed ? '展开' : '收起'" :title="collapsed ? '展开' : '收起'" @click="$emit('toggle')">
      <NxIcon :name="collapsed ? 'chevronRight' : 'chevronLeft'" :size="18" />
    </button>
  </div>
  <nav class="menu" :aria-label="product.name">
    <template v-for="item in items" :key="item.id">
      <router-link v-if="destination(item)" :to="destination(item)" custom v-slot="{ href, navigate }">
        <a :href="href" :title="item.label" :class="{ current: isCurrent(destination(item)) }" @click="navigate">
          <NxIcon class="icon" :name="iconFor(item)" :size="20" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </a>
      </router-link>
      <div v-else-if="item.later" class="later" :title="item.label">
        <NxIcon class="icon" :name="iconFor(item)" :size="20" />
        <span v-if="!collapsed">{{ item.label }}</span>
        <span v-if="!collapsed" class="tag">后续</span>
      </div>
      <div v-else-if="item.children?.length" class="group" :class="{ open: groupActive(item) }">
        <div class="group-label" :title="item.label">
          <NxIcon class="icon" :name="iconFor(item)" :size="20" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </div>
        <div v-if="!collapsed" class="children">
          <router-link v-for="child in item.children" :key="child.id" :to="child.to" custom v-slot="{ href, navigate }">
            <a :href="href" :class="{ current: isCurrent(child.to) }" @click="navigate"><NxIcon class="child-icon" :name="iconFor(child)" :size="16" />{{ child.label }}</a>
          </router-link>
        </div>
      </div>
      <div v-else class="later" :title="item.detailBase ? `${item.label}，从列表进入` : item.label">
        <NxIcon class="icon" :name="iconFor(item)" :size="20" />
        <span v-if="!collapsed">{{ item.label }}</span>
      </div>
    </template>
  </nav>
</template>

<script setup>
import { useRoute } from "vue-router";
import NxIcon from "../icons/NxIcon.vue";

const props = defineProps({
  product: { type: Object, required: true },
  items: { type: Array, required: true },
  collapsed: { type: Boolean, default: false },
});
defineEmits(["toggle"]);

const route = useRoute();

function destination(item) {
  const id = route.params.id;
  const onTenant = id && id !== "new" && route.path.startsWith("/p1/tenants/");
  if (item.detailSuffix && onTenant) {
    return `/p1/tenants/${id}${item.detailSuffix}`;
  }
  if (item.to) {
    return item.to;
  }
  if (item.detailBase && onTenant && (route.name === "tenant-detail" || route.name === "tenant-entitlement")) {
    return `${item.detailBase}/${id}`;
  }
  return "";
}

function menuLinks() {
  const links = [];
  for (const item of props.items) {
    const to = destination(item);
    if (to) {
      links.push(to);
    }
    for (const child of item.children || []) {
      if (child.to) {
        links.push(child.to);
      }
    }
  }
  return links;
}

function activeTarget() {
  const path = route.path;
  let best = "";
  for (const to of menuLinks()) {
    if ((path === to || path.startsWith(`${to}/`)) && to.length > best.length) {
      best = to;
    }
  }
  return best;
}

function isCurrent(to) {
  return activeTarget() === to;
}

function groupActive(item) {
  return (item.children || []).some((child) => isCurrent(child.to));
}

const iconsByItem = {
  home: "home", tenants: "building", "tenant-create": "userPlus", "tenant-detail": "building",
  relations: "network", quotas: "gauge", service: "server", audit: "shield",
  persons: "users", org: "network", positions: "briefcase", permissions: "key", security: "shield",
  totp: "phone", recovery: "key", email: "mail", ledgers: "table", forms: "file", todos: "check",
  reports: "chart", dumps: "archive", exports: "download", notifications: "bell", "workflow-designer": "workflow",
  "publish-center": "upload", market: "grid", packages: "package", skus: "tag", subscriptions: "card",
  "install-upgrade": "arrowUp", entitlements: "gift", operations: "chart", connect: "plug", apis: "code",
  "api-clients": "code", webhooks: "webhook", connectors: "network", credentials: "key", "call-logs": "clock",
};
function iconFor(item) { return iconsByItem[item.id] || "grid"; }
</script>

<style scoped>
.side-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 72px;
  margin-bottom: 8px;
}
.side-head strong { display: block; font-size: 16px; }
.en { color: var(--nx-text-secondary); font-size: 12px; }
.mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--nx-product);
  color: #fff;
  flex: none;
}
.fold {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--nx-text-secondary);
  cursor: pointer;
  font-size: 12px;
}
.menu a, .group-label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--nx-text-regular);
  font-size: 14px;
}
.menu a.current {
  background: var(--nx-product-light);
  color: var(--nx-product);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--nx-product);
}
.side-head.collapsed { gap: 4px; }
.side-head.collapsed .fold { margin-left: 0; padding: 0; }
.later {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 10px;
  color: var(--nx-text-disabled);
  font-size: 14px;
}
.tag { margin-left: auto; color: var(--nx-text-secondary); font-size: 12px; }
.icon { width: 20px; text-align: center; flex: none; color: currentColor; }
.children a { display: flex; align-items: center; gap: 8px; padding-left: 38px; }
.child-icon { flex: none; color: var(--nx-text-secondary); }
.group.open > .group-label { color: var(--nx-product); }
</style>
