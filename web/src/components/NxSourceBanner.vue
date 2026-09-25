<template>
  <div v-if="enabled" class="banner" data-source="LIVE" data-testid="p3-source-banner">
    <div>
      <strong>LIVE</strong>
      <p>{{ body }}</p>
    </div>
  </div>
</template>

<script setup>
import { devNotesEnabled } from "../utils/devNotes";

// Implementation notes for developers; hidden in the demo unless dev notes are switched on (O9).
const enabled = devNotesEnabled();
defineProps({
  body: { type: String, required: true },
});
</script>

<style scoped>
.banner {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  border: 1px dashed var(--nx-border-strong);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background: var(--nx-info-light);
}
.banner p { margin: 4px 0 0; color: var(--nx-text-regular); }
</style>
