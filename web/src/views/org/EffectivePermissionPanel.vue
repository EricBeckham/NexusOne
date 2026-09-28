<template>
  <div class="effective" data-testid="effective-permissions">
    <p v-if="error" class="nx-error">{{ error }}</p>
    <p v-else-if="loading" class="nx-muted">正在计算有效权限…</p>
    <template v-else-if="effective">
      <p v-if="effective.duty_conflict" class="nx-error" data-testid="duty-conflict">
        同时持有互斥的平台职责，引擎会拒绝全部操作。请调整岗位角色。
      </p>
      <h4>角色</h4>
      <p v-if="!roles.length" class="nx-muted">没有任何角色。</p>
      <ul v-else class="roles">
        <li v-for="role in roles" :key="`${role.role_code}-${role.via}-${role.via_id}`" :class="{ exception: role.via === 'PERSONAL' }">
          <strong>{{ role.role_name || role.role_code }}</strong>
          <span class="via">{{ viaText(role) }}</span>
        </li>
      </ul>
      <h4>最终权限</h4>
      <p v-if="!permissions.length" class="nx-muted">没有任何授权。</p>
      <table v-else class="nx-table">
        <thead><tr><th>资源</th><th>动作</th><th>结果</th><th>来源</th></tr></thead>
        <tbody>
          <tr v-for="item in permissions" :key="`${item.resource}:${item.action}`" :data-permission="`${item.resource}:${item.action}`">
            <td class="nx-code">{{ item.resource }}</td>
            <td>{{ item.action }}</td>
            <td :class="item.outcome === 'ALLOW' ? 'allow' : 'deny'">{{ item.outcome === 'ALLOW' ? '允许' : '拒绝' }}</td>
            <td>
              <span v-for="(source, index) in item.sources" :key="index" class="source" :class="{ deny: source.effect === 'DENY', exception: source.principal_type === 'user' }">
                {{ source.effect === 'DENY' ? '拒绝' : '授予' }} · {{ source.label }} · {{ scopeText(source.scope) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  effective: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: "" },
});

const roles = computed(() => props.effective?.roles || []);
const permissions = computed(() => props.effective?.permissions || []);

const VIA = { POSITION: "岗位", DEPARTMENT: "部门", PERSONAL: "个人例外授权", INHERITED: "继承自" };
const SCOPE = { SELF: "本人", DEPT: "本部门", DEPT_AND_CHILDREN: "本部门及下级", ALL: "全部", SPECIFIED: "指定部门", CUSTOM: "自定义" };

function viaText(role) {
  const label = VIA[role.via] || role.via;
  return role.via === "PERSONAL" ? label : `${label}：${role.via_label || role.via_id}`;
}

function scopeText(scope) {
  return SCOPE[scope] || scope;
}
</script>

<style scoped>
.effective h4 { margin: 12px 0 6px; font-size: 14px; }
.roles { list-style: none; padding: 0; margin: 0; display: grid; gap: 4px; }
.roles li { display: flex; justify-content: space-between; gap: 8px; }
.via { color: var(--nx-text-muted, #667085); font-size: 12px; }
.exception, .exception .via { color: var(--nx-warning, #b54708); }
.allow { color: var(--nx-success, #067647); }
.deny { color: var(--nx-danger, #b42318); }
.source { display: block; font-size: 12px; }
</style>
