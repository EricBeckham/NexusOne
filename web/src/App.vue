<template>
  <router-view v-if="route.path === '/login'" />
  <AppShell v-else />
</template>

<script setup>
import { useRoute } from "vue-router";
import AppShell from "./layout/AppShell.vue";

const route = useRoute();
</script>
