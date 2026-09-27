<template>
  <section class="nx-card authoring" aria-label="平台应用配置">
    <p>创建应用包 → 创建版本 → 送审、批准、发布 → 创建 SKU → 绑定产品定义。</p>
    <p v-if="error" role="alert" class="nx-error">{{ error }}</p>
    <p v-if="result" role="status">{{ result }}</p>
    <form v-if="section === 'packages'" @submit.prevent="createApp">
      <h2>创建应用包</h2>
      <label>应用编码<input v-model.trim="app.code" required pattern="[a-zA-Z0-9_.-]+" /></label>
      <label>应用名称<input v-model.trim="app.name" required /></label>
      <label>简介<input v-model.trim="app.summary" /></label>
      <button class="primary" :disabled="busy">创建应用包</button>
    </form>
    <form v-if="section === 'packages' && selectedApp" @submit.prevent="createVersion">
      <h2>为 {{ selectedApp.name }} 创建版本</h2>
      <label>版本号<input v-model.trim="version.version" required /></label>
      <label>发布说明<input v-model.trim="version.releaseNotes" /></label>
      <button class="primary" :disabled="busy">创建版本</button>
      <div v-for="row in versions" :key="row.id">
        {{ row.version }} · {{ row.status }}
        <button v-if="actions[row.status]" type="button" class="ghost" :disabled="busy" @click="transition(row)">{{ actions[row.status].label }}</button>
      </div>
    </form>
    <form v-if="section === 'skus'" @submit.prevent="createSku">
      <h2>创建 SKU</h2>
      <label>应用<select v-model="sku.appId" required><option value="">请选择</option><option v-for="row in apps" :key="row.id" :value="row.id">{{ row.name }}</option></select></label>
      <label>SKU 编码<input v-model.trim="sku.code" required pattern="[a-zA-Z0-9_.-]+" /></label>
      <label>名称<input v-model.trim="sku.name" required /></label>
      <label>权益编码<input v-model.trim="sku.entitlement" required pattern="[a-zA-Z0-9_.-]+" /></label>
      <label>配额<input v-model.number="sku.quota" required type="number" min="1" step="1" /></label>
      <button class="primary" :disabled="busy">创建 SKU</button>
      <router-link to="/p4/product-definitions">绑定产品定义</router-link>
      <p>发布版本与创建 SKU 不等于已可发起；还需绑定含已发布表单、流程的产品定义。</p>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { p4 } from '../../api/client';
const props = defineProps({ section: String, apps: Array, selectedApp: Object, versions: Array });
const emit = defineEmits(['saved']);
const busy = ref(false), error = ref(''), result = ref('');
const app = reactive({ code: '', name: '', summary: '' });
const version = reactive({ version: '', releaseNotes: '' });
const sku = reactive({ appId: '', code: '', name: '', entitlement: 'apps', quota: 1 });
const actions = { DRAFT: { action: 'review', label: '送审' }, IN_REVIEW: { action: 'approve', label: '批准' }, APPROVED: { action: 'publish', label: '发布' } };
async function save(operation, message) {
  if (busy.value) return;
  busy.value = true; error.value = ''; result.value = '';
  try { await operation(); result.value = message; emit('saved'); }
  catch (failure) { error.value = failure?.message || '保存失败，请检查后重试'; }
  finally { busy.value = false; }
}
function createApp() { return save(() => p4.createApp({ ...app }), '应用包已创建，请在列表选择它并创建版本。'); }
function createVersion() { return save(() => p4.createVersion(props.selectedApp.id, { ...version }), '版本已创建，请按状态依次送审、批准、发布。'); }
function transition(row) { return save(() => p4.transition(row.id, actions[row.status].action), '状态操作已保存；请以重新读取的版本状态为准。'); }
function createSku() { return save(() => p4.createSku({ appId: sku.appId, code: sku.code, name: sku.name, entitlements: { [sku.entitlement]: { enabled: true, quota: sku.quota, attributes: {} } } }), 'SKU 已创建，下一步绑定产品定义。'); }
</script>

<style scoped>
.authoring { margin-block: var(--nx-space-6); }
form { display: flex; flex-direction: column; gap: var(--nx-form-field-gap); margin-block: var(--nx-space-6); }
label { display: flex; flex-direction: column; gap: var(--nx-form-label-gap); }
.nx-error { color: var(--nx-danger); }
</style>
