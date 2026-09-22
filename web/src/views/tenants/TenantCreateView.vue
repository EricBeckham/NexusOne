<template>
  <div>
    <div class="nx-page-header">
      <h1>创建租户</h1>
      <p>调用已合并的 <code>POST /api/v1/p1/tenants</code>。可选地用 bootstrap token 初始化 P2 租户管理员。</p>
    </div>
    <form class="nx-card" style="max-width:560px;" @submit.prevent="submit">
      <div class="nx-field"><label>名称</label><input v-model="form.name" required /></div>
      <div class="nx-field"><label>编码（可空，服务端分配）</label><input v-model="form.code" /></div>
      <div class="nx-field"><label>法定名称</label><input v-model="form.legal_name" /></div>
      <div class="nx-field"><label>管理员姓名</label><input v-model="form.admin.display_name" required /></div>
      <div class="nx-field"><label>管理员邮箱</label><input v-model="form.admin.email" type="email" required /></div>
      <label class="nx-row">
        <input v-model="initIam" type="checkbox" />
        同时调用 P2 <code>/internal/tenants/{id}/admin-users</code>
      </label>
        <button class="primary" type="submit" data-testid="create-tenant">创建</button>
    </form>
    <p v-if="error" class="nx-error">{{ error }}</p>
    <pre v-if="result" class="nx-card nx-code">{{ JSON.stringify(result, null, 2) }}</pre>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { p1, p2 } from "../../api/client";
import { ensureContext, session } from "../../api/session";

const router = useRouter();
const form = reactive({
  name: "",
  code: "",
  legal_name: "",
  admin: { display_name: "", email: "" },
  config: { locale: "zh-CN", timezone: "Asia/Shanghai", currency: "CNY" },
});
const initIam = ref(true);
const error = ref("");
const result = ref(null);

async function submit() {
  error.value = "";
  try {
    const created = await p1.createTenant(form);
    result.value = created;
    const tenant = created.tenant || created;
    if (initIam.value && session.bootstrapToken && tenant.id) {
      const iam = await p2.bootstrapAdmin(tenant.id, {
        display_name: form.admin.display_name,
        email: form.admin.email,
        username: form.admin.email,
      });
      result.value = { ...created, iam_admin: iam };
      if (session.accessToken) {
        await ensureContext();
      }
    }
    router.push(`/p1/tenants/${tenant.id}`);
  } catch (err) {
    error.value = `${err.code || ""} ${err.message}`;
  }
}
</script>

<style scoped>
.primary { margin-top: 12px; background: var(--nx-product); color: #fff; border: 0; border-radius: 6px; padding: 8px 14px; }
</style>
