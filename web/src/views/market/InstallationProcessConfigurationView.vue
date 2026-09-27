<template>
  <main>
    <NxPageHeader eyebrow="P4 · 星市" title="个性化流程" description="默认使用平台发布的流程；管理员可为当前租户选择自己的已发布流程。仅影响之后发起的申请。">
      <template #actions><router-link class="ghost" to="/p4/installations">返回已开通</router-link><router-link class="ghost" to="/p3/workflow">设计租户流程</router-link></template>
    </NxPageHeader>
    <p v-if="error" role="alert" class="nx-error">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <NxSkeleton v-if="loading" variant="form-field" label="正在读取流程配置" />
    <form v-else-if="config" class="nx-card" @submit.prevent="save">
      <p>平台默认：{{ config.default_key ?? config.defaultKey ?? '未配置' }} · v{{ config.default_version ?? config.defaultVersion ?? '—' }}</p>
      <p>当前配置：{{ (config.override_key ?? config.overrideKey) ? '租户个性化流程' : '平台默认流程' }}</p>
      <label>本租户已发布流程<select v-model="selected" required><option value="">请选择</option><option v-for="row in published" :key="row.key" :value="row.key">{{ row.name || row.key }} · v{{ row.version }}</option></select></label>
      <p v-if="!published.length">暂无本租户已发布流程。可先设计、校验并发布，再返回配置。</p>
      <button class="primary" :disabled="busy || !selected">保存个性化配置</button>
      <button type="button" class="ghost" :disabled="busy || !(config.override_key ?? config.overrideKey)" @click="reset">恢复平台默认</button>
    </form>
  </main>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { p4 } from '../../api/client';
import { p3 } from '../../api/p3';
import NxPageHeader from '../../components/NxPageHeader.vue';
import NxSkeleton from '../../components/NxSkeleton.vue';
const route=useRoute(), loading=ref(true), busy=ref(false), error=ref(''), notice=ref(''), config=ref(null), published=ref([]), selected=ref('');
async function load() {
  loading.value=true; error.value='';
  try {
    const [configuration,catalog]=await Promise.all([p4.processConfiguration(route.params.id),p3.listProcessDefinitions()]);
    published.value=(Array.isArray(catalog)?catalog:[]).filter(row=>row.source==='tenant-deployment' && row.version!=null);
    config.value=configuration; selected.value=configuration.override_key ?? configuration.overrideKey ?? '';
  } catch (failure) { config.value=null; error.value=failure?.message || '流程配置加载失败'; }
  finally { loading.value=false; }
}
async function change(operation) {
  if(busy.value) return;
  busy.value=true; error.value=''; notice.value='';
  try { config.value=await operation(); notice.value='配置已保存，仅影响之后发起的申请。'; }
  catch(failure) { error.value=failure?.message || '保存失败，原配置未确认改变'; }
  finally { busy.value=false; }
}
function save() { const row=published.value.find(value=>value.key===selected.value); if(row) return change(()=>p4.configureProcess(route.params.id,{key:row.key,version:row.version})); }
function reset() { return change(()=>p4.resetProcess(route.params.id)); }
onMounted(load);
</script>
<style scoped>
form { display: grid; gap: var(--nx-form-field-gap); margin-block: var(--nx-space-6); }
label { display: grid; gap: var(--nx-form-label-gap); }
.nx-error { color: var(--nx-danger); }
</style>
