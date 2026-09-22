<template>
  <div class="nx-mobile" data-product="p3" data-testid="mobile-shell">
    <header class="m-appbar">
      <button
        v-if="showBack"
        class="m-back"
        type="button"
        data-testid="mobile-back"
        aria-label="返回"
        @click="goBack"
      >
        返回
      </button>
      <h1>{{ title }}</h1>
      <span v-if="session.tenantId" class="m-tenant" data-testid="mobile-tenant">
        {{ tenantLabel }}
      </span>
    </header>
    <main class="m-body">
      <router-view />
    </main>
    <nav v-if="showTabs" class="m-bottom" data-testid="mobile-tabs">
      <router-link to="/m/p3/home">首页</router-link>
      <router-link to="/m/p3/files">文件</router-link>
      <router-link to="/m/login">会话</router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { session } from "../../api/session";
import { DEFAULT_MOBILE_HOME } from "../auth/safeReturn";
import "../styles/mobile.css";

const route = useRoute();
const router = useRouter();

const title = computed(() => route.meta.title || "星构");
const showTabs = computed(() => route.meta.tabs !== false);
const showBack = computed(() => route.meta.back === true);
const tenantLabel = computed(() => {
  const id = session.tenantId || "";
  return id.length > 12 ? `${id.slice(0, 8)}…` : id || "未签发";
});

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.replace(DEFAULT_MOBILE_HOME);
}
</script>
