<template>
  <main>
    <NxPageHeader eyebrow="P3 · 星构" title="可用应用" description="从当前租户已开通的应用发起申请。开通与配置由管理员负责。" />
    <NxDataTable :loading="loading" :error="error ? { title: '可用应用加载失败', description: error } : ''" :empty="!loading && !error && !rows.length" empty-title="暂无可用应用" empty-description="请联系租户管理员开通并配置应用后，再来发起申请。" aria-label="可用应用" density="compact" @retry="load">
      <template #header><tr><th>应用</th><th>操作</th></tr></template>
      <tr v-for="row in rows" :key="row.id"><td>{{ row.name }}</td><td><router-link class="primary" :to="`/p3/applications/${encodeURIComponent(row.id)}/start`">发起申请</router-link></td></tr>
    </NxDataTable>
  </main>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import { p4 } from '../../api/client';
import NxPageHeader from '../../components/NxPageHeader.vue';
import NxDataTable from '../../components/NxDataTable.vue';
const rows = ref([]), loading = ref(false), error = ref('');
async function load() {
  loading.value = true; error.value = ''; rows.value = [];
  try {
    const installed = (await p4.installations()).filter(row => row.status === 'INSTALLED');
    rows.value = await Promise.all(installed.map(async row => {
      const app = await p4.app(row.appId ?? row.app_id);
      return { id: row.id, name: app.name || app.code };
    }));
  } catch (failure) { error.value = failure?.message || '暂时无法读取可用应用'; }
  finally { loading.value = false; }
}
onMounted(load);
</script>
