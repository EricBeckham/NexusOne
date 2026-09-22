<template>
  <div>
    <div class="nx-page-header">
      <h1>{{ title }}</h1>
      <p>M0-05 权限引擎 live：<code>/api/v1/permission</code>。禁止前端自行拼权限码或实现 DataScope。</p>
    </div>
    <NxSourceBanner :body="banner" />
    <p v-if="!session.contextId" class="nx-error">没有 TenantContext。snapshot / check 需要服务端签发的上下文。</p>
    <p v-if="error" class="nx-error">{{ error }}</p>

    <div class="nx-grid" style="grid-template-columns: 1fr 1fr;">
      <div class="nx-card">
        <h3>Health</h3>
        <p class="nx-code">{{ health ? JSON.stringify(health) : "—" }}</p>
        <h3 style="margin-top:16px;">PolicySnapshot</h3>
        <p v-if="snapshot" class="nx-code">{{ JSON.stringify(snapshot, null, 2) }}</p>
        <p v-else class="nx-muted">无快照。GET /api/v1/permission/snapshot</p>
      </div>
      <div class="nx-card">
        <h3>Evaluate（POST /check）</h3>
        <form @submit.prevent="onCheck">
          <div class="nx-field"><label>resource</label><input v-model="checkForm.resource" required /></div>
          <div class="nx-field"><label>action</label><input v-model="checkForm.action" required /></div>
          <button class="primary" type="submit">检查</button>
        </form>
        <p v-if="decision" class="nx-code" style="margin-top:12px;">{{ JSON.stringify(decision, null, 2) }}</p>
        <p class="nx-muted" style="margin-top:12px;">
          没有 GET /api/v1/permission/roles 列表接口。角色写入是 POST /roles；本壳只读 snapshot + check。
          完整权限矩阵 UI 仍未关闭 G2/G9。
        </p>
      </div>
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
  roles: "角色授权",
  datascope: "数据范围",
  fields: "字段权限",
}[kind.value] || "权限"));
const banner = computed(() =>
  `${title.value} 调用 /api/v1/permission/health|snapshot|check。G2/G9 全量治理 UI 未关闭。`);

const health = ref(null);
const snapshot = ref(null);
const decision = ref(null);
const error = ref("");
const checkForm = reactive({ resource: "ledger", action: "read" });

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
    snapshot.value = await perm.snapshot();
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
});

async function onCheck() {
  try {
    decision.value = await perm.check({ resource: checkForm.resource, action: checkForm.action });
    error.value = "";
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
    decision.value = err.body || { message: err.message };
  }
}
</script>
