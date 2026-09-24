<template>
  <NxPageHeader eyebrow="星权" title="星权" description="Nexus IAM" />
  <div class="nx-grid kpi">
    <NxStatCard label="用户" :value="personCount" unit="人" :hint="personHint" />
    <NxStatCard label="部门" :value="departmentCount" unit="个" :hint="departmentHint" />
    <NxStatCard label="岗位" :value="positionCount" unit="个" :hint="positionHint" />
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p2 } from "../../api/client";
import { session } from "../../api/session";
import { NxPageHeader, NxStatCard } from "../../components";
import { countTree, realCount } from "../homeStats";

const personCount = ref(null);
const personHint = ref("");
const departmentCount = ref(null);
const departmentHint = ref("");
const positionCount = ref(null);
const positionHint = ref("");

onMounted(async () => {
  if (!session.contextId) {
    const hint = "需要租户上下文";
    personHint.value = hint;
    departmentHint.value = hint;
    positionHint.value = hint;
    return;
  }
  const [persons, departments, positions] = await Promise.allSettled([
    p2.persons(),
    p2.departments(),
    p2.positions(),
  ]);
  if (persons.status === "fulfilled") {
    personCount.value = realCount(persons.value);
    if (personCount.value === null) {
      personHint.value = "接口没有返回列表";
    }
  } else {
    personHint.value = "接口不可用";
  }
  if (departments.status === "fulfilled") {
    departmentCount.value = countTree(departments.value);
    if (departmentCount.value === null) {
      departmentHint.value = "接口没有返回列表";
    }
  } else {
    departmentHint.value = "接口不可用";
  }
  if (positions.status === "fulfilled") {
    positionCount.value = realCount(positions.value);
    if (positionCount.value === null) {
      positionHint.value = "接口没有返回列表";
    }
  } else {
    positionHint.value = "接口不可用";
  }
});
</script>
