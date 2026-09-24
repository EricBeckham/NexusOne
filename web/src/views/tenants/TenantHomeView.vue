<template>
  <NxPageHeader eyebrow="星域" title="星域" description="Nexus Tenant" />
  <div class="nx-grid kpi">
    <NxStatCard label="租户数" :value="tenantCount" unit="个" :hint="tenantHint" />
    <NxStatCard label="平台租户" :value="platformName" :hint="platformHint" />
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { NxPageHeader, NxStatCard } from "../../components";
import { realCount, recordName } from "../homeStats";

const tenantCount = ref(null);
const tenantHint = ref("");
const platformName = ref(null);
const platformHint = ref("");

onMounted(async () => {
  const [tenants, platform] = await Promise.allSettled([p1.listTenants(), p1.platformTenant()]);
  if (tenants.status === "fulfilled") {
    tenantCount.value = realCount(tenants.value);
    if (tenantCount.value === null) {
      tenantHint.value = "接口没有返回列表";
    }
  } else {
    tenantHint.value = "接口不可用";
  }
  if (platform.status === "fulfilled") {
    platformName.value = recordName(platform.value);
    if (platformName.value === null) {
      platformHint.value = "接口没有返回名称";
    }
  } else {
    platformHint.value = "接口不可用";
  }
});
</script>
