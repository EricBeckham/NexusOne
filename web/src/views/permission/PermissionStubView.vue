<template>
  <div>
    <div class="nx-page-header">
      <h1>{{ title }}</h1>
      <p>M0-05 权限引擎尚未合并。本页是占位壳，禁止前端自行拼权限码或实现 DataScope。</p>
    </div>
    <NxTodo :body="todo" />
    <div class="nx-card">
      <p>预期 API（未实现，勿调用）：</p>
      <ul class="nx-code">
        <li>GET /api/v1/p2/roles</li>
        <li>GET /api/v1/p2/policies</li>
        <li>GET /api/v1/p2/data-scopes</li>
        <li>GET /api/v1/p2/field-policies</li>
        <li>GET /api/v1/p2/policy-snapshots/current</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import NxTodo from "../../components/NxTodo.vue";

const route = useRoute();
const kind = computed(() => route.params.kind || "roles");
const title = computed(() => ({
  roles: "角色授权",
  datascope: "数据范围",
  fields: "字段权限",
}[kind.value] || "权限"));
const todo = computed(() =>
  `TODO(M0-05): ${title.value} 需要 RBAC / Explicit Deny / DataScope / FieldPolicy / PolicySnapshot。G2/G9 不能在本包关闭。`);
</script>
