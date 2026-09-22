<template>
  <div>
    <div class="nx-page-header">
      <h1>{{ spec.title }}</h1>
      <p>{{ spec.blurb }}</p>
    </div>
    <NxTodo :body="spec.todo" />
    <div class="nx-card">
      <p>本包不模拟业务数据。双租户脚本对下列路径断言 <strong>HTTP 404</strong> 或明确 SKIP。</p>
      <p class="nx-code">{{ spec.path }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import NxTodo from "../../components/NxTodo.vue";

const route = useRoute();
const catalog = {
  ledgers: {
    title: "台账",
    blurb: "P3 Schema / CRUD 属于 M0-06。",
    todo: "TODO(M0-06): 应用、一个台账、基础字段、SchemaService DDL、租户过滤列表。G18 台账段未开始。",
    path: "GET /api/v1/p3/ledgers",
  },
    forms: {
    title: "表单与发布",
    blurb: "基础表单 API 已在 M0-07（Draft→Validate→Publish→Running）。本页不实现设计器。",
    todo: "TODO(M0-12): 表单设计/填报 UI。后端 POST/GET /api/v1/p3/forms 与 /validate /publish /submissions 已存在。",
    path: "GET /api/v1/p3/forms",
  },
  todos: {
    title: "待办",
    blurb: "顺序审批与待办属于 M0-08。站内通知属于 M0-10。",
    todo: "TODO(M0-08/M0-10): 发起/同意/驳回/撤回、待办/已办/我发起、审批触发通知。G18 审批段未开始。",
    path: "GET /api/v1/p3/todos",
  },
};

const spec = computed(() => catalog[route.params.kind] || catalog.ledgers);
</script>
