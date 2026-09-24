<template>
  <div class="nx-page-header">
    <h1>{{ product?.name || "产品" }}</h1>
    <p>{{ product?.en || "" }}</p>
  </div>
  <p>暂无</p>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { productById } from "../nav/products";

const route = useRoute();
const product = computed(() => productById(route.meta.product));
</script>
