<template>
  <header class="top" :class="{ platform: session.platformMode }">
    <div class="lead">
      <div class="crumb">{{ crumb }}</div>
      <span v-if="session.platformMode" class="platform-flag">平台视图 · 跨租户</span>
    </div>
    <label class="search">
      <span class="sr">全局搜索</span>
      <input
        ref="searchEl"
        type="search"
        placeholder="搜索，暂无"
        readonly
        aria-disabled="true"
      />
    </label>
    <div class="actions">
      <span class="env" :data-tone="env.tone">
        <i />
        {{ env.label }}
      </span>
      <router-link class="notify" to="/p3/notifications">通知</router-link>
      <div class="user">
        <button type="button" class="user-btn" @click="open = !open">
          {{ session.displayName || "未登录" }}
        </button>
        <div v-if="open" class="menu">
          <p><span>用户名</span><strong>{{ session.displayName || "未登录" }}</strong></p>
          <p><span>租户</span><strong>{{ session.tenantId || "暂无" }}</strong></p>
          <p><span>岗位</span><strong>暂无</strong></p>
          <label class="switch">
            <input v-model="session.platformMode" type="checkbox" @change="persist" />
            平台操作
          </label>
          <label v-if="session.memberships.length > 1" class="switch">
            切换租户
            <select :value="session.tenantId" @change="onSwitch($event.target.value)">
              <option value="" disabled>切换租户</option>
              <option v-for="item in session.memberships" :key="itemKey(item)" :value="itemKey(item)">
                {{ itemKey(item) }}
              </option>
            </select>
          </label>
          <button type="button" class="link" @click="refresh">刷新会话</button>
          <button type="button" class="link" @click="logout">退出</button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { clearIdentity, ensureContext, persist, session, switchTenant } from "../api/session";
import { productById } from "../nav/products";

const route = useRoute();
const router = useRouter();
const open = ref(false);
const searchEl = ref(null);

const crumb = computed(() => {
  const product = productById(route.meta.product);
  const title = route.meta.title || "NexusOne";
  return product ? `${product.name} / ${title}` : title;
});

const env = computed(() => {
  if (import.meta.env.MODE === "test") {
    return { label: "测试环境", tone: "test" };
  }
  if (import.meta.env.PROD) {
    return { label: "生产环境", tone: "prod" };
  }
  return { label: "开发环境", tone: "dev" };
});

function itemKey(item) {
  return item.tenant_id || item.tenantId || "";
}

async function onSwitch(tenantId) {
  try {
    await switchTenant(tenantId);
  } catch (error) {
    session.error = error.message;
    alert(error.message);
  }
}

async function refresh() {
  try {
    await ensureContext();
  } catch (error) {
    alert(error.message);
  }
}

function logout() {
  clearIdentity();
  open.value = false;
  router.push("/login");
}

function onKey(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    searchEl.value?.focus();
  }
}

onMounted(() => window.addEventListener("keydown", onKey));
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>

<style scoped>
.top {
  height: var(--nx-header);
  background: var(--nx-surface);
  border-bottom: 1px solid var(--nx-border);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 2;
}
.top.platform { box-shadow: inset 0 -3px 0 var(--nx-warning); }
.lead { display: flex; align-items: center; gap: 12px; min-width: 0; }
.crumb { color: var(--nx-text-primary); font-weight: 600; white-space: nowrap; }
.platform-flag {
  color: var(--nx-warning);
  background: var(--nx-warning-light);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  white-space: nowrap;
}
.search { flex: 1; display: flex; justify-content: center; }
.search input {
  width: min(420px, 100%);
  min-width: 240px;
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  padding: 6px 10px;
  background: var(--nx-bg-page);
  color: var(--nx-text-secondary);
}
.actions { display: flex; align-items: center; gap: 12px; }
.env { display: flex; align-items: center; gap: 6px; color: var(--nx-text-secondary); font-size: 13px; }
.env i { width: 8px; height: 8px; border-radius: 50%; background: var(--nx-info); display: block; }
.env[data-tone="prod"] i { background: var(--nx-success); }
.env[data-tone="test"] i { background: var(--nx-warning); }
.notify { font-size: 13px; }
.user { position: relative; }
.user-btn, .link {
  background: none;
  border: 0;
  color: var(--nx-primary);
  cursor: pointer;
  font-size: 13px;
}
.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 240px;
  background: var(--nx-surface);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  box-shadow: var(--nx-shadow-popover);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.menu p { margin: 0; display: flex; justify-content: space-between; gap: 8px; font-size: 13px; }
.menu span { color: var(--nx-text-secondary); }
.switch { display: flex; gap: 6px; align-items: center; color: var(--nx-text-secondary); font-size: 13px; }
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
