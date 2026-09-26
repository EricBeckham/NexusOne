<template>
  <main class="register-page" data-testid="tenant-self-register">
    <NxPageHeader
      eyebrow="星枢 · 租户开通"
      title="租户自助注册"
      description="提交组织名称与管理员联系方式。平台审订后开通租户，无支付、无下单。"
      :crumbs="[{ label: '登录' }, { label: '租户自助注册' }]"
    />

    <NxResult
      v-if="submitted"
      tone="success"
      title="开通申请已提交"
      description="平台将审订您的注册申请。通过后管理员邮箱会用于初始化租户管理员，请留意后续通知。"
      data-testid="register-result"
    >
      <template #next>
        <router-link class="nx-btn nx-btn--primary" to="/login">返回登录</router-link>
        <button class="nx-btn nx-btn--default" type="button" data-testid="register-again" @click="reset">再提交一份</button>
      </template>
    </NxResult>

    <NxErrorState
      v-else-if="error"
      title="注册申请未能提交"
      :description="error"
      :trace-id="traceId"
      retry-label="返回修改"
      data-testid="register-error"
      @retry="error = ''"
    />

    <form v-else class="nx-card register-form" data-testid="register-form" @submit.prevent="submit">
      <div class="nx-field">
        <label for="org-name">组织名称 <span class="req" aria-hidden="true">*</span></label>
        <input id="org-name" v-model="form.org_name" required maxlength="200" data-testid="register-org-name" />
        <p v-if="fieldErrors.org_name" class="field-error">{{ fieldErrors.org_name }}</p>
      </div>
      <div class="nx-field">
        <label for="admin-name">管理员姓名 <span class="req" aria-hidden="true">*</span></label>
        <input id="admin-name" v-model="form.admin_display_name" required maxlength="200" data-testid="register-admin-name" />
      </div>
      <div class="nx-field">
        <label for="admin-email">管理员邮箱 <span class="req" aria-hidden="true">*</span></label>
        <input id="admin-email" v-model="form.admin_email" type="email" required maxlength="320" data-testid="register-admin-email" />
        <p v-if="fieldErrors.admin_email" class="field-error">{{ fieldErrors.admin_email }}</p>
      </div>
      <div class="nx-field">
        <label for="contact-note">补充说明（可选）</label>
        <textarea id="contact-note" v-model="form.contact_note" rows="3" maxlength="1000" data-testid="register-note" />
      </div>
      <p class="nx-muted tip">提交后状态为待审订。不会产生费用，也不会立即创建可登录租户。</p>
      <button class="nx-btn nx-btn--primary" type="submit" :disabled="busy" data-testid="register-submit">
        {{ busy ? "提交中…" : "提交开通申请" }}
      </button>
    </form>
  </main>
</template>

<script setup>
import { reactive, ref } from "vue";
import { NxErrorState, NxPageHeader, NxResult } from "../../components";
import { p1 } from "../../api/client";

const form = reactive({
  org_name: "",
  admin_display_name: "",
  admin_email: "",
  contact_note: "",
});
const fieldErrors = reactive({ org_name: "", admin_email: "" });
const busy = ref(false);
const submitted = ref(false);
const error = ref("");
const traceId = ref("");

function validate() {
  fieldErrors.org_name = form.org_name.trim() ? "" : "请填写组织名称";
  fieldErrors.admin_email = /.+@.+\..+/.test(form.admin_email.trim()) ? "" : "请填写有效的管理员邮箱";
  return !fieldErrors.org_name && !fieldErrors.admin_email && form.admin_display_name.trim();
}

async function submit() {
  error.value = "";
  if (!validate()) return;
  busy.value = true;
  try {
    await p1.applyTenantRegistration({
      org_name: form.org_name.trim(),
      admin_display_name: form.admin_display_name.trim(),
      admin_email: form.admin_email.trim(),
      contact_note: form.contact_note.trim() || null,
    });
    submitted.value = true;
  } catch (err) {
    error.value = err.message || "提交失败，请稍后重试";
    traceId.value = err.traceId || "";
  } finally {
    busy.value = false;
  }
}

function reset() {
  submitted.value = false;
  error.value = "";
  form.org_name = "";
  form.admin_display_name = "";
  form.admin_email = "";
  form.contact_note = "";
}
</script>

<style scoped>
.register-page { max-width: 640px; margin: 0 auto; padding: var(--nx-space-4); }
.register-form { display: grid; gap: var(--nx-space-3); }
.req { color: var(--nx-danger); }
.field-error { margin: 4px 0 0; color: var(--nx-danger); font-size: 13px; }
.tip { margin: 0; }
</style>