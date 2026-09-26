<template>
  <main class="clients-page">
    <NxPageHeader
      eyebrow="P5 · NEXUS CONNECT"
      title="API Client"
      description="自用服务身份。密钥只在签发成功时出现一次，列表和详情不显示。"
    >
      <template v-if="isDetail" #actions>
        <router-link class="ghost" to="/p5/clients">返回列表</router-link>
      </template>
    </NxPageHeader>

    <NxErrorState v-if="error" :title="error" :trace-id="traceId" retry-label="重试" @retry="load" />

    <template v-else-if="isDetail">
      <NxEmpty v-if="loaded && !client" title="暂无" description="接口没有返回这个 API Client。" />
      <section v-else-if="client" class="nx-card detail">
        <h2>{{ show(client.name) }}</h2>
        <dl>
          <div><dt>标识</dt><dd>{{ show(client.id) }}</dd></div>
          <div><dt>密钥前缀</dt><dd>{{ show(client.keyPrefix) }}</dd></div>
          <div><dt>Scope</dt><dd>{{ client.scopes.length ? client.scopes.join("、") : "暂无" }}</dd></div>
          <div><dt>每分钟限流</dt><dd>{{ show(client.rateLimit) }}</dd></div>
          <div><dt>状态</dt><dd><NxStatusTag v-if="client.active != null" :status="client.active ? 'ACTIVE' : 'DISABLED'" :label="client.active ? '启用' : '停用'" /><template v-else>暂无</template></dd></div>
          <div><dt>创建时间</dt><dd>{{ show(client.createdAt) }}</dd></div>
        </dl>
      </section>
    </template>

    <template v-else>
      <NxStatCard label="API Client" :value="count" unit="个" />
      <NxEmpty v-if="rows && !rows.length" title="暂无 API Client" description="签发一个服务身份后，Client 会显示在这里；密钥只会在签发时展示一次。">
        <template #next><a class="ghost" href="#issue-client">去签发</a></template>
      </NxEmpty>
      <div v-else-if="rows?.length" class="table-scroll"><table class="nx-table nx-table--compact" data-testid="clients-table">
        <thead>
          <tr><th scope="col">名称</th><th scope="col">密钥前缀</th><th scope="col">Scope</th><th scope="col">每分钟限流</th><th scope="col">状态</th><th scope="col">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in listed" :key="row.id || row.name">
            <td>{{ show(row.name) }}</td>
            <td>{{ show(row.keyPrefix) }}</td>
            <td>{{ row.scopes.length ? row.scopes.join("、") : "暂无" }}</td>
            <td>{{ show(row.rateLimit) }}</td>
            <td><NxStatusTag v-if="row.active != null" :status="row.active ? 'ACTIVE' : 'DISABLED'" :label="row.active ? '启用' : '停用'" /><template v-else>暂无</template></td>
            <td><router-link v-if="row.id" :to="`/p5/clients/${row.id}`">查看</router-link><template v-else>暂无</template></td>
          </tr>
        </tbody>
      </table></div>
      <div v-else-if="!loaded" class="loading-list"><NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载 API Client" compact /></div>

      <section id="issue-client" class="nx-card issue">
        <h2>签发</h2>
        <div class="nx-field"><label>名称</label><input v-model="name" /></div>
        <div class="nx-field"><label>Scope（逗号分隔）</label><input v-model="scopes" /></div>
        <div class="nx-field"><label>每分钟限流</label><input v-model="rate" inputmode="numeric" /></div>
        <p v-if="formError" class="nx-error">{{ formError }}</p>
        <button class="primary" type="button" :disabled="saving" @click="issue">签发一次性密钥</button>
        <div v-if="issuedSecret" class="secret">
          <b>请立即保存，之后不再显示</b>
          <code>{{ issuedSecret }}</code>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatCard, NxStatusTag } from "../../components";
import { p5 } from "../../api/client";
import { asRows, clientFields, show } from "./connectDisplay";

const route = useRoute();
const rows = ref(null);
const detail = ref(null);
const loaded = ref(false);
const error = ref("");
const traceId = ref("");
const issuedSecret = ref("");
const name = ref("");
const scopes = ref("");
const rate = ref("");
const saving = ref(false);
const formError = ref("");

const isDetail = computed(() => route.name === "p5-client");
const count = computed(() => rows.value?.length ?? null);
const listed = computed(() => (rows.value || []).map(clientFields).filter(Boolean));
const client = computed(() => clientFields(detail.value));

async function load() {
  error.value = "";
  traceId.value = "";
  formError.value = "";
  issuedSecret.value = "";
  loaded.value = false;
  if (isDetail.value) {
    rows.value = null;
    try {
      detail.value = await p5.client(route.params.id);
    } catch (err) {
      detail.value = null;
      error.value = err.message || "加载失败";
      traceId.value = err.traceId || "";
    } finally {
      loaded.value = true;
    }
    return;
  }
  detail.value = null;
  try {
    const body = await p5.clients();
    rows.value = asRows(body);
    if (!rows.value) {
      error.value = "接口没有返回 API Client 列表";
    }
  } catch (err) {
    rows.value = null;
    error.value = err.message || "加载失败";
    traceId.value = err.traceId || "";
  } finally {
    loaded.value = true;
  }
}

async function issue() {
  formError.value = "";
  if (!name.value.trim()) {
    formError.value = "请填写名称";
    return;
  }
  const body = {
    name: name.value.trim(),
    scopes: scopes.value.split(",").map((item) => item.trim()).filter(Boolean),
  };
  if (rate.value !== "") {
    const parsed = Number(rate.value);
    if (!Number.isFinite(parsed)) {
      formError.value = "限流必须是数字";
      return;
    }
    body.rateLimitPerMinute = parsed;
  }
  saving.value = true;
  formError.value = "";
  try {
    const issued = await p5.issueClient(body);
    issuedSecret.value = issued?.secret || "";
    if (!issuedSecret.value) {
      formError.value = "接口没有返回一次性密钥";
    }
    name.value = "";
    scopes.value = "";
    rate.value = "";
  } catch (err) {
    issuedSecret.value = "";
    formError.value = err.message || "签发失败";
    return;
  } finally {
    saving.value = false;
  }
  try {
    const list = await p5.clients();
    rows.value = asRows(list);
    if (!rows.value) {
      formError.value = "接口没有返回 API Client 列表";
    }
  } catch (err) {
    formError.value = err.message || "列表刷新失败";
  }
}

watch(() => route.fullPath, load);
onMounted(load);
</script>

<style scoped>
.clients-page { display: grid; gap: var(--nx-space-4); }
.clients-page :deep(.page-header) { margin-bottom: 0; }
.table-scroll { overflow-x: auto; }
.table-scroll .nx-table { min-width: 700px; }
.loading-list { display: grid; gap: var(--nx-space-2); }
.detail, .issue { margin-top: 0; }
.detail h2, .issue h2 { margin-top: 0; font-size: 16px; }
dl { display: grid; gap: var(--nx-space-3); margin: 0; }
dl div { display: grid; grid-template-columns: 120px 1fr; gap: var(--nx-space-3); }
dt { color: var(--nx-text-secondary); }
dd { margin: 0; overflow-wrap: anywhere; }
.secret { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; padding: 12px; border-radius: 8px; background: var(--nx-primary-light); }
.secret code { overflow-wrap: anywhere; }
</style>
