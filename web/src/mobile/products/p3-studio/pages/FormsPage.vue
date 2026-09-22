<template>
  <div>
    <div class="m-card">
      <h3>已发布表单</h3>
      <p class="m-muted">H5 运行态填报。设计 / Validate / Publish 仍在桌面。</p>
      <MPermDecision :decision="decision" :cell="H5_SURFACES.formRead" />
    </div>
    <p v-if="!session.contextId" class="m-error">没有 TenantContext。Stub 会话可浏览本页；live 填报需要已签发上下文。</p>
    <p v-if="error" class="m-error" data-testid="m-forms-error">{{ error }}</p>
    <p v-if="!rows.length && !error" class="m-muted" data-testid="m-forms-empty">当前租户没有表单。</p>
    <router-link
      v-for="row in rows"
      :key="row.id"
      class="m-entity"
      :to="`/m/p3/forms/${row.id}`"
      :data-testid="`m-form-${row.id}`"
    >
      <strong>{{ row.name }}</strong>
      <div class="m-muted">{{ row.code || row.id }} · {{ row.ledger_name || "未绑定台账" }}</div>
      <div class="m-muted">Running v{{ row.running_version || 0 }} · {{ row.draft_status || "—" }}</div>
    </router-link>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p3 } from "../../../../api/p3";
import { session } from "../../../../api/session";
import MPermDecision from "../../../permission/MPermDecision.vue";
import { H5_SURFACES } from "../../../permission/surfaceCatalog";
import { useH5Surface } from "../../../permission/useH5Surface";

const rows = ref([]);
const { decision, error, requireCell, capture } = useH5Surface();

onMounted(async () => {
  try {
    await requireCell(H5_SURFACES.formRead);
    const body = await p3.listForms();
    rows.value = body.items || [];
  } catch (err) {
    rows.value = [];
    capture(err);
  }
});
</script>
