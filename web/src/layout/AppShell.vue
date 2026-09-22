<template>
  <div class="shell" :data-product="product">
    <aside class="side">
      <div class="brand">
        <span class="mark" />
        <div>
          <strong>NexusOne</strong>
          <div class="nx-muted">M0-12 壳</div>
        </div>
      </div>
      <nav>
        <p class="group">星域 · 租户</p>
        <router-link to="/">工作台</router-link>
        <router-link to="/p1/tenants">租户目录</router-link>
        <router-link to="/p1/tenants/new">创建租户</router-link>
        <p class="group">星权 · 身份</p>
        <router-link to="/p2/persons">用户目录</router-link>
        <router-link to="/p2/org">组织架构</router-link>
        <router-link to="/p2/positions">岗位任职</router-link>
        <p class="group">权限 · M0-05 未到</p>
        <router-link to="/p2/permissions/roles">角色授权</router-link>
        <router-link to="/p2/permissions/datascope">数据范围</router-link>
        <router-link to="/p2/permissions/fields">字段权限</router-link>
        <p class="group">星构 · 业务延后</p>
        <router-link to="/p3/ledgers">台账</router-link>
        <router-link to="/p3/forms">表单</router-link>
        <router-link to="/p3/todos">待办</router-link>
      </nav>
    </aside>
    <div class="main">
      <header class="top">
        <div class="crumb">{{ route.meta.title || "NexusOne" }}</div>
        <div class="top-actions">
          <label class="switch">
            <input v-model="session.platformMode" type="checkbox" @change="persist" />
            平台操作
          </label>
          <select
            v-if="session.memberships.length > 1"
            :value="session.tenantId"
            @change="onSwitch($event.target.value)"
          >
            <option value="" disabled>切换租户</option>
            <option v-for="item in session.memberships" :key="itemKey(item)" :value="itemKey(item)">
              {{ itemKey(item) }}
            </option>
          </select>
          <span class="nx-muted nx-code">{{ session.displayName || "未登录" }}</span>
          <button class="link" type="button" @click="refresh">刷新会话</button>
          <router-link to="/login">会话</router-link>
        </div>
      </header>
      <section class="content">
        <router-view />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { persist, session, switchTenant, ensureContext } from "../api/session";

const route = useRoute();
const product = computed(() => route.meta.product || "p1");

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
</script>

<style scoped>
.shell { display: grid; grid-template-columns: var(--nx-sidebar) 1fr; min-height: 100%; }
.side {
  background: var(--nx-bg-sidebar);
  border-right: 1px solid var(--nx-border);
  padding: 16px 12px;
}
.brand { display: flex; gap: 10px; align-items: center; margin-bottom: 20px; }
.mark {
  width: 28px; height: 28px; border-radius: 8px;
  background: linear-gradient(135deg, var(--nx-product), var(--nx-primary));
}
.group {
  margin: 16px 8px 6px;
  font-size: 11px;
  letter-spacing: .04em;
  color: var(--nx-text-secondary);
  text-transform: uppercase;
}
nav a {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--nx-text-regular);
}
nav a.router-link-active {
  background: var(--nx-primary-light);
  color: var(--nx-product);
  font-weight: 600;
}
.main { display: flex; flex-direction: column; min-width: 0; }
.top {
  height: var(--nx-header);
  background: var(--nx-surface);
  border-bottom: 1px solid var(--nx-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 2;
}
.top-actions { display: flex; gap: 12px; align-items: center; }
.switch { display: flex; gap: 6px; align-items: center; color: var(--nx-text-secondary); font-size: 13px; }
.link { color: var(--nx-primary); background: none; border: 0; cursor: pointer; font-size: 13px; }
.content { padding: 24px; }
</style>
