<template>
  <!-- §6 / Q5: optional page crumb + title/description + right actions. Does not own routing. -->
  <header class="nx-page-header page-header" data-testid="nx-page-header">
    <nav
      v-if="showCrumb"
      class="nx-page-crumb"
      aria-label="页面路径"
      data-testid="nx-page-crumb"
    >
      <slot name="breadcrumb">
        <template v-for="(crumb, index) in crumbs" :key="`${crumb.label}-${index}`">
          <span v-if="index" class="nx-page-crumb__sep" aria-hidden="true">/</span>
          <router-link v-if="crumb.to" class="nx-page-crumb__link" :to="crumb.to">{{ crumb.label }}</router-link>
          <span v-else class="nx-page-crumb__current" aria-current="page">{{ crumb.label }}</span>
        </template>
      </slot>
    </nav>
    <div class="nx-page-header__row">
      <div class="nx-page-header__heading heading">
        <p v-if="eyebrow" class="nx-page-header__eyebrow eyebrow">{{ eyebrow }}</p>
        <h1 class="nx-page-header__title">{{ title }}</h1>
        <p v-if="description" class="nx-page-header__description description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="nx-page-header__actions actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, useSlots } from "vue";

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: "" },
  eyebrow: { type: String, default: "" },
  /** Optional in-page path chips. Prefer `to` for links; last item usually has no `to`. */
  crumbs: {
    type: Array,
    default: () => [],
  },
});

const slots = useSlots();
const showCrumb = computed(() => Boolean(slots.breadcrumb) || props.crumbs.length > 0);
</script>

<style scoped>
.page-header {
  display: flex;
  flex-direction: column;
  gap: var(--nx-space-2);
  margin-bottom: var(--nx-space-4);
}
.nx-page-header__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--nx-space-4);
}
.heading { min-width: var(--nx-space-0); }
.eyebrow {
  margin: var(--nx-space-0) var(--nx-space-0) var(--nx-space-1);
  color: var(--nx-product);
  font-size: var(--nx-font-size-xs);
  font-weight: 650;
}
.nx-page-header__title {
  margin: var(--nx-space-0);
  color: var(--nx-text-primary);
  font-size: var(--nx-font-size-page-title);
  font-weight: 700;
}
.description {
  margin: var(--nx-space-1) var(--nx-space-0) var(--nx-space-0);
  color: var(--nx-text-secondary);
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--nx-space-2);
  flex: none;
}
.nx-page-crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--nx-space-1);
  color: var(--nx-text-secondary);
  font-size: var(--nx-font-size-xs);
  line-height: var(--nx-status-tag-line-height);
}
.nx-page-crumb__sep { color: var(--nx-text-disabled); }
.nx-page-crumb__link {
  color: var(--nx-text-secondary);
  text-decoration: none;
}
.nx-page-crumb__link:hover { color: var(--nx-primary); }
.nx-page-crumb__current {
  color: var(--nx-text-regular);
  font-weight: 500;
}
</style>
