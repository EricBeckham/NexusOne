<template>
  <div>
    <div class="nx-page-header">
      <h1>岗位与任职</h1>
      <p>岗位不等于角色。本页只维护岗位、人员所属部门和任职有效期。</p>
    </div>
    <div class="nx-grid editors">
      <form class="nx-card" data-testid="position-form" @submit.prevent="createPosition">
        <h3>新建岗位</h3>
        <div class="nx-field"><label>编码</label><input v-model.trim="positionForm.code" required /></div>
        <div class="nx-field"><label>名称</label><input v-model.trim="positionForm.name" required /></div>
        <div class="nx-field"><label>类别</label><input v-model.trim="positionForm.category" /></div>
        <div class="nx-field"><label>职责</label><textarea v-model.trim="positionForm.duties" rows="2" /></div>
        <button class="primary" type="submit" :disabled="busy">保存岗位</button>
      </form>
      <form class="nx-card" data-testid="employment-form" @submit.prevent="assign">
        <h3>新增任职</h3>
        <div class="nx-field">
          <label>人员</label>
          <select v-model="employmentForm.personId" required>
            <option value="" disabled>请选择</option>
            <option v-for="row in activePersons" :key="row.id" :value="row.id">{{ row.displayName }}{{ row.employeeNo ? ` · ${row.employeeNo}` : "" }}</option>
          </select>
        </div>
        <div class="nx-field">
          <label>部门</label>
          <select v-model="employmentForm.departmentId" required>
            <option value="" disabled>请选择</option>
            <option v-for="row in activeDepartments" :key="row.id" :value="row.id">{{ row.name }}</option>
          </select>
        </div>
        <div class="nx-field">
          <label>岗位</label>
          <select v-model="employmentForm.positionId" required>
            <option value="" disabled>请选择</option>
            <option v-for="row in activePositions" :key="row.id" :value="row.id">{{ row.name }}</option>
          </select>
        </div>
        <div class="inline-fields">
          <div class="nx-field"><label>类型</label><select v-model="employmentForm.kind"><option value="PRIMARY">主任职</option><option value="SECONDARY">兼任</option></select></div>
          <div class="nx-field"><label>生效日</label><input v-model="employmentForm.validFrom" type="date" /></div>
          <div class="nx-field"><label>结束日</label><input v-model="employmentForm.validTo" type="date" /></div>
        </div>
        <button class="primary" type="submit" :disabled="busy || !canAssign">分配任职</button>
      </form>
    </div>

    <p v-if="error" class="nx-error" data-testid="organization-error">{{ error }}</p>
    <p v-if="message" class="ok" data-testid="organization-message">{{ message }}</p>

    <section class="section">
      <h2>岗位</h2>
      <table class="nx-table">
        <thead><tr><th>编码</th><th>名称</th><th>类别</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in positions" :key="row.id">
            <td>{{ row.code }}</td><td>{{ row.name }}</td><td>{{ row.category || "—" }}</td>
            <td>{{ statusLabel(row.status) }}</td>
            <td><button v-if="row.status === 'ACTIVE'" class="link danger-link" type="button" :data-testid="`disable-position-${row.id}`" @click="disablePosition(row)">停用</button></td>
          </tr>
          <tr v-if="!positions.length"><td colspan="5" class="nx-empty">暂无岗位</td></tr>
        </tbody>
      </table>
    </section>

    <section class="section">
      <h2>任职记录</h2>
      <table class="nx-table" data-testid="employment-table">
        <thead><tr><th>人员</th><th>部门</th><th>岗位</th><th>类型</th><th>有效期</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in employments" :key="row.id">
            <td>{{ personName(row.personId) }}</td><td>{{ departmentName(row.departmentId) }}</td><td>{{ positionName(row.positionId) }}</td>
            <td>{{ row.kind === "PRIMARY" ? "主任职" : "兼任" }}</td><td>{{ row.validFrom }} → {{ row.validTo || "长期" }}</td><td>{{ statusLabel(row.status) }}</td>
            <td><button v-if="row.status === 'ACTIVE'" class="link danger-link" type="button" :data-testid="`end-employment-${row.id}`" @click="endEmployment(row)">结束</button></td>
          </tr>
          <tr v-if="!employments.length"><td colspan="7" class="nx-empty">暂无任职</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { p2 } from "../../api/client";

const positions = ref([]);
const persons = ref([]);
const departments = ref([]);
const employments = ref([]);
const error = ref("");
const message = ref("");
const busy = ref(false);
const positionForm = reactive({ code: "", name: "", category: "ic", duties: "" });
const employmentForm = reactive({ personId: "", departmentId: "", positionId: "", kind: "PRIMARY", validFrom: "", validTo: "" });

function flatten(nodes) {
  return (nodes || []).flatMap((node) => {
    const department = node.department || node;
    return [department, ...flatten(node.children || [])];
  });
}

const activePersons = computed(() => persons.value.filter((row) => row.status === "ACTIVE"));
const activeDepartments = computed(() => departments.value.filter((row) => row.status === "ACTIVE"));
const activePositions = computed(() => positions.value.filter((row) => row.status === "ACTIVE"));
const canAssign = computed(() => employmentForm.personId && employmentForm.departmentId && employmentForm.positionId);

async function reload() {
  const [personRows, tree, positionRows, employmentRows] = await Promise.all([
    p2.persons(), p2.departments(), p2.positions(), p2.allEmployments(),
  ]);
  persons.value = personRows || [];
  departments.value = flatten(tree);
  positions.value = positionRows || [];
  employments.value = employmentRows || [];
}

onMounted(async () => {
  try { await reload(); } catch (err) { showError(err); }
});

async function createPosition() {
  await run(async () => {
    await p2.createPosition({ ...positionForm });
    Object.assign(positionForm, { code: "", name: "", category: "ic", duties: "" });
    message.value = "岗位已创建";
  });
}

async function assign() {
  await run(async () => {
    await p2.assignEmployment({
      ...employmentForm,
      validFrom: employmentForm.validFrom || null,
      validTo: employmentForm.validTo || null,
    });
    Object.assign(employmentForm, { personId: "", departmentId: "", positionId: "", kind: "PRIMARY", validFrom: "", validTo: "" });
    message.value = "任职已分配";
  });
}

async function disablePosition(row) {
  await run(async () => { await p2.disablePosition(row.id); message.value = `岗位“${row.name}”已停用`; });
}

async function endEmployment(row) {
  await run(async () => { await p2.endEmployment(row.id); message.value = "任职已结束"; });
}

async function run(action) {
  busy.value = true; error.value = ""; message.value = "";
  try { await action(); await reload(); } catch (err) { showError(err); } finally { busy.value = false; }
}

function showError(err) { error.value = `${err.code || ""} ${err.message}`.trim(); }
function byId(rows, id) { return rows.find((row) => row.id === id); }
function personName(id) { return byId(persons.value, id)?.displayName || id; }
function departmentName(id) { return byId(departments.value, id)?.name || id; }
function positionName(id) { return byId(positions.value, id)?.name || id; }
function statusLabel(status) { return status === "ACTIVE" ? "启用" : status === "ENDED" ? "已结束" : "已停用"; }
</script>

<style scoped>
.editors { grid-template-columns: minmax(280px, .8fr) minmax(460px, 1.2fr); }
.inline-fields { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
.section { margin-top: 22px; }
.section h2 { font-size: 16px; }
.link { border: 0; background: transparent; cursor: pointer; }
.danger-link { color: var(--nx-danger); }
.ok { color: var(--nx-success); }
@media (max-width: 900px) { .editors, .inline-fields { grid-template-columns: 1fr; } }
</style>
