<template>
  <textarea v-if="field.type === 'TEXTAREA'" rows="3" :placeholder="placeholder" tabindex="-1" />
  <select v-else-if="field.type === 'SELECT'" tabindex="-1">
    <option v-if="!options.length" value="">暂无选项</option>
    <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
  </select>
  <select v-else-if="field.type === 'PERSON'" tabindex="-1">
    <option value="">选择成员</option>
  </select>
  <div v-else-if="field.type === 'ATTACHMENT'" class="upload">
    <NxIcon name="upload" :size="16" />
    <span>点击或拖入文件上传</span>
  </div>
  <input v-else-if="field.type === 'NUMBER'" type="number" :placeholder="placeholder" tabindex="-1" />
  <input v-else-if="field.type === 'DATE'" type="date" tabindex="-1" />
  <div v-else-if="field.type === 'MONEY'" class="money">
    <span>¥</span><input type="text" inputmode="decimal" placeholder="0.00" tabindex="-1" />
  </div>
  <input v-else type="text" :placeholder="placeholder" tabindex="-1" />
</template>

<script setup>
import { computed } from "vue";
import NxIcon from "../../../icons/NxIcon.vue";
import { filledOptions } from "./fieldTypes";

const props = defineProps({ field: { type: Object, required: true } });
const options = computed(() => filledOptions(props.field));
const placeholder = computed(() => `请输入${props.field.label || ""}`);
</script>

<style scoped>
textarea, select, input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--nx-border-strong);
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
  font-size: var(--nx-font-size-md);
  color: var(--nx-text-regular);
  background: var(--nx-surface);
}
textarea { resize: none; }
.upload {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  border: 1px dashed var(--nx-border-strong);
  border-radius: 6px;
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-sm);
  background: var(--nx-bg-page);
}
.money { display: flex; align-items: stretch; }
.money span {
  display: grid;
  place-items: center;
  padding: 0 10px;
  border: 1px solid var(--nx-border-strong);
  border-right: 0;
  border-radius: 6px 0 0 6px;
  color: var(--nx-text-secondary);
  background: var(--nx-bg-page);
}
.money input { border-radius: 0 6px 6px 0; }
</style>
