<template>
  <div>
    <div class="nx-page-header">
      <h1>{{ title }}</h1>
      <p>统一维护角色、主体分配与 DataScope；写入后 PolicySnapshot 立即失效并重载，受保护查询继续由服务端 PermissionEngine 绑定租户与数据范围。</p>
    </div>
    <NxSourceBanner :body="banner" />
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。矩阵 / snapshot / check 需要服务端签发的上下文。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>

    <div v-if="matrix" class="nx-card" style="margin-bottom:16px;" data-testid="g2-matrix-status">
      <h3>G2 完整性</h3>
      <p>
        PolicySnapshot v{{ pick(matrix, "policy_version", "policyVersion") }} ·
        demo seed {{ pick(matrix, "demo_seed_present", "demoSeedPresent") ? "present" : "absent" }} ·
        differentiated {{ pick(matrix, "differentiated_policy", "differentiatedPolicy") ? "yes" : "no" }}
      </p>
      <p class="nx-muted">{{ pick(matrix, "completeness_note", "completenessNote") }}</p>
      <p v-if="pick(matrix, 'demo_seed_present', 'demoSeedPresent') && !pick(matrix, 'differentiated_policy', 'differentiatedPolicy')" class="nx-error">
        仅有 tenant_member ALL-scope 种子不算权限完成。请安装 G2 示例角色或写入 SELF/DEPT 与字段策略。
      </p>
      <div class="nx-row" style="margin-top:12px;">
        <button class="primary" type="button" data-testid="g2-install-example" @click="onInstall">
          安装 G2 示例角色（不自动分配）
        </button>
      </div>
    </div>

    <div class="nx-grid" style="grid-template-columns: 1fr 1fr; margin-bottom:16px;">
      <div class="nx-card">
        <h3>Health / PolicySnapshot</h3>
        <p class="nx-code">{{ health ? JSON.stringify(health) : "—" }}</p>
        <p v-if="snapshot" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(snapshot, null, 2) }}</p>
      </div>
      <div class="nx-card">
        <h3>Evaluate（POST /check，与 list/detail/flow 同一引擎）</h3>
        <form @submit.prevent="onCheck">
          <div class="nx-field"><label>resource</label><input v-model="checkForm.resource" required /></div>
          <div class="nx-field"><label>action</label><input v-model="checkForm.action" required /></div>
          <button class="primary" type="submit">检查</button>
        </form>
        <p v-if="decision" class="nx-code" style="margin-top:12px;" data-testid="g2-check-result">
          {{ JSON.stringify(decision, null, 2) }}
        </p>
      </div>
    </div>

    <div v-if="kind === 'roles'" class="nx-card" style="margin-bottom:16px;">
      <h3>角色</h3>
      <form class="nx-row" data-testid="permission-role-form" @submit.prevent="onCreateRole">
        <div class="nx-field"><label>code</label><input v-model="roleForm.code" required data-testid="permission-role-code" /></div>
        <div class="nx-field"><label>name</label><input v-model="roleForm.name" required data-testid="permission-role-name" /></div>
        <button class="primary" type="submit">新建角色</button>
      </form>
      <table class="nx-table" data-testid="g2-role-table">
        <thead><tr><th>code</th><th>name</th><th>system</th><th>id</th></tr></thead>
        <tbody>
          <tr v-for="row in roles" :key="row.id">
            <td>{{ row.code }}</td>
            <td>{{ row.name }}</td>
            <td>{{ pick(row, "system_role", "systemRole") ? "yes" : "no" }}</td>
            <td class="nx-code">{{ row.id }}</td>
          </tr>
        </tbody>
      </table>
      <h3 style="margin-top:16px;">分配</h3>
      <form class="nx-row" data-testid="permission-assignment-form" @submit.prevent="onAssign">
        <div class="nx-field">
          <label>主体类型</label>
          <select v-model="assignForm.principalType" required @change="assignForm.principalId = ''">
            <option value="user">人员</option>
            <option value="position">岗位</option>
            <option value="department">部门</option>
          </select>
        </div>
        <div class="nx-field">
          <label>主体</label>
          <select v-model="assignForm.principalId" required data-testid="permission-assignment-principal">
            <option value="" disabled>请选择</option>
            <option v-for="item in assignmentPrincipals" :key="item.id" :value="item.id">{{ item.label }}（{{ item.code }}）</option>
          </select>
        </div>
        <div class="nx-field">
          <label>角色</label>
          <select v-model="assignForm.roleId" required data-testid="permission-assignment-role">
            <option value="" disabled>请选择</option>
            <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}（{{ role.code }}）</option>
          </select>
        </div>
        <button class="primary" type="submit" data-testid="permission-assignment-submit">分配</button>
      </form>
      <table class="nx-table">
        <thead><tr><th>principal</th><th>role</th></tr></thead>
        <tbody>
          <tr v-for="row in assignments" :key="row.id">
            <td>{{ pick(row, "principal_type", "principalType") }}:{{ pick(row, "principal_id", "principalId") }}</td>
            <td>{{ pick(row, "role_code", "roleCode") }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="kind === 'datascope'" class="nx-card" style="margin-bottom:16px;">
      <h3>DataScope 单元格</h3>
      <form class="nx-row" data-testid="permission-grant-form" @submit.prevent="onGrant">
        <div class="nx-field">
          <label>角色</label>
          <select v-model="grantForm.principalId" required data-testid="permission-grant-role">
            <option value="" disabled>请选择</option>
            <option v-for="role in roles" :key="role.id" :value="role.code">{{ role.name }}（{{ role.code }}）</option>
          </select>
        </div>
        <div class="nx-field">
          <label>效果</label>
          <select v-model="grantForm.effect"><option>GRANT</option><option>DENY</option></select>
        </div>
        <div class="nx-field"><label>resource</label><input v-model="grantForm.resource" required /></div>
        <div class="nx-field"><label>action</label><input v-model="grantForm.action" required /></div>
        <div class="nx-field">
          <label>scope</label>
          <select v-model="grantForm.scopeKind" data-testid="permission-grant-scope">
            <option>SELF</option>
            <option>DEPT</option>
            <option>DEPT_AND_CHILDREN</option>
            <option>SPECIFIED</option>
            <option>ALL</option>
          </select>
        </div>
        <div v-if="grantForm.scopeKind === 'SPECIFIED'" class="nx-field">
          <label>指定部门（可多选）</label>
          <select v-model="grantForm.specifiedDepartments" multiple required data-testid="permission-grant-departments">
            <option v-for="department in principals.departments" :key="department.id" :value="department.id">
              {{ department.label }}（{{ department.code }}）
            </option>
          </select>
        </div>
        <button class="primary" type="submit" data-testid="permission-grant">写入授权</button>
      </form>
      <table class="nx-table" data-testid="g2-scope-table">
        <thead><tr><th>principal</th><th>resource</th><th>action</th><th>effect</th><th>scope</th></tr></thead>
        <tbody>
          <tr v-for="cell in scopeCells" :key="cell.id">
            <td>{{ pick(cell, "principal_type", "principalType") }}:{{ pick(cell, "principal_id", "principalId") }}</td>
            <td>{{ cell.resource }}</td>
            <td>{{ cell.action }}</td>
            <td>{{ cell.effect }}</td>
            <td>{{ pick(cell, "scope_kind", "scopeKind") }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="kind === 'fields'" class="nx-card">
      <h3>字段策略</h3>
      <p class="nx-muted">隐藏字段在 list/detail 投影中丢弃；不可编辑字段在 write 时 403。</p>
      <table class="nx-table" data-testid="g2-field-table">
        <thead><tr><th>principal</th><th>resource/action</th><th>field</th><th>visible</th><th>editable</th><th>mask</th></tr></thead>
        <tbody>
          <tr v-if="!fieldRows.length">
            <td colspan="6" class="nx-empty">没有字段策略。tenant_member 种子是空字段图，不算 G2 完成。</td>
          </tr>
          <tr v-for="row in fieldRows" :key="row.key">
            <td>{{ row.principal }}</td>
            <td>{{ row.resource }}/{{ row.action }}</td>
            <td>{{ row.field }}</td>
            <td>{{ row.visible ? "yes" : "no" }}</td>
            <td>{{ row.editable ? "yes" : "no" }}</td>
            <td>{{ row.mask }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { perm } from "../../api/client";
import { session } from "../../api/session";

const route = useRoute();
const kind = computed(() => route.params.kind || "roles");
const title = computed(() => ({
  roles: "角色矩阵",
  datascope: "数据范围",
  fields: "字段权限",
}[kind.value] || "权限"));
const banner = computed(() =>
  `${title.value} 调用 /api/v1/permission/matrix|roles|grants|check。tenant_member 种子不是 G2 完成条件。`);

const health = ref(null);
const snapshot = ref(null);
const matrix = ref(null);
const principals = ref({ users: [], positions: [], departments: [] });
const decision = ref(null);
const error = ref("");
const checkForm = reactive({ resource: "ledger", action: "read" });
const roleForm = reactive({ code: "", name: "" });
const assignForm = reactive({ principalType: "user", principalId: "", roleId: "" });
const grantForm = reactive({
  effect: "GRANT",
  principalType: "role",
  principalId: "",
  resource: "ledger",
  action: "read",
  scopeKind: "SELF",
  specifiedDepartments: [],
});

const roles = computed(() => matrix.value?.roles || []);
const assignments = computed(() => matrix.value?.assignments || []);
const assignmentPrincipals = computed(() => {
  const key = assignForm.principalType === "position"
    ? "positions"
    : assignForm.principalType === "department" ? "departments" : "users";
  return principals.value[key] || [];
});
const cells = computed(() => matrix.value?.cells || []);
const scopeCells = computed(() => cells.value);
const fieldRows = computed(() => {
  const rows = [];
  for (const cell of cells.value) {
    const fields = cell.fields || {};
    for (const [name, rule] of Object.entries(fields)) {
      rows.push({
        key: `${cell.id}:${name}`,
        principal: `${pick(cell, "principal_type", "principalType")}:${pick(cell, "principal_id", "principalId")}`,
        resource: cell.resource,
        action: cell.action,
        field: name,
        visible: rule.visible,
        editable: rule.editable,
        mask: rule.mask,
      });
    }
  }
  return rows;
});

async function reloadMatrix() {
  if (!session.contextId) {
    return;
  }
  [snapshot.value, matrix.value, principals.value] = await Promise.all([
    perm.snapshot(),
    perm.matrix(),
    perm.principals(),
  ]);
}

onMounted(async () => {
  try {
    health.value = await perm.health();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
  if (!session.contextId) {
    return;
  }
  try {
    await reloadMatrix();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

function pick(obj, ...keys) {
  if (!obj) {
    return undefined;
  }
  for (const key of keys) {
    if (obj[key] !== undefined && obj[key] !== null) {
      return obj[key];
    }
  }
  return undefined;
}

async function onCheck() {
  try {
    decision.value = await perm.check({ resource: checkForm.resource, action: checkForm.action });
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    decision.value = err.body || { message: err.message };
  }
}

async function onInstall() {
  try {
    matrix.value = await perm.installExample();
    snapshot.value = await perm.snapshot();
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function onCreateRole() {
  try {
    await perm.createRole({ code: roleForm.code, name: roleForm.name, systemRole: false });
    roleForm.code = "";
    roleForm.name = "";
    await reloadMatrix();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function onAssign() {
  try {
    await perm.assign({
      principalType: assignForm.principalType,
      principalId: assignForm.principalId,
      roleId: assignForm.roleId,
    });
    assignForm.principalId = "";
    assignForm.roleId = "";
    error.value = "";
    await reloadMatrix();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}

async function onGrant() {
  try {
    await perm.grant({
      effect: grantForm.effect,
      principalType: grantForm.principalType,
      principalId: grantForm.principalId,
      resource: grantForm.resource,
      action: grantForm.action,
      scopeKind: grantForm.scopeKind,
      specifiedDepartments: grantForm.scopeKind === "SPECIFIED" ? grantForm.specifiedDepartments : [],
      fields: {},
    });
    error.value = "";
    await reloadMatrix();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.primary { background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 12px; height: 38px; align-self: end; }
</style>
