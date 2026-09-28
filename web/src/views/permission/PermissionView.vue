<template>
  <section class="permissions-page">
    <NxPageHeader
      eyebrow="星权 · 权限管理"
      :title="pageTitle"
      description="配置角色、人员分配、数据可见范围与字段规则。谁能执行操作，由服务端按当前策略统一判定。"
    />

    <p class="permission-explainer" data-testid="permission-explainer" role="note">
      本页回答「谁能做什么」：角色定义能力，分配绑定到人，矩阵决定允许或拒绝。改动会进入策略版本，不影响登录与组织目录本身。
    </p>

    <NxSourceBanner body="本页只读写 /api/v1/permission 已提供的角色、分配、授权矩阵与快照；不在前端定义权限码或实现数据范围。" />

    <nav class="permission-tabs" aria-label="权限管理分区">
      <router-link
        v-for="tab in tabs"
        :key="tab.id"
        :to="tab.to"
        :aria-current="kind === tab.id ? 'page' : undefined"
        :class="{ active: kind === tab.id }"
        :data-testid="`permission-tab-${tab.id}`"
      >
        {{ tab.label }}
      </router-link>
    </nav>

    <NxErrorState
      v-if="error"
      :description="error"
      :trace-id="traceId"
      retry-label="重试"
      @retry="load"
    />

    <NxNoPermission
      v-if="!session.contextId"
      title="需要租户上下文"
      description="请先登录并切换到服务端签发的租户上下文，再查看或管理权限。"
    />

    <template v-else>
      <div class="overview-grid" aria-label="权限服务状态">
        <section class="nx-card overview-card">
          <span class="nx-muted">权限服务</span>
          <strong>{{ health?.status || (loading ? "加载中…" : "暂无") }}</strong>
          <small>{{ health?.module || "由服务端提供" }}</small>
        </section>
        <section class="nx-card overview-card">
          <span class="nx-muted">策略版本</span>
          <strong>{{ snapshot?.version ?? "暂无" }}</strong>
          <small>PolicySnapshot</small>
        </section>
        <section class="nx-card overview-card">
          <span class="nx-muted">授权 / 拒绝规则</span>
          <strong>{{ snapshot ? `${snapshot.grant_count ?? snapshot.grantCount ?? 0} / ${snapshot.deny_count ?? snapshot.denyCount ?? 0}` : "暂无" }}</strong>
          <small>当前租户上下文</small>
        </section>
      </div>

      <ExceptionGrantsPanel v-if="kind === 'exceptions'" />

      <section v-else-if="kind === 'matrix'" class="nx-card content-card" data-testid="permission-matrix">
        <div class="section-heading">
          <div>
            <h2>权限矩阵总览</h2>
            <p class="nx-muted">矩阵内容来自当前租户的服务端策略快照。</p>
          </div>
          <button class="primary" type="button" :disabled="loading" @click="load">刷新</button>
        </div>
        <div v-if="matrix" class="matrix-summary">
          <span>角色 <strong>{{ matrix.roles?.length ?? 0 }}</strong></span>
          <span>分配 <strong>{{ matrix.assignments?.length ?? 0 }}</strong></span>
          <span>授权单元 <strong>{{ matrix.cells?.length ?? 0 }}</strong></span>
          <span>差异化策略 <strong>{{ matrix.differentiatedPolicy ? "是" : "否" }}</strong></span>
        </div>
        <p v-if="matrix?.completenessNote" class="completeness-note">{{ matrix.completenessNote }}</p>
        <div v-if="matrix" class="matrix-browser" data-testid="resource-action-matrix">
          <label class="matrix-search"><span>筛选资源、操作或主体</span><input v-model="matrixSearch" type="search" placeholder="输入接口返回的名称" data-testid="matrix-search" /></label>
          <p class="nx-muted matrix-note">交叉格仅展示当前策略快照返回的授权；空格表示接口没有返回匹配的授权单元。</p>
          <div v-if="!matrixResources.length" class="matrix-empty" role="status">暂无匹配的资源 / 操作授权</div>
          <div v-else class="table-scroll matrix-scroll">
            <table class="nx-table matrix-table" data-testid="resource-action-table">
              <thead><tr><th class="resource-heading">Resource</th><th v-for="action in matrixActions" :key="action">{{ action }}</th></tr></thead>
              <tbody><tr v-for="resource in matrixResources" :key="resource">
                <th scope="row" class="resource-name">{{ resource }}</th>
                <td v-for="action in matrixActions" :key="`${resource}:${action}`" :data-testid="`matrix-cell-${resource}-${action}`">
                  <ul v-if="matrixGrants(resource, action).length" class="matrix-grants">
                    <li v-for="grant in matrixGrants(resource, action)" :key="grant.id" class="matrix-grant">
                      <strong class="effect" :class="grant.effect === 'DENY' ? 'deny' : 'grant'">{{ grant.effect || '暂无效果' }}</strong>
                      <span class="principal">{{ grant.principalType || '暂无主体类型' }} · {{ grant.principalId || '暂无主体 ID' }}</span>
                      <small>范围：{{ grant.scopeKind || '暂无' }}</small>
                    </li>
                  </ul>
                  <span v-else class="matrix-unset">—</span>
                </td>
              </tr></tbody>
            </table>
          </div>
        </div>
        <NxEmpty v-if="!loading && !matrix" title="暂无权限矩阵" description="服务端尚未返回当前租户的策略矩阵。" />
      </section>

      <template v-else-if="kind === 'roles'">
        <section class="nx-card content-card">
          <div class="section-heading">
            <div>
              <h2>角色</h2>
              <p class="nx-muted">角色标识由管理员输入并由服务端校验；系统角色不可在此改写。</p>
            </div>
          </div>
          <form class="form-grid role-form" data-testid="create-role-form" @submit.prevent="createRole">
            <label class="nx-field">
              <span>角色编码</span>
              <input v-model.trim="roleForm.code" autocomplete="off" required />
            </label>
            <label class="nx-field">
              <span>角色名称</span>
              <input v-model.trim="roleForm.name" autocomplete="off" required />
            </label>
            <button class="primary" type="submit" :disabled="saving">新建角色</button>
          </form>
          <NxEmpty
            v-if="!loading && !roles.length"
            title="暂无角色"
            description="当前租户尚无可显示角色。"
            compact
          />
          <div v-else-if="roles.length" class="table-scroll">
            <table class="nx-table" data-testid="role-table">
              <thead><tr><th>编码</th><th>名称</th><th>类型</th><th>状态</th><th>ID</th></tr></thead>
              <tbody>
                <tr v-for="role in roles" :key="role.id">
                  <td class="code-cell">{{ role.code }}</td>
                  <td>{{ role.name }}</td>
                  <td>{{ role.systemRole ? "系统角色" : "自定义角色" }}</td>
                  <td>{{ role.status || "暂无" }}</td>
                  <td class="code-cell">{{ role.id }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="nx-card content-card">
          <div class="section-heading">
            <div>
              <h2>角色分配</h2>
              <p class="nx-muted">principal type / ID 使用身份服务已有主体标识。</p>
            </div>
          </div>
          <form class="form-grid assignment-form" data-testid="create-assignment-form" @submit.prevent="createAssignment">
            <label class="nx-field"><span>主体类型</span><input v-model.trim="assignmentForm.principalType" required /></label>
            <label class="nx-field"><span>主体 ID</span><input v-model.trim="assignmentForm.principalId" required /></label>
            <label class="nx-field">
              <span>角色</span>
              <select v-model="assignmentForm.roleId" required>
                <option disabled value="">选择已有角色</option>
                <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.code }} · {{ role.name }}</option>
              </select>
            </label>
            <button class="primary" type="submit" :disabled="saving || !roles.length">分配角色</button>
          </form>
          <NxEmpty
            v-if="!loading && !assignments.length"
            title="暂无角色分配"
            description="当前租户尚无可显示的主体角色分配。"
            compact
          />
          <div v-else-if="assignments.length" class="table-scroll">
            <table class="nx-table" data-testid="assignment-table">
              <thead><tr><th>主体</th><th>主体 ID</th><th>角色</th><th>分配 ID</th></tr></thead>
              <tbody>
                <tr v-for="assignment in assignments" :key="assignment.id">
                  <td>{{ assignment.principalType }}<span v-if="assignment.principalType === 'user'" class="exception-badge" title="直接给个人，不经过岗位（D5）">例外</span></td>
                  <td class="code-cell">{{ assignment.principalId }}</td>
                  <td>{{ assignment.roleCode || assignment.roleId }}</td>
                  <td class="code-cell">{{ assignment.id }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <template v-else-if="kind === 'datascope'">
        <section class="nx-card content-card">
          <div class="section-heading">
            <div>
              <h2>数据范围授权</h2>
              <p class="nx-muted">范围类型沿用服务端 GrantBody.scopeKind；指定部门仅在选择 SPECIFIED 时提交。</p>
            </div>
          </div>
          <form class="form-grid grant-form" data-testid="create-grant-form" @submit.prevent="createGrant">
            <label class="nx-field"><span>效果</span><select v-model="grantForm.effect"><option value="GRANT">GRANT</option><option value="DENY">DENY</option></select></label>
            <label class="nx-field"><span>主体类型</span><input v-model.trim="grantForm.principalType" required /></label>
            <label class="nx-field"><span>主体 ID / 角色编码</span><input v-model.trim="grantForm.principalId" required /></label>
            <label class="nx-field"><span>资源</span><input v-model.trim="grantForm.resource" required /></label>
            <label class="nx-field"><span>操作</span><input v-model.trim="grantForm.action" required /></label>
            <label class="nx-field">
              <span>数据范围</span>
              <select v-model="grantForm.scopeKind" required>
                <option disabled value="">选择后端已有范围类型</option>
                <option v-for="scope in scopeKinds" :key="scope" :value="scope">{{ scope }}</option>
              </select>
            </label>
            <label v-if="grantForm.scopeKind === 'SPECIFIED'" class="nx-field specified-field">
              <span>指定部门 ID（逗号分隔）</span>
              <input v-model="grantForm.specifiedDepartments" autocomplete="off" />
            </label>
            <button class="primary" type="submit" :disabled="saving">写入授权</button>
          </form>
          <NxEmpty v-if="!loading && !grants.length" title="暂无数据范围授权" description="当前租户没有可显示的授权单元。" compact />
          <div v-else-if="grants.length" class="table-scroll">
            <table class="nx-table" data-testid="scope-table">
              <thead><tr><th>主体</th><th>资源 / 操作</th><th>效果</th><th>数据范围</th><th>指定部门</th></tr></thead>
              <tbody>
                <tr v-for="grant in grants" :key="grant.id">
                  <td>{{ grant.principalType }}:{{ grant.principalId }}<span v-if="grant.principalType === 'user'" class="exception-badge" title="直接给个人，不经过岗位（D5）">例外</span></td>
                  <td>{{ grant.resource }} / {{ grant.action }}</td>
                  <td>{{ grant.effect }}</td>
                  <td>{{ grant.scopeKind || "暂无" }}</td>
                  <td>{{ (grant.specifiedDepartments || []).join("、") || "—" }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <section v-else-if="kind === 'fields'" class="nx-card content-card">
        <div class="section-heading">
          <div>
            <h2>字段权限</h2>
            <p class="nx-muted">字段名和规则均来自服务端 fields 映射；隐藏字段投影与不可编辑写入由服务端执行。</p>
          </div>
        </div>
        <form class="form-grid grant-form" data-testid="create-field-grant-form" @submit.prevent="createFieldGrant">
          <label class="nx-field"><span>效果</span><select v-model="fieldForm.effect"><option value="GRANT">GRANT</option><option value="DENY">DENY</option></select></label>
          <label class="nx-field"><span>主体类型</span><input v-model.trim="fieldForm.principalType" required /></label>
          <label class="nx-field"><span>主体 ID / 角色编码</span><input v-model.trim="fieldForm.principalId" required /></label>
          <label class="nx-field"><span>资源</span><input v-model.trim="fieldForm.resource" required /></label>
          <label class="nx-field"><span>操作</span><input v-model.trim="fieldForm.action" required /></label>
          <label class="nx-field">
            <span>数据范围</span>
            <select v-model="fieldForm.scopeKind" required>
              <option disabled value="">选择后端已有范围类型</option>
              <option v-for="scope in scopeKinds" :key="scope" :value="scope">{{ scope }}</option>
            </select>
          </label>
          <label v-if="fieldForm.scopeKind === 'SPECIFIED'" class="nx-field specified-field">
            <span>指定部门 ID（逗号分隔）</span>
            <input v-model="fieldForm.specifiedDepartments" autocomplete="off" />
          </label>
          <div class="field-rules" data-testid="field-rules">
            <div v-for="(rule, index) in fieldForm.rules" :key="index" class="field-rule">
              <label class="nx-field"><span>字段名</span><input v-model.trim="rule.name" required /></label>
              <label class="nx-field"><span>遮罩</span><select v-model="rule.mask"><option v-for="mask in maskKinds" :key="mask" :value="mask">{{ mask }}</option></select></label>
              <label class="check-field"><input v-model="rule.visible" type="checkbox" /> 可见</label>
              <label class="check-field"><input v-model="rule.editable" type="checkbox" /> 可编辑</label>
              <label class="check-field"><input v-model="rule.secondAuthorization" type="checkbox" /> 二次授权</label>
              <button class="text-button" type="button" :disabled="fieldForm.rules.length === 1" @click="removeFieldRule(index)">移除</button>
            </div>
            <button class="secondary" type="button" @click="addFieldRule">添加字段规则</button>
          </div>
          <button class="primary" type="submit" :disabled="saving">写入字段策略</button>
        </form>
        <NxEmpty v-if="!loading && !fieldRows.length" title="暂无字段策略" description="当前授权矩阵未返回 fields 规则。" compact />
        <div v-else-if="fieldRows.length" class="table-scroll">
          <table class="nx-table" data-testid="field-table">
            <thead><tr><th>主体</th><th>资源 / 操作</th><th>字段</th><th>可见</th><th>可编辑</th><th>遮罩</th><th>二次授权</th></tr></thead>
            <tbody>
              <tr v-for="row in fieldRows" :key="row.key">
                <td>{{ row.principalType }}:{{ row.principalId }}</td>
                <td>{{ row.resource }} / {{ row.action }}</td>
                <td>{{ row.name }}</td>
                <td>{{ row.rule.visible ? "是" : "否" }}</td>
                <td>{{ row.rule.editable ? "是" : "否" }}</td>
                <td>{{ row.rule.mask || "NONE" }}</td>
                <td>{{ row.rule.secondAuthorization ? "是" : "否" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxNoPermission, NxPageHeader } from "../../components";
import NxSourceBanner from "../../components/NxSourceBanner.vue";
import { perm } from "../../api/client";
import { session } from "../../api/session";
import ExceptionGrantsPanel from "./ExceptionGrantsPanel.vue";

const route = useRoute();
const tabs = [
  { id: "roles", label: "角色与分配", to: "/p2/permissions/roles" },
  { id: "datascope", label: "数据范围", to: "/p2/permissions/datascope" },
  { id: "fields", label: "字段权限", to: "/p2/permissions/fields" },
  { id: "matrix", label: "矩阵总览", to: "/p2/permissions/matrix" },
  { id: "exceptions", label: "例外授权", to: "/p2/permissions/exceptions" },
];
// Values are the existing PermissionController DataScopeKind / Mask enums. The
// browser only submits their names; evaluation remains in PermissionEngine.
const scopeKinds = ["SELF", "DEPT", "DEPT_AND_CHILDREN", "SPECIFIED", "ALL"];
const maskKinds = ["NONE", "PARTIAL", "FULL"];
const kind = computed(() => tabs.some((tab) => tab.id === route.params.kind) ? route.params.kind : "roles");
const pageTitle = computed(() => ({
  roles: "角色与分配",
  datascope: "数据范围",
  fields: "字段权限",
  matrix: "权限矩阵",
  exceptions: "例外授权",
}[kind.value]));

const loading = ref(false);
const saving = ref(false);
const error = ref("");
const traceId = ref("");
const health = ref(null);
const snapshot = ref(null);
const matrix = ref(null);
const roles = ref([]);
const assignments = ref([]);
const grants = ref([]);
const matrixSearch = ref("");
const roleForm = reactive({ code: "", name: "" });
const assignmentForm = reactive({ principalType: "", principalId: "", roleId: "" });
const grantForm = reactive({
  effect: "GRANT",
  principalType: "",
  principalId: "",
  resource: "",
  action: "",
  scopeKind: "",
  specifiedDepartments: "",
});
const fieldForm = reactive({
  effect: "GRANT",
  principalType: "",
  principalId: "",
  resource: "",
  action: "",
  scopeKind: "",
  specifiedDepartments: "",
  rules: [newFieldRule()],
});

const fieldRows = computed(() => grants.value.flatMap((grant) =>
  Object.entries(grant.fields || {}).map(([name, rule]) => ({
    key: `${grant.id}:${name}`,
    principalType: grant.principalType,
    principalId: grant.principalId,
    resource: grant.resource,
    action: grant.action,
    name,
    rule,
  })),
));

const matrixCells = computed(() => Array.isArray(matrix.value?.cells) ? matrix.value.cells : grants.value);
const matchingMatrixCells = computed(() => {
  const term = matrixSearch.value.trim().toLocaleLowerCase();
  if (!term) return matrixCells.value;
  return matrixCells.value.filter((cell) => [cell.resource, cell.action, cell.principalType, cell.principalId]
    .some((value) => String(value || "").toLocaleLowerCase().includes(term)));
});
const matrixResources = computed(() => [...new Set(matchingMatrixCells.value.map((cell) => cell.resource).filter(Boolean))].sort());
const matrixActions = computed(() => [...new Set(matchingMatrixCells.value.map((cell) => cell.action).filter(Boolean))].sort());
function matrixGrants(resource, action) {
  return matchingMatrixCells.value.filter((cell) => cell.resource === resource && cell.action === action);
}

function newFieldRule() {
  return { name: "", visible: true, editable: false, mask: "NONE", secondAuthorization: false };
}

function normalizeError(reason) {
  if (!reason) return "加载权限数据失败";
  traceId.value = reason.traceId || reason.body?.trace_id || reason.body?.traceId || "";
  if (reason.status === 403) return "当前账号无权查看或管理该租户的权限。";
  return reason.message || "加载权限数据失败，请重试。";
}

async function load() {
  loading.value = true;
  error.value = "";
  traceId.value = "";
  try {
    const healthResult = await perm.health();
    health.value = healthResult;
  } catch {
    health.value = null;
  }
  if (!session.contextId) {
    matrix.value = null;
    snapshot.value = null;
    roles.value = [];
    assignments.value = [];
    grants.value = [];
    loading.value = false;
    return;
  }

  const results = await Promise.allSettled([
    perm.matrix(),
    perm.snapshot(),
    perm.roles(),
    perm.assignments(),
    perm.grants(),
  ]);
  [matrix, snapshot, roles, assignments, grants].forEach((target, index) => {
    if (results[index].status === "fulfilled") target.value = results[index].value;
  });
  const failure = results.find((result) => result.status === "rejected");
  if (failure) error.value = normalizeError(failure.reason);
  loading.value = false;
}

async function submit(action) {
  saving.value = true;
  error.value = "";
  traceId.value = "";
  try {
    await action();
    await load();
  } catch (reason) {
    error.value = normalizeError(reason);
  } finally {
    saving.value = false;
  }
}

function createRole() {
  return submit(async () => {
    await perm.createRole({ code: roleForm.code, name: roleForm.name, systemRole: false });
    roleForm.code = "";
    roleForm.name = "";
  });
}

function createAssignment() {
  return submit(async () => {
    await perm.assign({
      principalType: assignmentForm.principalType,
      principalId: assignmentForm.principalId,
      roleId: assignmentForm.roleId,
    });
    assignmentForm.principalType = "";
    assignmentForm.principalId = "";
    assignmentForm.roleId = "";
  });
}

function grantBody({ includeFields = false } = {}) {
  const body = {
    effect: includeFields ? fieldForm.effect : grantForm.effect,
    principalType: includeFields ? fieldForm.principalType : grantForm.principalType,
    principalId: includeFields ? fieldForm.principalId : grantForm.principalId,
    resource: includeFields ? fieldForm.resource : grantForm.resource,
    action: includeFields ? fieldForm.action : grantForm.action,
    scopeKind: includeFields ? fieldForm.scopeKind : grantForm.scopeKind,
    specifiedDepartments: (includeFields ? fieldForm.scopeKind : grantForm.scopeKind) === "SPECIFIED"
      ? (includeFields ? fieldForm.specifiedDepartments : grantForm.specifiedDepartments)
        .split(",").map((id) => id.trim()).filter(Boolean)
      : [],
    fields: includeFields
      ? Object.fromEntries(fieldForm.rules.filter((rule) => rule.name).map((rule) => [rule.name, {
          visible: rule.visible,
          editable: rule.editable,
          mask: rule.mask,
          secondAuthorization: rule.secondAuthorization,
        }]))
      : {},
  };
  return body;
}

function createGrant() {
  return submit(async () => {
    await perm.grant(grantBody());
    grantForm.effect = "GRANT";
    grantForm.principalType = "";
    grantForm.principalId = "";
    grantForm.resource = "";
    grantForm.action = "";
    grantForm.scopeKind = "";
    grantForm.specifiedDepartments = "";
  });
}

function createFieldGrant() {
  return submit(async () => {
    await perm.grant(grantBody({ includeFields: true }));
    fieldForm.effect = "GRANT";
    fieldForm.principalType = "";
    fieldForm.principalId = "";
    fieldForm.resource = "";
    fieldForm.action = "";
    fieldForm.scopeKind = "";
    fieldForm.specifiedDepartments = "";
    fieldForm.rules = [newFieldRule()];
  });
}

function addFieldRule() {
  fieldForm.rules.push(newFieldRule());
}

function removeFieldRule(index) {
  if (fieldForm.rules.length > 1) fieldForm.rules.splice(index, 1);
}

onMounted(load);
watch(() => session.contextId, load);
</script>

<style scoped>
.permissions-page { display: grid; gap: var(--nx-space-4); }
.permission-explainer {
  margin: 0;
  padding: var(--nx-space-3) var(--nx-space-4);
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-info-light);
  color: var(--nx-text-regular);
  font-size: var(--nx-font-size-sm);
  line-height: 1.5;
}
.permission-tabs { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); border-bottom: 1px solid var(--nx-border); }
.permission-tabs a { padding: var(--nx-space-2) var(--nx-space-3); color: var(--nx-text-secondary); text-decoration: none; border-bottom: 2px solid transparent; }
.permission-tabs a.active { color: var(--nx-product); border-color: var(--nx-product); font-weight: 600; }
.overview-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--nx-space-3); }
.overview-card { display: grid; gap: var(--nx-space-1); }
.overview-card strong { color: var(--nx-text-primary); font-size: var(--nx-font-size-lg); }
.overview-card small { color: var(--nx-text-secondary); }
.content-card { display: grid; gap: var(--nx-space-4); }
.section-heading { display: flex; align-items: start; justify-content: space-between; gap: var(--nx-space-3); }
.section-heading h2 { margin: 0; }
.section-heading p { margin: var(--nx-space-1) 0 0; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); align-items: end; gap: var(--nx-space-3); }
.nx-field { display: grid; gap: var(--nx-space-1); }
.nx-field span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.nx-field input, .nx-field select { min-width: 0; width: 100%; }
.form-grid > .primary { min-height: 38px; }
.specified-field { grid-column: 1 / -1; }
.table-scroll { overflow-x: auto; }
.code-cell { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
.exception-badge { margin-left: 6px; border-radius: 10px; padding: 0 6px; font-size: 12px; background: var(--nx-warning-bg, #fef0c7); color: var(--nx-warning, #b54708); }
.matrix-summary { display: flex; flex-wrap: wrap; gap: var(--nx-space-4); }
.matrix-summary span { color: var(--nx-text-secondary); }
.matrix-summary strong { color: var(--nx-text-primary); }
.completeness-note { margin: 0; color: var(--nx-text-secondary); }
.matrix-browser { display: grid; gap: var(--nx-space-3); }
.matrix-search { display: grid; gap: var(--nx-space-1); max-width: 420px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.matrix-search input { min-height: 38px; padding: var(--nx-space-2) var(--nx-space-3); color: var(--nx-text-primary); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); border-radius: 6px; }
.matrix-note { margin: 0; }
.matrix-scroll { max-height: 560px; border: 1px solid var(--nx-divider); border-radius: var(--nx-radius-sm); }
.matrix-table { min-width: 680px; border-collapse: separate; border-spacing: 0; }
.matrix-table th,.matrix-table td { min-width: 180px; padding: var(--nx-space-3); text-align: left; vertical-align: top; border-right: 1px solid var(--nx-divider); border-bottom: 1px solid var(--nx-divider); }
.matrix-table thead th { position: sticky; top: 0; z-index: 1; color: var(--nx-text-secondary); background: var(--nx-bg-sidebar); }
.matrix-table .resource-heading,.matrix-table .resource-name { position: sticky; left: 0; z-index: 2; min-width: 160px; color: var(--nx-text-primary); background: var(--nx-surface); font-weight: 600; }
.matrix-table .resource-heading { z-index: 3; background: var(--nx-bg-sidebar); }
.matrix-grants { display: grid; gap: var(--nx-space-2); margin: 0; padding: 0; list-style: none; }
.matrix-grant { display: grid; justify-items: start; gap: var(--nx-space-1); padding: var(--nx-space-2); background: var(--nx-bg-sidebar); border-radius: var(--nx-radius-sm); }
.effect { padding: 2px 6px; border-radius: 999px; font-size: var(--nx-font-size-xs); }.effect.grant { color: var(--nx-success); background: color-mix(in srgb, var(--nx-success) 12%, transparent); }.effect.deny { color: var(--nx-danger); background: color-mix(in srgb, var(--nx-danger) 12%, transparent); }
.principal { max-width: 240px; overflow-wrap: anywhere; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: var(--nx-font-size-xs); }.matrix-grant small,.matrix-unset,.matrix-empty { color: var(--nx-text-secondary); }.matrix-unset { display: inline-block; padding: var(--nx-space-1); }.matrix-empty { padding: var(--nx-space-5); text-align: center; }
.field-rules { display: grid; grid-column: 1 / -1; gap: var(--nx-space-3); }
.field-rule { display: grid; grid-template-columns: minmax(150px, 2fr) minmax(120px, 1fr) repeat(3, auto) auto; align-items: end; gap: var(--nx-space-3); padding-bottom: var(--nx-space-3); border-bottom: 1px solid var(--nx-border); }
.check-field { display: flex; align-items: center; gap: var(--nx-space-1); min-height: 38px; white-space: nowrap; }
.secondary, .text-button { color: var(--nx-product); background: transparent; border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); padding: var(--nx-space-2) var(--nx-space-3); }
.text-button { border: 0; }
@media (max-width: 720px) {
  .field-rule { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .section-heading { flex-direction: column; }
}
</style>
