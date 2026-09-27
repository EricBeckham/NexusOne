<template>
  <router-view v-if="route.meta.authLayout || route.path === '/callback' || route.meta.harness" />
  <NxMobileShell v-else-if="route.meta.mobile" />
  <AppShell v-else />
  <NxToastHost />
</template>

<script setup>
import { useRoute } from "vue-router";
import AppShell from "./layout/AppShell.vue";
import NxMobileShell from "./mobile/layouts/NxMobileShell.vue";
import NxToastHost from "./components/NxToastHost.vue";

const route = useRoute();
</script>
