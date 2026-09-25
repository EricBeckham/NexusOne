<template>
  <main>
    <NxPageHeader eyebrow="星权 · 组织与身份" title="用户" description="查看当前租户的人员目录与任职信息。人员详情为只读。" />
    <p v-if="!session.contextId" class="nx-error">没有 context。请先登录并完成租户切换。</p>
    <form class="nx-card create-form" @submit.prevent="create">
      <div class="nx-row">
        <div class="nx-field"><label>姓名</label><input v-model="form.display_name" required /></div>
        <div class="nx-field"><label>工号</label><input v-model="form.employee_no" /></div>
        <div class="nx-field"><label>邮箱</label><input v-model="form.email" type="email" /></div>
        <button class="primary" type="submit" :disabled="!session.contextId || creating">新增人员</button>
      </div>
    </form>
    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="reload" />
    <section v-else-if="loading" class="nx-card loading" role="status">正在加载用户目录…</section>
    <NxEmpty v-else-if="!rows.length" title="暂无用户" description="当前租户还没有可展示的人员记录。" />
    <div v-else class="table-scroll nx-card">
      <table class="nx-table" data-testid="person-table">
        <thead><tr><th>姓名 / 账号</th><th>所属组织</th><th>岗位</th><th>角色</th><th>账号状态</th><th>最近登录</th><th>来源</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td><button class="person-name" type="button" @click="openDrawer(row)">{{ displayName(row) }}</button><small>{{ row.employee_no || row.employeeNo || row.email || '暂无账号信息' }}</small></td>
            <td>详情中查看</td><td>详情中查看</td><td>暂无</td>
            <td><NxStatus :label="row.status || '暂无'" :tone="row.status === 'ACTIVE' ? 'success' : 'neutral'" /></td>
            <td>暂无</td><td>暂无</td>
            <td><button class="text-action" type="button" @click="openDrawer(row)">详情</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="selectedPerson" class="drawer-backdrop" data-testid="person-drawer-backdrop" @click.self="closeDrawer">
      <aside class="person-drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" data-testid="person-drawer">
        <header class="drawer-header">
          <div><span class="eyebrow">用户详情 · 只读</span><h2 id="drawer-title">{{ displayName(detail || selectedPerson) }}</h2></div>
          <button class="close-button" type="button" aria-label="关闭详情" @click="closeDrawer">×</button>
        </header>
        <p v-if="detailLoading" class="loading" role="status">正在加载人员详情…</p>
        <NxErrorState v-else-if="detailError" :description="detailError" retry-label="重试" @retry="loadDetail(selectedPerson)" />
        <template v-else>
          <section class="detail-section"><h3>基础信息</h3><dl class="person-facts">
            <dt>姓名</dt><dd>{{ value(detail?.display_name || detail?.displayName) }}</dd>
            <dt>工号</dt><dd>{{ value(detail?.employee_no || detail?.employeeNo) }}</dd>
            <dt>邮箱</dt><dd>{{ value(detail?.email) }}</dd><dt>电话</dt><dd>{{ value(detail?.phone) }}</dd>
            <dt>人员 ID</dt><dd class="nx-code">{{ value(detail?.id) }}</dd><dt>租户 ID</dt><dd class="nx-code">{{ value(detail?.tenant_id || detail?.tenantId) }}</dd>
            <dt>入职日期</dt><dd>{{ value(detail?.hired_on || detail?.hiredOn) }}</dd>
            <dt>人员状态</dt><dd><NxStatus :label="detail?.status || '暂无'" :tone="detail?.status === 'ACTIVE' ? 'success' : 'neutral'" /></dd>
          </dl></section>
          <section class="detail-section"><h3>任职</h3>
            <p v-if="employmentError" class="nx-error">{{ employmentError }}</p>
            <NxEmpty v-else-if="!employments.length" title="暂无任职" description="该人员当前没有任职记录。" compact />
            <div v-else class="employment-list"><article v-for="employment in employments" :key="employment.id" class="employment-card">
              <strong>{{ departmentName(employment.department_id || employment.departmentId) }}</strong>
              <span>{{ positionName(employment.position_id || employment.positionId) }}</span>
              <small>{{ employment.kind || '暂无类型' }} · {{ employment.status || '暂无状态' }}</small>
              <small>{{ value(employment.valid_from || employment.validFrom) }} 至 {{ value(employment.valid_to || employment.validTo) }}</small>
            </article></div>
          </section>
          <section class="detail-section"><h3>角色与权限摘要</h3><NxEmpty title="暂无角色 / 权限摘要" description="当前接口未提供按人员关联的角色与权限查询。" compact /></section>
          <section class="detail-section"><h3>登录状态</h3><dl class="person-facts"><dt>账号关联</dt><dd class="nx-code">{{ value(detail?.account_id || detail?.accountId) }}</dd><dt>最近登录</dt><dd>暂无登录记录读接口</dd></dl></section>
        </template>
        <footer class="drawer-actions"><button class="ghost" type="button" @click="closeDrawer">关闭</button></footer>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from "vue";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { p2 } from "../../api/client";
import { ensureContext, session } from "../../api/session";

const rows = ref([]); const loading = ref(false); const error = ref(""); const creating = ref(false);
const form = reactive({ display_name: "", employee_no: "", email: "" });
const selectedPerson = ref(null); const detail = ref(null); const employments = ref([]); const departments = ref([]); const positions = ref([]);
const detailLoading = ref(false); const detailError = ref(""); const employmentError = ref("");
const value = (input) => input === null || input === undefined || input === "" ? "暂无" : input;
const displayName = (person) => person?.display_name || person?.displayName || "暂无姓名";

async function reload() {
  loading.value = true; error.value = "";
  try {
    if (session.accessToken && !session.contextId) await ensureContext();
    if (!session.contextId) { rows.value = []; return; }
    rows.value = (await p2.persons()) || [];
  } catch (err) { error.value = `${err.code || ""} ${err.message || "无法读取用户目录"}`.trim(); }
  finally { loading.value = false; }
}
function flattenDepartments(tree, result = []) {
  for (const item of tree || []) { result.push(item); flattenDepartments(item.children || item.departments || [], result); }
  return result;
}
function departmentName(id) { return departments.value.find((item) => (item.id || item.department_id) === id)?.name || "暂无组织名称"; }
function positionName(id) { return positions.value.find((item) => (item.id || item.position_id) === id)?.name || "暂无岗位名称"; }
async function loadDetail(person) {
  const requestedId = person.id;
  detailLoading.value = true; detailError.value = ""; employmentError.value = "";
  const [personResult, employmentResult, departmentResult, positionResult] = await Promise.allSettled([
    p2.getPerson(requestedId), p2.employments(requestedId), p2.departments(), p2.positions(),
  ]);
  if (selectedPerson.value?.id !== requestedId) return;
  if (personResult.status === "fulfilled") detail.value = personResult.value || person;
  else detailError.value = `${personResult.reason?.code || ""} ${personResult.reason?.message || "无法读取人员详情"}`.trim();
  if (employmentResult.status === "fulfilled") employments.value = employmentResult.value || [];
  else { employments.value = []; employmentError.value = `${employmentResult.reason?.code || ""} ${employmentResult.reason?.message || "无法读取任职记录"}`.trim(); }
  departments.value = departmentResult.status === "fulfilled" ? flattenDepartments(departmentResult.value) : [];
  positions.value = positionResult.status === "fulfilled" ? positionResult.value || [] : [];
  detailLoading.value = false;
}
function openDrawer(person) { selectedPerson.value = person; detail.value = null; employments.value = []; void loadDetail(person); }
function closeDrawer() { selectedPerson.value = null; detail.value = null; }
async function create() {
  creating.value = true; error.value = "";
  try { await p2.createPerson({ ...form }); form.display_name = ""; form.employee_no = ""; form.email = ""; await reload(); }
  catch (err) { error.value = `${err.code || ""} ${err.message || "无法新增人员"}`.trim(); }
  finally { creating.value = false; }
}
function onKey(event) { if (event.key === "Escape") closeDrawer(); }
onMounted(() => { void reload(); window.addEventListener("keydown", onKey); });
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>

<style scoped>
.create-form { margin-bottom: var(--nx-space-4); }
.primary,.ghost { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 0 var(--nx-space-3); border-radius: 6px; cursor: pointer; }
.primary { align-self: end; color: #fff; background: var(--nx-product); border: 1px solid var(--nx-product); }
.primary:disabled { opacity: .55; cursor: not-allowed; }
.ghost { color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); }
.table-scroll { overflow-x: auto; padding: 0; }
.nx-table { min-width: 980px; }
.nx-table th,.nx-table td { white-space: nowrap; }
.person-name,.text-action { padding: 0; color: var(--nx-primary); background: none; border: 0; font: inherit; cursor: pointer; }
.person-name { font-weight: 600; }.nx-table small { display: block; margin-top: 4px; color: var(--nx-text-secondary); }
.loading { padding: var(--nx-space-5); color: var(--nx-text-secondary); }
.drawer-backdrop { position: fixed; inset: 0; z-index: 20; display: flex; justify-content: flex-end; background: color-mix(in srgb, var(--nx-text-primary) 28%, transparent); }
.person-drawer { display: flex; flex-direction: column; width: min(520px, 100vw); height: 100%; padding: var(--nx-space-6); overflow-y: auto; background: var(--nx-surface); box-shadow: var(--nx-shadow-dialog); }
.drawer-header { display: flex; justify-content: space-between; gap: var(--nx-space-4); padding-bottom: var(--nx-space-4); border-bottom: 1px solid var(--nx-divider); }
.eyebrow { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }.drawer-header h2 { margin: var(--nx-space-1) 0 0; font-size: var(--nx-font-size-lg); }
.close-button { width: 36px; height: 36px; border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); color: var(--nx-text-secondary); background: var(--nx-surface); font-size: 22px; cursor: pointer; }
.detail-section { padding: var(--nx-space-4) 0; border-bottom: 1px solid var(--nx-divider); }.detail-section h3 { margin: 0 0 var(--nx-space-3); font-size: var(--nx-font-size-sm); }
.person-facts { display: grid; grid-template-columns: minmax(120px,.8fr) 1.2fr; gap: var(--nx-space-3); margin: 0; }.person-facts dt { color: var(--nx-text-secondary); }.person-facts dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.employment-list { display: grid; gap: var(--nx-space-2); }.employment-card { display: grid; gap: var(--nx-space-1); padding: var(--nx-space-3); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); }.employment-card span,.employment-card small { color: var(--nx-text-secondary); }
.drawer-actions { display: flex; justify-content: flex-end; margin-top: auto; padding-top: var(--nx-space-4); }
@media(max-width:760px) { .nx-row { flex-wrap: wrap; }.person-drawer { padding: var(--nx-space-4); } }
</style>
