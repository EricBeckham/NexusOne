<template>
  <div class="banner" :data-source="source" data-testid="p3-source-banner">
    <div>
      <strong>{{ source }}</strong>
      <p>{{ body }}</p>
    </div>
    <div class="actions">
      <button class="ghost" type="button" data-testid="p3-toggle-source" @click="toggle">
        {{ source === "MOCK" ? "尝试真实 API" : "回到 MOCK" }}
      </button>
      <button v-if="source === 'MOCK'" class="ghost" type="button" @click="reset">重置 MOCK</button>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { p3, p3Source, setP3UseMock, useP3Mock } from "../api/p3";

defineProps({
  body: { type: String, required: true },
});

const source = computed(() => p3Source());

function toggle() {
  setP3UseMock(!useP3Mock());
  location.reload();
}

async function reset() {
  await p3.resetMock();
  location.reload();
}
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
}
.banner[data-source="MOCK"] { background: var(--nx-warning-light); }
.banner[data-source="LIVE"] { background: var(--nx-info-light); }
.banner p { margin: 4px 0 0; color: var(--nx-text-regular); }
.actions { display: flex; gap: 8px; flex-shrink: 0; }
</style>
