<template>
  <main class="click-path" data-testid="clickpath-guide">
    <NxPageHeader
      eyebrow="星域 / 演示点击路径"
      title="演示点击路径"
      description="只读顺序。本页不登录、不提交、不审订。"
      :crumbs="[{ label: '星域', to: '/p1/demo-deploy' }, { label: '演示点击路径' }]"
    />
    <p data-testid="clickpath-mode">当前会话标签：{{ mode.label }}。{{ mode.note }}</p>
    <ol data-testid="clickpath-steps">
      <li v-for="(step, index) in steps" :key="step.id" :data-testid="`clickpath-step-${step.id}`">
        <span>{{ index + 1 }}. {{ step.title }}</span>
        <strong :data-testid="`clickpath-mode-${step.id}`">{{ mode.label }}</strong>
        <router-link :to="step.to">{{ step.to }}</router-link>
        <p>{{ step.note }}</p>
      </li>
    </ol>
    <ul data-testid="clickpath-docs">
      <li v-for="doc in docs" :key="doc.id">{{ doc.label }}：{{ doc.path }}</li>
    </ul>
    <p class="note">预备路径不是验收勾选。不要把本页理解成 Keycloak 真机已通过。</p>
  </main>
</template>

<script setup>
import { computed } from "vue";
import { NxPageHeader } from "../../components";
import { CLICK_PATH_DOCS, CLICK_PATH_STEPS, clickPathMode } from "../../demo/clickPath";

const steps = CLICK_PATH_STEPS;
const docs = CLICK_PATH_DOCS;
const mode = computed(() => clickPathMode(import.meta.env));
</script>

<style scoped>
.click-path { display: grid; gap: 12px; }
ol, ul { margin: 0; padding-left: 1.2rem; }
li { display: grid; gap: 4px; margin-bottom: 12px; }
.note { color: var(--nx-text-secondary); }
</style>
