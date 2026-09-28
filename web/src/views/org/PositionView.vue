<template>
  <div>
    <NxPageHeader
      eyebrow="星权"
      title="岗位任职"
      description="岗位绑定角色，人员经在职任职获得岗位的角色；任职结束，角色随之失效。"
    />
    <div class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <form class="nx-card" @submit.prevent="createPos">
        <h3>新建岗位</h3>
        <div class="nx-field"><label>编码</label><input v-model="pos.code" required /></div>
        <div class="nx-field"><label>名称</label><input v-model="pos.name" required /></div>
        <div class="nx-field"><label>类别</label><input v-model="pos.category" /></div>
        <button class="primary" type="submit">保存岗位</button>
      </form>
      <form class="nx-card" @submit.prevent="assign">
        <h3>任职</h3>
        <div class="nx-field"><label>人员 ID</label><input v-model="emp.person_id" required /></div>
        <div class="nx-field"><label>部门 ID</label><input v-model="emp.department_id" required /></div>
        <div class="nx-field"><label>岗位 ID</label><input v-model="emp.position_id" required /></div>
        <button class="primary" type="submit">分配</button>
      </form>
    </div>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-if="bindingNotice" class="nx-muted" data-testid="binding-notice">{{ bindingNotice }}</p>
    <table class="nx-table" style="margin-top:16px;">
      <thead><tr><th>编码</th><th>名称</th><th>类别</th><th>角色</th><th v-if="canBind">操作</th></tr></thead>
      <tbody>
        <tr v-for="row in positions" :key="row.id" :data-testid="`position-${row.id}`">
          <td>{{ row.code }}</td>
          <td>{{ row.name }}</td>
          <td>{{ row.category }}</td>
          <td class="roles">
            <span v-for="role in rolesOf(row.id)" :key="role.role_id" class="role-chip">{{ role.role_name || role.role_code }}</span>
            <span v-if="!rolesOf(row.id).length" class="nx-muted">未绑定</span>
          </td>
          <td v-if="canBind" class="row-actions">
            <button type="button" class="link" @click="openEditor(row)">编辑角色</button>
            <button type="button" class="link" data-action="preview" @click="openPreview(row)">有效权限</button>
          </td>
        </tr>
      </tbody>
    </table>

    <form v-if="editing" class="nx-card editor" data-testid="role-editor" @submit.prevent="saveRoles">
      <h3>「{{ editing.name }}」的角色</h3>
      <p v-if="!roles.length" class="nx-muted">当前租户还没有角色，请先到「权限」创建角色。</p>
      <label v-for="role in roles" :key="role.id" class="role-option">
        <input v-model="selected" type="checkbox" :value="role.id" />
        {{ role.name || role.code }} <span class="nx-code">{{ role.code }}</span>
      </label>
      <p v-if="editorError" class="nx-error">{{ editorError }}</p>
      <div class="actions">
        <button class="primary" type="submit" :disabled="saving">保存</button>
        <button type="button" @click="closeEditor">取消</button>
      </div>
    </form>

    <section v-if="previewing" class="nx-card editor" data-testid="position-preview">
      <h3>「{{ previewing.name }}」的有效权限</h3>
      <p class="nx-muted">只含岗位本身带来的角色与授权，不含个人例外授权。</p>
      <EffectivePermissionPanel :effective="preview" :loading="previewLoading" :error="previewError" />
      <div class="actions"><button type="button" @click="previewing = null">关闭</button></div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { NxPageHeader } from "../../components";
import { p2, perm } from "../../api/client";
import EffectivePermissionPanel from "./EffectivePermissionPanel.vue";

const positions = ref([]);
const previewing = ref(null);
const preview = ref(null);
const previewLoading = ref(false);
const previewError = ref("");
const roles = ref([]);
const bindings = ref({});
const canBind = ref(false);
const bindingNotice = ref("");
const error = ref("");
const editing = ref(null);
const selected = ref([]);
const editorError = ref("");
const saving = ref(false);
const pos = reactive({ code: "", name: "", category: "ic", duties: "" });
const emp = reactive({ person_id: "", department_id: "", position_id: "", kind: "SECONDARY" });

function message(err) {
  return `${err.code || ""} ${err.message}`.trim();
}

function rolesOf(positionId) {
  return bindings.value[positionId] || [];
}

/** PLA3: what holding this position brings, recomputed each time (bindings may just have changed). */
async function openPreview(row) {
  previewing.value = row;
  preview.value = null;
  previewError.value = "";
  previewLoading.value = true;
  try {
    const result = await perm.effectivePosition(row.id);
    if (previewing.value?.id === row.id) preview.value = result;
  } catch (err) {
    if (previewing.value?.id === row.id) previewError.value = message(err);
  } finally {
    previewLoading.value = false;
  }
}

function indexBindings(rows) {
  const out = {};
  for (const row of rows || []) out[row.position_id || row.positionId] = row.roles || [];
  return out;
}

async function reload() {
  positions.value = (await p2.positions()) || [];
}

/** Bindings need permission read access; without it the page stays a plain position list. */
async function loadBindings() {
  try {
    const [rows, roleRows] = await Promise.all([perm.positionRoles(), perm.roles()]);
    bindings.value = indexBindings(rows);
    roles.value = (roleRows || []).filter((role) => !role.status || role.status === "ACTIVE");
    canBind.value = true;
    bindingNotice.value = "";
  } catch (err) {
    canBind.value = false;
    bindingNotice.value = err.status === 403 || err.code === "P2.PERM.0003"
      ? "你没有查看角色绑定的权限，仅显示岗位。"
      : `角色绑定暂不可用：${message(err)}`;
  }
}

onMounted(async () => {
  try {
    await reload();
  } catch (err) {
    error.value = message(err);
  }
  await loadBindings();
});

async function createPos() {
  try {
    await p2.createPosition(pos);
    await reload();
  } catch (err) {
    error.value = message(err);
  }
}

async function assign() {
  try {
    await p2.assignEmployment(emp);
  } catch (err) {
    error.value = message(err);
  }
}

function openEditor(row) {
  editing.value = row;
  selected.value = rolesOf(row.id).map((role) => role.role_id || role.roleId);
  editorError.value = "";
}

function closeEditor() {
  editing.value = null;
}

async function saveRoles() {
  saving.value = true;
  editorError.value = "";
  try {
    const saved = await perm.setPositionRoles(editing.value.id, selected.value);
    bindings.value = { ...bindings.value, [editing.value.id]: saved || [] };
    editing.value = null;
  } catch (err) {
    editorError.value = err.code === "P2.PERM.0003" || err.status === 403
      ? "你没有修改岗位角色的权限。"
      : message(err);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.primary { background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 12px; }
.link { background: none; border: 0; color: var(--nx-product); cursor: pointer; padding: 0; }
.roles { display: flex; flex-wrap: wrap; gap: 4px; }
.role-chip { border: 1px solid var(--nx-border, #d0d5dd); border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.editor { margin-top: 16px; max-width: 520px; }
.role-option { display: flex; gap: 8px; align-items: center; padding: 4px 0; }
.actions { display: flex; gap: 8px; margin-top: 12px; }
.row-actions { display: flex; gap: 12px; }
</style>
