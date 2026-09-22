<template>
  <p v-if="decision" class="m-muted" data-testid="m-perm-decision">
    {{ decision.engine }} · {{ label }} · {{ decision.reason }}
  </p>
</template>

<script setup>
import { computed } from "vue";
import { cellKey } from "./surfaceCatalog";

const props = defineProps({
  decision: { type: Object, default: null },
  cell: { type: Object, default: null },
});

const label = computed(() => {
  if (props.cell) {
    return cellKey(props.cell);
  }
  if (props.decision?.resource && props.decision?.action) {
    return cellKey(props.decision);
  }
  return "permission";
});
</script>
