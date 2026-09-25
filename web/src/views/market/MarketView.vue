<template>
  <main class="market-page">
    <NxPageHeader eyebrow="P4 · 星市" title="应用目录" description="无支付。选择 SKU 后即时开通，成功后进入我的已开通。">
      <template #actions><button class="ghost" type="button" :disabled="loading" @click="load">刷新目录</button></template>
    </NxPageHeader>

    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <template v-else>
      <section class="catalog-toolbar" aria-label="目录筛选" data-testid="catalog-filter">
        <label class="search-field"><span>关键词</span><input v-model="search" type="search" placeholder="应用名称、编码、简介或 SKU" data-testid="catalog-search" /></label>
        <button class="ghost" type="button" data-testid="catalog-reset" @click="resetSearch">重置</button>
        <p class="result-count" aria-live="polite">{{ loading ? "正在加载目录…" : `共 ${visibleItems.length} 个可用版本` }}</p>
      </section>
      <section v-if="failure" class="subscribe-failure" role="alert" data-testid="subscribe-failure">
        <div class="failure-body">
          <strong>订阅开通失败<template v-if="failure.skuLabel">：{{ failure.skuLabel }}</template></strong>
          <p data-testid="subscribe-failure-hint">{{ failure.hint }}</p>
          <p v-if="failure.serverMessage" class="server-message" data-testid="subscribe-failure-server">服务端信息：{{ failure.serverMessage }}<template v-if="failure.code">（{{ failure.code }}）</template></p>
          <code v-if="failure.traceId" data-testid="subscribe-failure-trace">追踪号 {{ failure.traceId }}</code>
        </div>
        <div class="failure-actions">
          <router-link v-if="failure.action" class="ghost" :to="failure.action.to">{{ failure.action.label }}</router-link>
          <button v-if="failure.retryable && failure.skuCode" class="primary" type="button" data-testid="subscribe-retry" :disabled="Boolean(installing)" @click="subscribe(failure.skuCode, failure.versionId, failure.skuLabel)">
            {{ installing ? "开通中…" : "重试开通" }}
          </button>
          <button class="ghost" type="button" data-testid="subscribe-dismiss" @click="failure = null">关闭</button>
        </div>
      </section>
      <div v-if="loading" class="catalog-skeleton" role="status" data-testid="catalog-loading">
        <NxSkeleton v-for="n in 3" :key="n" variant="list-row" label="正在加载应用目录" />
      </div>
      <NxEmpty v-else-if="!visibleItems.length" :title="items.length ? '没有匹配的应用' : '暂无'" :description="items.length ? '请调整搜索词后重试。' : '应用审核并发布后，会出现在这里。'" />
      <section v-else class="catalog-grid" aria-label="已发布应用目录" data-testid="catalog-grid">
        <article v-for="item in visibleItems" :key="item.version.id" class="app-card">
          <header class="card-header">
            <div class="app-logo" role="img" aria-label="应用标识占位"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></svg></div>
            <div class="app-identity"><h2>{{ item.app.name || '暂无应用名称' }}</h2><span class="app-code">{{ item.app.code || '暂无编码' }}</span></div>
            <NxStatusTag status="PUBLISHED" label="已发布" tone="success" />
          </header>
          <p class="summary">{{ item.app.summary || "暂无简介" }}</p>
          <div class="version-row"><span>版本</span><strong>{{ item.version.version || '暂无' }}</strong></div>
          <div class="sku-section"><div class="sku-heading"><h3>可选 SKU</h3><span data-testid="catalog-sku-count">{{ catalogSkus(item).length }} 个</span></div>
            <NxEmpty v-if="!catalogSkus(item).length" title="暂无 SKU" description="当前版本没有可安装的 SKU。" compact />
            <ul v-else class="sku-list">
              <li v-for="sku in catalogSkus(item)" :key="sku.code" class="sku-item" data-testid="catalog-sku">
                <div class="sku-meta">
                  <div class="sku-title"><strong data-testid="catalog-sku-name">{{ sku.name || sku.code || "未命名 SKU" }}</strong><code v-if="sku.code">{{ sku.code }}</code></div>
                  <p v-if="sku.definition" class="sku-product" data-testid="catalog-sku-product">产品：{{ sku.definition.name || sku.definition.code }}</p>
                  <p v-else class="sku-warn" data-testid="catalog-sku-unbound">未绑定产品定义，暂不可开通</p>
                  <div v-if="sku.entitlements.length" class="sku-chips"><span v-for="chip in sku.entitlements" :key="chip">{{ chip }}</span></div>
                </div>
                <div v-if="subscribedSkus.has(sku.code)" class="subscribed" data-testid="catalog-subscribed">
                  <span class="subscribed-chip">已开通</span>
                  <router-link class="install-button" to="/p4/installations">前往我的已开通</router-link>
                </div>
                <button
                  v-else
                  class="install-button"
                  :class="{ ready: sku.code && sku.definition }"
                  type="button"
                  data-testid="catalog-subscribe"
                  :aria-label="`订阅开通 ${sku.name || sku.code}`"
                  :disabled="!sku.code || !sku.definition || installing === sku.code"
                  @click="subscribe(sku.code, item.version.id, sku.name || sku.code)"
                >{{ installing === sku.code ? "开通中…" : "订阅开通" }}</button>
              </li>
            </ul>
          </div>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxSkeleton, NxStatusTag } from "../../components";
import { p4 } from "../../api/client";
import { session } from "../../api/session";
import { catalogSkus } from "./catalogSku";
import { describeSubscribeError } from "./subscribeError";

const route = useRoute();
const router = useRouter();

const items = ref([]); const loading = ref(true); const error = ref(""); const installing = ref("");
const failure = ref(null);
// SKUs this tenant already subscribed (ACTIVE): show 已开通 instead of a subscribe that would 409.
const subscribedSkus = ref(new Set());

async function loadSubscriptions() {
  if (!session.contextId) { subscribedSkus.value = new Set(); return; }
  try {
    const rows = (await p4.subscriptions()) || [];
    subscribedSkus.value = new Set(rows
      .filter((row) => String(row?.status || "").toUpperCase() === "ACTIVE")
      .map((row) => row?.sku_code ?? row?.skuCode)
      .filter(Boolean));
  } catch {
    subscribedSkus.value = new Set(); // Unknown: keep offering subscribe; the 409 alert still explains duplicates.
  }
}
const search = ref(typeof route.query.q === "string" ? route.query.q : "");
const visibleItems = computed(() => {
  const term = search.value.trim().toLocaleLowerCase();
  if (!term) return items.value;
  return items.value.filter((item) => [item.app?.name, item.app?.code, item.app?.summary, item.version?.version, ...catalogSkus(item).flatMap((sku) => [sku.name, sku.code])]
    .some((value) => String(value || "").toLocaleLowerCase().includes(term)));
});

async function load() {
  loading.value = true; error.value = "";
  try { [items.value] = await Promise.all([p4.catalog().then((rows) => rows || []), loadSubscriptions()]); }
  catch (err) { error.value = err.message || "应用目录暂时不可用"; }
  finally { loading.value = false; }
}
async function subscribe(skuCode, versionId, skuLabel = "") {
  if (!session.tenantId) {
    failure.value = { hint: "请先登录并进入租户后再开通。", serverMessage: "", code: "", traceId: "", retryable: false, action: null, skuLabel };
    return;
  }
  installing.value = skuCode; failure.value = null;
  try {
    await p4.subscribe(session.tenantId, { sku_code: skuCode, version_id: versionId });
    await router.push("/p4/installations");
  } catch (err) {
    // A subscribe failure must not replace the catalog: keep it visible and offer a real retry.
    failure.value = { ...describeSubscribeError(err), skuCode, versionId, skuLabel };
  } finally { installing.value = ""; }
}
function resetSearch() {
  search.value = "";
}
watch(search, (value) => {
  const q = value.trim();
  const next = { ...route.query };
  if (q) next.q = q;
  else delete next.q;
  const current = typeof route.query.q === "string" ? route.query.q : "";
  if (current === q) return;
  router.replace({ query: next });
});
onMounted(load);
</script>

<style scoped>
.market-page { display: grid; gap: var(--nx-space-4); }
.catalog-skeleton { display: grid; gap: var(--nx-space-3); }
.catalog-toolbar { display: flex; align-items: flex-end; gap: 8px; padding: 0; background: transparent; border: 0; box-shadow: none; }
.search-field { display: grid; flex: 1; gap: var(--nx-space-1); max-width: 560px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.search-field input { width: 100%; min-height: 36px; padding: var(--nx-space-2) var(--nx-space-3); color: var(--nx-text-primary); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); border-radius: 6px; }
.result-count { margin: 0; color: var(--nx-text-secondary); white-space: nowrap; }
.catalog-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(min(100%,300px),1fr)); gap: var(--nx-space-4); align-items: stretch; }
.app-card { display: flex; min-width: 0; flex-direction: column; gap: var(--nx-space-4); padding: var(--nx-space-5); background: var(--nx-surface); border: 1px solid var(--nx-border); border-radius: 12px; box-shadow: var(--nx-shadow-card); }
.card-header { display: flex; align-items: center; gap: var(--nx-space-3); min-width: 0; }
.app-logo { display: grid; flex: 0 0 48px; width: 48px; height: 48px; place-items: center; color: var(--nx-product); background: var(--nx-primary-light); border: 1px solid var(--nx-border); border-radius: 12px; }
.app-logo svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.7; }
.app-identity { flex: 1; min-width: 0; }.app-identity h2 { margin: 0; overflow: hidden; color: var(--nx-text-primary); font-size: var(--nx-font-size-md); text-overflow: ellipsis; white-space: nowrap; }
.app-code { display: block; margin-top: var(--nx-space-1); color: var(--nx-text-secondary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: var(--nx-font-size-xs); }
.summary { min-height: 42px; margin: 0; color: var(--nx-text-secondary); line-height: 1.5; }
.version-row { display: flex; justify-content: space-between; padding: var(--nx-space-3) 0; color: var(--nx-text-secondary); border-top: 1px solid var(--nx-divider); border-bottom: 1px solid var(--nx-divider); font-size: var(--nx-font-size-sm); }.version-row strong { color: var(--nx-text-primary); }
.sku-section { display: grid; gap: var(--nx-space-3); margin-top: auto; }.sku-heading { display: flex; align-items: center; justify-content: space-between; }.sku-heading h3 { margin: 0; font-size: var(--nx-font-size-sm); }.sku-heading span { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.sku-list { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--nx-space-2); }
.sku-item { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-3); padding: var(--nx-space-3); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); background: var(--nx-bg-page); }
.sku-meta { display: grid; gap: 4px; min-width: 0; }
.sku-title { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--nx-space-2); }
.sku-title strong { color: var(--nx-text-primary); font-size: var(--nx-font-size-sm); }
.sku-title code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.sku-product, .sku-warn { margin: 0; font-size: var(--nx-font-size-xs); }
.sku-product { color: var(--nx-text-regular); }
.sku-warn { color: var(--nx-warning); }
.sku-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.sku-chips span { padding: 1px 8px; border-radius: var(--nx-radius-pill); font-size: var(--nx-font-size-xs); color: var(--nx-product); background: var(--nx-product-light); }
.sku-item { flex-wrap: wrap; }
.sku-item .sku-meta { flex: 1 1 200px; }
.sku-item .install-button { flex: none; display: inline-flex; align-items: center; text-decoration: none; }
/* The one primary action on a subscribable SKU (N1); disabled / unbound SKUs stay outlined. */
.install-button.ready:not(:disabled) { color: #fff; background: var(--nx-product); border-color: var(--nx-product); font-weight: 600; }
.install-button.ready:not(:disabled):hover { color: #fff; background: var(--nx-product); border-color: var(--nx-product); filter: brightness(0.93); }
.subscribed { display: flex; flex: none; align-items: center; gap: var(--nx-space-2); }
.subscribed-chip { padding: 2px 10px; border-radius: var(--nx-radius-pill); font-size: var(--nx-font-size-xs); font-weight: 600; color: var(--nx-success); background: var(--nx-success-light); }
.subscribe-failure { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); padding: var(--nx-space-3) var(--nx-space-4); border: 1px solid color-mix(in srgb, var(--nx-danger) 30%, white); border-left: 4px solid var(--nx-danger); border-radius: var(--nx-radius-sm); background: var(--nx-danger-light); }
.failure-body { display: grid; gap: 4px; min-width: 0; }
.failure-body strong { color: var(--nx-danger); }
.failure-body p { margin: 0; color: var(--nx-text-regular); font-size: var(--nx-font-size-sm); }
.failure-body .server-message { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); overflow-wrap: anywhere; }
.failure-body code { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.failure-actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
.failure-actions .primary { min-height: 36px; padding: 0 var(--nx-space-3); border-radius: 8px; }.install-button,.ghost { min-height: 36px; padding: 0 var(--nx-space-3); color: var(--nx-text-regular); background: var(--nx-surface); border: 1px solid var(--nx-border-strong); border-radius: 8px; cursor: pointer; }.install-button:hover,.ghost:hover { color: var(--nx-product); border-color: var(--nx-product); }.install-button:disabled,.ghost:disabled { opacity: .55; cursor: not-allowed; }
.loading { padding: var(--nx-space-6); color: var(--nx-text-secondary); }
@media(max-width:640px) { .catalog-toolbar { align-items: stretch; flex-direction: column; }.result-count { align-self: flex-start; } }
</style>
