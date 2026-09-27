<template>
  <main class="demo-deploy-page" data-testid="demo-deploy-status">
    <NxPageHeader
      eyebrow="星域 / 演示部署"
      title="演示部署状态"
      description="只读。只显示健康探活与配置是否存在，不展示任何配置值。"
      :crumbs="[{ label: '星域' }, { label: '演示部署状态' }]"
    >
      <template #actions>
        <router-link class="ghost" to="/p1/demo-clickpath" data-testid="demo-deploy-clickpath">演示点击路径</router-link>
        <button class="ghost" type="button" :disabled="loading" data-testid="demo-deploy-refresh" @click="load">
          {{ loading ? "探测中…" : "重新探测" }}
        </button>
      </template>
    </NxPageHeader>

    <section class="presence" data-testid="demo-deploy-presence" aria-label="配置是否存在">
      <p>
        <span>Keycloak issuer</span>
        <strong data-testid="issuer-presence">{{ issuerConfigured ? "已配置" : "未配置" }}</strong>
      </p>
      <p>
        <span>前端生产构建</span>
        <strong data-testid="fe-prod-presence">{{ frontendProd ? "是" : "否" }}</strong>
      </p>
      <p>
        <span>服务端 prod profile</span>
        <strong data-testid="server-prod-presence">未知</strong>
      </p>
    </section>

    <section aria-label="健康探活">
      <NxErrorState
        v-if="healthError"
        title="健康探活失败"
        :description="healthError"
        retry-label="重试"
        @retry="load"
      />
      <NxEmpty
        v-else-if="!loading && !healthStatus"
        title="暂无健康数据"
        description="探活没有返回可展示的状态。"
      />
      <p v-else-if="healthStatus" data-testid="demo-deploy-health">健康状态：{{ healthStatus }}</p>
    </section>

    <section aria-label="验收清单对照">
      <ul data-testid="demo-deploy-checklist">
        <li v-for="item in checklist" :key="item.id">
          <span>{{ item.label }}</span>
          <strong>{{ item.scope }}</strong>
        </li>
      </ul>
      <p class="note">待外部项需环境所有者完成。本页不代表 Keycloak 真机已验收。</p>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { p1 } from "../../api/client";
import { keycloakPublicConfig } from "../../auth/keycloakPublic";
import { NxEmpty, NxErrorState, NxPageHeader } from "../../components";

const HEALTH_STATUSES = new Set(["UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"]);

const DEMO_DEPLOY_CHECKLIST = [
  { id: "issuer", label: "公开 issuer 是否已配置", scope: "可本地" },
  { id: "fe-build", label: "前端是否为生产构建", scope: "可本地" },
  { id: "health", label: "公共健康探活", scope: "可本地" },
  { id: "host", label: "演示机主机、DNS 与证书", scope: "待外部" },
  { id: "keystore", label: "密钥管理器注入", scope: "待外部" },
  { id: "realm", label: "Keycloak realm 与受控账号", scope: "待外部" },
  { id: "server-prod", label: "服务端 prod profile", scope: "待外部" },
];

const checklist = DEMO_DEPLOY_CHECKLIST;
const issuerConfigured = keycloakPublicConfig(import.meta.env).configured;
const frontendProd = Boolean(import.meta.env.PROD);
const loading = ref(true);
const healthStatus = ref("");
const healthError = ref("");

function safeHealthStatus(body) {
  const status = String(body?.status || "").trim().toUpperCase();
  return HEALTH_STATUSES.has(status) ? status : "";
}

async function load() {
  loading.value = true;
  healthError.value = "";
  healthStatus.value = "";
  try {
    const body = await p1.actuatorHealth();
    healthStatus.value = safeHealthStatus(body);
  } catch (error) {
    healthError.value = error?.status ? `HTTP ${error.status}` : "探活不可用";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.presence,
.note,
ul {
  margin: 0 0 16px;
}
.presence p,
li {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
}
.note {
  color: var(--nx-text-secondary);
}
</style>
