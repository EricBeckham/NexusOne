<template>
  <NxStatus :label="displayLabel" :tone="displayTone" v-bind="forwarded" />
</template>

<script setup>
import { computed, useAttrs } from "vue";
import NxStatus from "./NxStatus.vue";

defineOptions({ inheritAttrs: false });

/** §12.4 / Q4: common backend codes → label + tone. Domain pages may override via props. */
const labels = {
  ACTIVE: "启用",
  ENABLED: "启用",
  SUCCESS: "成功",
  PENDING: "待处理",
  PROCESSING: "处理中",
  DISABLED: "停用",
  FAILED: "失败",
  ERROR: "错误",
  DRAFT: "草稿",
  VALIDATED: "已校验",
  PUBLISHED: "已发布",
  ARCHIVED: "已归档",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  WITHDRAWN: "已撤回",
  RUNNING: "进行中",
  IN_APPROVAL: "审批中",
  CANCELLED: "已取消",
  INSTALLED: "已安装",
  UNINSTALLED: "已卸载",
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
  DRAFT: "neutral",
  VALIDATED: "info",
  PUBLISHED: "success",
  ARCHIVED: "neutral",
  APPROVED: "success",
  REJECTED: "danger",
  WITHDRAWN: "neutral",
  RUNNING: "info",
  IN_APPROVAL: "info",
  CANCELLED: "neutral",
  INSTALLED: "success",
  UNINSTALLED: "neutral",
};

const props = defineProps({
  status: { type: String, default: "" },
  label: { type: String, default: "" },
  tone: { type: String, default: "" },
});

const attrs = useAttrs();
const forwarded = computed(() => ({
  "data-testid": "nx-status-tag",
  ...attrs,
}));

const normalized = computed(() => props.status.trim().toUpperCase());
const displayLabel = computed(() => props.label || labels[normalized.value] || props.status || "未知");
const displayTone = computed(() => props.tone || tones[normalized.value] || "neutral");
</script>
