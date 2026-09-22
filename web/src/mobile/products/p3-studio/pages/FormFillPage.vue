<template>
  <div>
    <div v-if="form" class="m-card">
      <h3>{{ form.name }}</h3>
      <p class="m-muted">{{ form.code }} · Running v{{ form.running_version || 0 }}</p>
      <p class="m-muted">submit-start 写物理行并启动顺序审批。本页不提供设计器。</p>
      <MPermDecision :decision="decision" :cell="permCell" />
    </div>
    <p v-if="error" class="m-error" data-testid="m-form-fill-error">{{ error }}</p>
    <p v-if="notice" class="m-ok" data-testid="m-form-fill-ok">{{ notice }}</p>
    <form v-if="form" class="m-card" @submit.prevent="onSubmit">
      <p v-if="!runningFields.length" class="m-muted" data-testid="m-form-no-running">
        没有 running 快照。请先在桌面 Validate / Publish。
      </p>
      <div v-for="field in runningFields" :key="field.id || field.code" class="m-field">
        <label>{{ field.name || field.code }}</label>
        <textarea
          v-if="field.type === 'longtext' || field.type === 'textarea'"
          v-model="values[field.code]"
          rows="3"
          :required="field.required"
        />
        <input
          v-else
          v-model="values[field.code]"
          :type="inputTypeFor(field.type)"
          :required="field.required"
          :data-testid="`m-form-field-${field.code}`"
        />
      </div>
      <div class="m-field">
        <label>审批人</label>
        <select v-model="assigneeRef" data-testid="m-form-assignee">
          <option value="">当前登录人</option>
          <option v-for="person in persons" :key="person.id" :value="person.id">
            {{ person.display_name || person.displayName || person.email }}
          </option>
        </select>
      </div>
      <button class="m-btn primary" type="submit" :disabled="!runningFields.length || busy" data-testid="m-form-submit">
        {{ busy ? "提交中…" : "提交并启动审批" }}
      </button>
    </form>
    <p v-else-if="!error" class="m-muted">正在加载表单…</p>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { p2 } from "../../../../api/client";
import { p3 } from "../../../../api/p3";
import { session } from "../../../../api/session";
import { inputTypeFor } from "../../../runtime/display";
import MPermDecision from "../../../permission/MPermDecision.vue";
import { H5_SURFACES } from "../../../permission/surfaceCatalog";
import { useH5Surface } from "../../../permission/useH5Surface";

const route = useRoute();
const form = ref(null);
const values = reactive({});
const persons = ref([]);
const assigneeRef = ref("");
const notice = ref("");
const busy = ref(false);
const permCell = ref(H5_SURFACES.formRead);
const { decision, error, requireCell, capture } = useH5Surface();
const runningFields = computed(() => form.value?.running_fields || []);

onMounted(async () => {
  try {
    await requireCell(H5_SURFACES.formRead);
    permCell.value = H5_SURFACES.formRead;
    form.value = await p3.getForm(route.params.id);
    for (const field of form.value.running_fields || []) {
      if (values[field.code] === undefined) {
        values[field.code] = "";
      }
    }
    try {
      persons.value = (await p2.persons()) || [];
    } catch {
      persons.value = [];
    }
    if (!assigneeRef.value && session.personId) {
      assigneeRef.value = session.personId;
    }
  } catch (err) {
    form.value = null;
    capture(err);
  }
});

async function onSubmit() {
  notice.value = "";
  busy.value = true;
  try {
    permCell.value = H5_SURFACES.formWrite;
    await requireCell(H5_SURFACES.formWrite);
    const payload = {};
    for (const field of runningFields.value) {
      payload[field.code] = values[field.code];
    }
    const result = await p3.submitStart(route.params.id, {
      values: payload,
      process_key: "nexusSequentialApproval",
      assignee_type: "PERSON",
      assignee_ref: assigneeRef.value || session.personId,
    });
    notice.value = `已提交。record ${result.record_id || "?"} · todo ${result.todo?.id || "?"} · trace ${result.trace_id || ""}`;
  } catch (err) {
    capture(err);
  } finally {
    busy.value = false;
  }
}
</script>
