<template>
  <NxStatus :label="displayLabel" :tone="displayTone" data-testid="nx-status-tag" />
</template>

<script setup>
import { computed } from "vue";
import NxStatus from "./NxStatus.vue";

const labels = {
  ACTIVE: "启用",
  ENABLED: "启用",
  SUCCESS: "成功",
  PENDING: "待处理",
  PROCESSING: "处理中",
  DISABLED: "停用",
  FAILED: "失败",
  ERROR: "错误",
};

const tones = {
  ACTIVE: "success",
  ENABLED: "success",
  SUCCESS: "success",
  PENDING: "warning",
  PROCESSING: "info",
  DISABLED: "neutral",
  FAILED: "danger",
  ERROR: "danger",
};

const props = defineProps({
  status: { type: String, default: "" },
  label: { type: String, default: "" },
  tone: { type: String, default: "" },
});

const normalized = computed(() => props.status.trim().toUpperCase());
const displayLabel = computed(() => props.label || labels[normalized.value] || props.status || "未知");
const displayTone = computed(() => props.tone || tones[normalized.value] || "neutral");
</script>
