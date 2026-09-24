<template>
  <NxPageHeader
    eyebrow="星域 / 平台"
    title="表单模板"
    description="平台管理员维护表单模板草稿。这里不是租户台账表单。"
  >
    <template #actions>
      <router-link class="primary" to="/p1/form-templates/new">新建模板</router-link>
    </template>
  </NxPageHeader>

  <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />
  <NxEmpty v-else-if="loaded && !rows.length" title="暂无" description="还没有平台表单模板。" />
  <table v-else-if="rows.length" class="nx-table" data-testid="template-list">
    <thead>
      <tr><th>编码</th><th>名称</th><th>状态</th><th>字段</th><th></th></tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.id">
        <td>{{ row.code || "暂无" }}</td>
        <td>{{ row.name || "暂无" }}</td>
        <td>{{ statusLabel(row.status) }}</td>
        <td>{{ Array.isArray(row.fields) ? row.fields.length : "暂无" }}</td>
        <td><router-link :to="`/p1/form-templates/${row.id}`">编辑</router-link></td>
      </tr>
    </tbody>
  </table>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";

const rows = ref([]);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");

function statusLabel(status) {
  return ({ DRAFT: "草稿", VALIDATED: "已校验", PUBLISHED: "已发布", ARCHIVED: "已归档" })[status] || status || "暂无";
}

async function load() {
  error.value = "";
  traceId.value = "";
  try {
    const body = await p1.formTemplates();
    rows.value = Array.isArray(body) ? body : [];
  } catch (err) {
    rows.value = [];
    error.value = err?.message || "表单模板加载失败";
    traceId.value = err?.traceId || "";
  } finally {
    loaded.value = true;
  }
}

onMounted(load);
</script>
