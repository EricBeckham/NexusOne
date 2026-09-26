<template>
  <main class="product-config-wizard" data-testid="product-config-wizard">
    <NxPageHeader
      eyebrow="P4 · 平台配置"
      title="产品配置向导"
      description="按顺序完成：配表单 → 配流程 → 绑 SKU → 发布。本页为配置完整感薄壳；步骤就绪态未接读口前一律待开始，禁止假勾选。"
      :crumbs="crumbs"
    >
      <template #actions>
        <router-link class="ghost" to="/p4/product-definitions">产品定义</router-link>
        <router-link class="ghost" to="/p1/form-templates">表单模板</router-link>
        <router-link class="ghost" to="/p3/workflow">流程设计器</router-link>
      </template>
    </NxPageHeader>

    <ol class="wizard-steps" aria-label="产品配置步骤" data-testid="wizard-steps">
      <li
        v-for="(step, index) in steps"
        :key="step.id"
        class="wizard-step"
        :class="{ active: activeStepId === step.id }"
        :data-status="step.status"
        :data-testid="`wizard-step-${step.id}`"
      >
        <button type="button" class="step-hit" @click="activeStepId = step.id">
          <span class="step-index">{{ index + 1 }}</span>
          <span class="step-body">
            <strong>{{ step.title }}</strong>
            <small>{{ stepStatusLabel(step.status) }}</small>
          </span>
        </button>
      </li>
    </ol>

    <section class="wizard-panel nx-card" data-testid="wizard-panel" :aria-labelledby="`step-heading-${activeStep.id}`">
      <h2 :id="`step-heading-${activeStep.id}`">{{ activeStep.title }}</h2>
      <p class="nx-muted">{{ activeStep.blurb }}</p>
      <p class="status-line" data-testid="wizard-step-status">
        当前状态：<strong>{{ stepStatusLabel(activeStep.status) }}</strong>
        <span class="nx-muted">（就绪态读口未接时不标完成）</span>
      </p>
      <div class="panel-actions">
        <router-link v-if="activeStep.to" class="primary" :to="activeStep.to" :data-testid="`wizard-cta-${activeStep.id}`">
          {{ activeStep.cta }}
        </router-link>
        <router-link class="ghost" to="/p4/product-definitions" data-testid="wizard-open-definitions">打开已有产品定义</router-link>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from "vue";
import { NxPageHeader } from "../../components";

/** PC1: fixed four steps; status stays PENDING until a readiness API (PC6/PC7) exists. */
const STEP_DEFS = [
  {
    id: "form",
    title: "配表单",
    blurb: "在平台表单模板中创建草稿、校验并发布版本，供产品定义选用。",
    to: "/p1/form-templates",
    cta: "去表单模板",
  },
  {
    id: "process",
    title: "配流程",
    blurb: "在流程设计器中配置并发布审批流程版本。",
    to: "/p3/workflow",
    cta: "去流程设计器",
  },
  {
    id: "bind",
    title: "绑 SKU",
    blurb: "保存产品定义后，将 SKU 绑定到该产品，租户才可在应用目录开通。",
    to: "/p4/product-definitions",
    cta: "去产品定义绑定",
  },
  {
    id: "publish",
    title: "发布",
    blurb: "确认表单、流程与 SKU 绑定就绪后，产品即可出现在应用目录供订阅开通。",
    to: "/p3/publish-center",
    cta: "打开发布中心",
  },
];

const crumbs = [
  { label: "产品定义", to: "/p4/product-definitions" },
  { label: "产品配置向导" },
];

const steps = STEP_DEFS.map((step) => ({ ...step, status: "PENDING" }));
const activeStepId = ref(steps[0].id);
const activeStep = computed(() => steps.find((step) => step.id === activeStepId.value) || steps[0]);

function stepStatusLabel(status) {
  if (status === "DONE") return "已完成";
  if (status === "ACTIVE") return "进行中";
  return "待开始";
}
</script>

<style scoped>
.product-config-wizard {
  display: grid;
  gap: var(--nx-space-4);
}
.wizard-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--nx-space-3);
}
.wizard-step {
  margin: 0;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
}
.wizard-step.active {
  border-color: var(--nx-product);
  box-shadow: 0 0 0 1px var(--nx-product-light);
}
.wizard-step[data-status="PENDING"] .step-index {
  background: var(--nx-divider);
  color: var(--nx-text-secondary);
}
.wizard-step[data-status="DONE"] .step-index {
  background: var(--nx-success);
  color: #fff;
}
.step-hit {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--nx-space-2);
  width: 100%;
  padding: var(--nx-space-3);
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
}
.step-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: var(--nx-font-size-xs);
  font-weight: 700;
}
.step-body {
  display: grid;
  gap: 2px;
}
.step-body strong {
  font-size: var(--nx-font-size-sm);
}
.step-body small {
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
}
.wizard-panel {
  display: grid;
  gap: var(--nx-space-3);
  padding: var(--nx-space-4) var(--nx-space-5);
}
.wizard-panel h2 {
  margin: 0;
  font-size: var(--nx-font-size-lg);
}
.status-line {
  margin: 0;
  font-size: var(--nx-font-size-sm);
}
.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nx-space-2);
}
@media (max-width: 900px) {
  .wizard-steps {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
