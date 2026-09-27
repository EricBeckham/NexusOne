<template>
  <header class="top" :class="{ platform: session.platformMode }">
    <div class="lead">
      <nav class="crumb" aria-label="面包屑" data-testid="shell-crumb">
        <template v-if="crumb.product">
          <router-link v-if="crumb.page" class="crumb-product" :to="crumb.product.home">{{ crumb.product.name }}</router-link>
          <span v-else class="crumb-page" aria-current="page">{{ crumb.product.name }}</span>
          <span v-if="crumb.page" class="crumb-sep" aria-hidden="true">/</span>
        </template>
        <span v-if="crumb.page" class="crumb-page" aria-current="page">{{ crumb.page }}</span>
      </nav>
      <span v-if="session.platformMode" class="platform-flag">平台视图 · 跨租户</span>
    </div>
    <label class="search">
      <span class="sr">全局搜索</span>
      <NxIcon name="search" :size="18" />
      <input
        ref="searchEl"
        type="search"
        placeholder="全局搜索（即将开放）"
        readonly
        aria-disabled="true"
      />
      <kbd class="hotkey" aria-hidden="true">Ctrl K</kbd>
    </label>
    <div class="actions">
      <span class="env" :data-tone="env.tone">
        <i />
        {{ env.label }}
      </span>
      <router-link class="notify" to="/p3/notifications"><NxIcon name="bell" :size="18" /><span>通知</span></router-link>
      <div class="user">
        <button type="button" class="user-btn" :aria-expanded="open" data-testid="shell-user" @click="open = !open">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ session.displayName || "未登录" }}</span>
          <NxIcon name="chevronDown" :size="14" />
        </button>
        <div v-if="open" class="menu">
          <p><span>用户名</span><strong>{{ session.displayName || "未登录" }}</strong></p>
          <p><span>租户</span><strong data-testid="shell-tenant">{{ currentTenantLabel(session) }}</strong></p>
          <p><span>岗位</span><strong data-testid="shell-position">{{ session.positionName || "暂无" }}</strong></p>
          <label class="switch">
            <input v-model="session.platformMode" type="checkbox" @change="persist" />
            平台操作
          </label>
          <label v-if="session.memberships.length > 1" class="switch">
            切换租户
            <select :value="session.tenantId" data-testid="shell-tenant-switch" @change="onSwitch($event.target.value)">
              <option value="" disabled>切换租户</option>
              <option v-for="item in session.memberships" :key="itemKey(item)" :value="itemKey(item)">
                {{ tenantLabel(item) }}
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
import { clearIdentity, ensureContext, loadPrimaryPosition, persist, session, switchTenant } from "../api/session";
import { p2 } from "../api/client";
import { endSessionTarget, leaveTo } from "../auth/federatedLogout";
import { currentTenantLabel, tenantLabel } from "../api/tenantLabel";
import { productById } from "../nav/products";
import NxIcon from "../icons/NxIcon.vue";

const route = useRoute();
const router = useRouter();
const open = ref(false);
const searchEl = ref(null);

// Product › page; a product home whose title is the product name shows once (no "星市 / 星市").
const crumb = computed(() => {
  const product = productById(route.meta.product);
  const title = route.meta.title || "";
  if (!product) return { product: null, page: title || "星枢 NexusOne" };
  return { product, page: title && title !== product.name ? title : "" };
});

const initial = computed(() => (session.displayName || "").trim().charAt(0).toUpperCase() || "?");

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

// AUTH3: end the server session when we still hold a token (best effort), always clear the local session, and
// replace the history entry so Back cannot return to a page of the signed-out user.
// LO4: when the server returns a Keycloak end_session URL, leave through it so the IdP SSO session ends too.
async function logout() {
  open.value = false;
  let target = "";
  if (session.accessToken) {
    try {
      target = endSessionTarget(await p2.logout());
    } catch {
      // The local session is cleared regardless; a failed server logout must not keep the user signed in here.
    }
  }
  clearIdentity();
  if (target) {
    leaveTo(target);
    return;
  }
  await router.replace("/login");
}

function onKey(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    searchEl.value?.focus();
  }
}

onMounted(() => {
  window.addEventListener("keydown", onKey);
  // AUTH4: a session restored from storage may predate the position lookup.
  if (session.contextId && !session.positionName) loadPrimaryPosition();
});
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
.crumb { display: flex; align-items: center; gap: 8px; font-size: 14px; white-space: nowrap; min-width: 0; }
.crumb-product { color: var(--nx-text-secondary); }
.crumb-product:hover { color: var(--nx-product); }
.crumb-sep { color: var(--nx-text-disabled); }
.crumb-page { color: var(--nx-text-primary); font-weight: 600; overflow: hidden; text-overflow: ellipsis; }
.platform-flag {
  color: var(--nx-warning);
  background: var(--nx-warning-light);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  white-space: nowrap;
}
.search { position: relative; flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--nx-text-secondary); }
.hotkey {
  margin-left: -64px;
  padding: 1px 6px;
  border: 1px solid var(--nx-border);
  border-radius: 4px;
  background: var(--nx-surface);
  color: var(--nx-text-secondary);
  font: 12px/18px var(--nx-font-mono, monospace);
  pointer-events: none;
}
.search input {
  width: min(420px, 100%);
  min-width: 240px;
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  height: 34px;
  padding: 0 72px 0 10px;
  background: var(--nx-bg-page);
  color: var(--nx-text-secondary);
}
.actions { display: flex; align-items: center; gap: 12px; }
.env { display: flex; align-items: center; gap: 6px; color: var(--nx-text-secondary); font-size: 13px; }
.env i { width: 8px; height: 8px; border-radius: var(--nx-radius-pill); background: var(--nx-info); display: block; }
.env[data-tone="prod"] i { background: var(--nx-success); }
.env[data-tone="test"] i { background: var(--nx-warning); }
.notify { display: inline-flex; align-items: center; gap: 6px; color: var(--nx-text-regular); font-size: 13px; }
.user { position: relative; }
.user-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 8px 0 4px;
  border: 0;
  border-radius: 999px;
  background: none;
  color: var(--nx-text-primary);
  cursor: pointer;
  font-size: 14px;
}
.user-btn:hover { background: var(--nx-bg-page); }
.avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--nx-product-light, var(--nx-info-light));
  color: var(--nx-product, var(--nx-primary));
  font-weight: 600;
  font-size: 13px;
}
.user-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link {
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
