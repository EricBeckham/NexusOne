<template>
  <router-view v-if="route.path === '/login'" />
  <NxMobileShell v-else-if="route.meta.mobile" />
  <AppShell v-else />
</template>

<script setup>
import { useRoute } from "vue-router";
import AppShell from "./layout/AppShell.vue";
import NxMobileShell from "./mobile/layouts/NxMobileShell.vue";

const route = useRoute();
</script>
