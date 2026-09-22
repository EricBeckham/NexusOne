<template>
  <div class="todo">
    <strong>{{ title }}</strong>
    <p>{{ body }}</p>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: "TODO · 后续工作包" },
  body: { type: String, required: true },
});
</script>

<style scoped>
.todo {
  border: 1px dashed var(--nx-border-strong);
  background: var(--nx-warning-light);
  color: var(--nx-text-regular);
  border-radius: 8px;
  padding: 12px 14px;
  margin: 12px 0 16px;
}
.todo p { margin: 4px 0 0; }
</style>
