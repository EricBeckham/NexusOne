<template>
  <main class="p4-management">
    <NxPageHeader eyebrow="P4 · 星市" :title="title" :description="description" :crumbs="crumbs">
      <template #actions><router-link class="ghost" to="/p4/market">应用目录</router-link><button class="ghost" type="button" :disabled="loading" @click="load">刷新</button></template>
    </NxPageHeader>

    <nav class="p4-tabs" aria-label="星市管理分区">
      <router-link v-for="tab in tabs" :key="tab.to" :to="tab.to" :aria-current="route.path === tab.to ? 'page' : undefined">{{ tab.label }}</router-link>
    </nav>

    <template v-if="section === 'packages'">
      <div class="split-layout">
        <section class="nx-card">
          <h2>应用包目录</h2>
          <NxDataTable
            :loading="loading"
            :error="tableError"
            :empty="!loading && !error && !apps.length"
            empty-title="暂无应用包"
            empty-description="平台创建并发布应用后，会在此显示真实应用包与版本。"
            density="compact"
            loading-label="正在读取应用包"
            aria-label="应用包目录"
            testid="packages-table"
            @retry="load"
          >
            <template #header>
              <tr><th>应用包</th><th></th></tr>
            </template>
            <tr
              v-for="app in apps"
              :key="app.id"
              class="selectable-row"
              :class="{ selected: selectedApp?.id === app.id }"
              data-testid="package-row"
              @click="selectApp(app)"
            >
              <td><strong>{{ app.name }}</strong><div class="nx-muted">{{ app.code }}</div></td>
              <td class="row-action">查看版本 →</td>
            </tr>
          </NxDataTable>
        </section>
        <section class="nx-card detail-card">
          <template v-if="selectedApp">
            <div class="section-heading"><div><span class="nx-muted">应用包 · {{ selectedApp.code }}</span><h2>{{ selectedApp.name }}</h2></div></div>
            <p>{{ selectedApp.summary || "暂无简介" }}</p>
            <dl class="facts"><dt>创建时间</dt><dd>{{ formatDate(selectedApp.createdAt) }}</dd></dl>
            <div data-testid="package-declarations">
              <NxEmpty
                compact
                title="包内容与声明暂未开放"
                description="当前不提供包内文件与声明明细，因此这里不展示占位内容，也不提供不可用操作。"
              />
            </div>
            <h3>版本与发布记录</h3>
            <NxDataTable
              :loading="loading"
              :empty="!loading && !versions.length"
              empty-title="暂无版本"
              empty-description="此应用尚无版本记录。"
              empty-compact
              density="compact"
              loading-label="正在读取版本"
              aria-label="版本与发布记录"
              testid="package-versions-table"
            >
              <template #header>
                <tr><th>版本</th><th>说明</th><th>状态</th></tr>
              </template>
              <tr v-for="version in versions" :key="version.id">
                <td><strong>{{ version.version }}</strong><div class="nx-muted">{{ version.id }}</div></td>
                <td>{{ version.releaseNotes || "暂无发布说明" }}</td>
                <td><NxStatusTag :status="version.status" :label="version.status" /></td>
              </tr>
            </NxDataTable>
          </template>
          <NxEmpty v-else title="选择一个应用包" description="应用包详情和版本记录将显示在这里。" />
        </section>
      </div>
    </template>

    <template v-else-if="section === 'skus'">
      <section class="nx-card">
        <div class="section-heading"><div><h2>商品与 SKU</h2><p class="nx-muted">每个应用对应一个商品；SKU 定义可开通的权益与配额。</p></div><span class="nx-muted">{{ apps.length }} 个商品 · {{ skus.length }} 个 SKU</span></div>
        <NxDataTable
          v-if="loading || error || !apps.length"
          :loading="loading"
          :error="tableError"
          :empty="!loading && !error && !apps.length"
          empty-title="暂无商品"
          empty-description="平台发布应用后，商品会显示在这里。"
          density="compact"
          loading-label="正在读取商品与 SKU"
          aria-label="商品与 SKU"
          testid="skus-catalog-table"
          @retry="load"
        />
        <template v-else>
          <article v-for="app in apps" :key="app.id" class="product-block">
            <div class="product-heading"><div><span class="eyebrow">商品 · {{ app.code }}</span><h3>{{ app.name }}</h3><p>{{ app.summary || "暂无简介" }}</p></div><code v-if="devNotes">{{ app.id }}</code></div>
            <NxDataTable
              :empty="!skusFor(app).length"
              empty-title="暂无 SKU"
              empty-description="该商品还没有 SKU。"
              empty-compact
              density="compact"
              :aria-label="`${app.name} SKU`"
              :testid="`sku-table-${app.id}`"
            >
              <template #header>
                <tr><th>SKU</th><th>权益</th><th>配额 / 属性</th></tr>
              </template>
              <tr v-for="sku in skusFor(app)" :key="sku.code">
                <td><strong>{{ sku.name || sku.code }}</strong><div class="nx-muted">{{ sku.code }}</div></td>
                <td>
                  <span v-if="!Object.keys(sku.entitlements || {}).length" class="nx-muted">暂无权益定义</span>
                  <ul v-else class="entitlements">
                    <li v-for="(entitlement, name) in sku.entitlements" :key="name"><code>{{ name }}</code><span>{{ entitlement.enabled ? "启用" : "停用" }}</span></li>
                  </ul>
                </td>
                <td>
                  <span v-if="!Object.keys(sku.entitlements || {}).length" class="nx-muted">暂无</span>
                  <ul v-else class="entitlements">
                    <li v-for="(entitlement, name) in sku.entitlements" :key="name"><span>{{ name }}</span><span>{{ entitlement.quota ?? "未设置" }} · {{ formatAttributes(entitlement.attributes) }}</span></li>
                  </ul>
                </td>
              </tr>
            </NxDataTable>
          </article>
        </template>
      </section>
    </template>

    <template v-else-if="section === 'subscriptions'">
      <NxNoPermission v-if="!session.contextId" title="需要先进入租户" description="订阅按租户查看，请先登录并切换到对应租户。" />
      <section v-else class="nx-card">
        <div class="section-heading"><div><h2>开通记录</h2><p class="nx-muted">当前租户已开通记录及其安装状态。无支付。</p></div><span class="nx-muted">{{ subscriptions.length }} 条记录</span></div>
        <NxDataTable
          :loading="loading"
          :error="tableError"
          :empty="!loading && !error && !subscriptions.length"
          empty-title="还没有开通记录"
          empty-description="在应用目录选择 SKU 开通后，记录会显示在这里。"
          density="compact"
          loading-label="正在读取开通记录"
          aria-label="开通记录"
          testid="subscriptions-table"
          @retry="load"
        >
          <template #empty-next>
            <router-link class="primary" to="/p4/market">前往应用目录</router-link>
          </template>
          <template #header>
            <tr><th>租户</th><th>商品</th><th>SKU</th><th>状态</th><th>开始</th><th>到期</th><th>权益状态</th><th>安装状态</th></tr>
          </template>
          <tr v-for="sub in subscriptions" :key="sub.id">
            <td><code>{{ sub.tenantId }}</code></td>
            <td>{{ appForSku(sub.skuCode)?.name || appForSku(sub.skuCode)?.code || "暂无" }}</td>
            <td>{{ skuByCode(sub.skuCode)?.name || sub.skuCode }}</td>
            <td><NxStatusTag :status="sub.status" :label="sub.status" /></td>
            <td>{{ formatDate(sub.startedAt) }}</td>
            <td>{{ formatDate(sub.endedAt) }}</td>
            <td data-testid="subscription-entitlement">
              <template v-if="entitlementFor(sub)">
                <span class="entitlement-count">{{ entitlementFor(sub).total ? `${entitlementFor(sub).enabled}/${entitlementFor(sub).total} 项启用` : "无权益项" }}</span>
                <NxStatus v-if="entitlementFor(sub).projected === true" label="当前生效" tone="success" />
                <NxStatus v-else-if="entitlementFor(sub).projected === false" label="未生效" tone="neutral" title="租户当前生效的是另一条订阅或默认权益包" />
              </template>
              <span v-else class="nx-muted">暂无</span>
            </td>
            <td>{{ installationStatus(sub) }}</td>
          </tr>
        </NxDataTable>
      </section>
    </template>

    <template v-else>
      <NxNoPermission v-if="!session.contextId" title="需要先进入租户" description="已开通产品按租户查看，请先登录并切换到对应租户。" />
      <section v-else class="installations">
        <p v-if="!loading && installations.length" class="installations-count nx-muted" data-testid="installations-count">共 {{ installations.length }} 个已开通产品</p>
        <NxDataTable
          v-if="loading || error || !installations.length"
          :loading="loading"
          :error="tableError"
          :empty="!loading && !error && !installations.length"
          empty-title="还没有已开通可用的产品"
          empty-description="从应用目录开通后，产品会显示在这里，并可直接发起申请。"
          loading-label="正在读取已开通产品"
          aria-label="已开通产品"
          testid="installations-table"
          card
          @retry="load"
        >
          <template #empty-next>
            <router-link class="primary" to="/p4/market">前往应用目录</router-link>
          </template>
        </NxDataTable>
        <div v-else class="installation-list"><article v-for="installation in installations" :key="installation.id" class="installation-card">
          <div class="installation-summary">
            <div class="installation-identity">
              <div class="installation-title">
                <h3>{{ appById(installation.appId)?.name || "未命名应用" }}</h3>
                <NxStatusTag :status="installation.status" :label="installStatusDisplay(installation.status).label" :tone="installStatusDisplay(installation.status).tone" data-testid="installation-status" />
              </div>
              <p class="installation-meta">
                <code>{{ appById(installation.appId)?.code || "应用" }}</code><span aria-hidden="true">·</span>当前版本 <strong>{{ versionLabel(installation.versionId) }}</strong><span aria-hidden="true">·</span>更新于 {{ formatDate(installation.updatedAt) }}
              </p>
            </div>
            <router-link v-if="installation.status === 'INSTALLED'" class="nx-btn nx-btn--primary" :to="`/p4/installations/${installation.id}/start`" data-testid="start-application">发起申请</router-link>
            <router-link v-if="installation.status === 'INSTALLED'" class="ghost" :to="`/p4/installations/${installation.id}/process-configuration`">个性化流程</router-link>
          </div>
          <details class="upgrade" data-testid="installation-upgrade" @toggle="onUpgradeToggle($event, installation)">
            <summary>版本升级</summary>
            <div class="stepper" aria-label="升级步骤" data-testid="upgrade-stepper">
              <span
                v-for="step in stepsFor(installation)"
                :key="step.id"
                class="step"
                :data-status="step.status"
                :data-testid="`upgrade-step-${step.id}`"
              >
                <span class="step-label">{{ step.label }}</span>
                <span class="step-status">{{ stepStatusLabel(step.status) }}</span>
              </span>
            </div>
            <!-- XB8: server step state and facts; UNKNOWN / 暂无 where nothing records or declares them. -->
            <p v-if="upgradeInfo[installation.id]?.note" class="upgrade-note" data-testid="upgrade-steps-note">{{ upgradeInfo[installation.id].note }}</p>
            <p v-if="upgradeInfo[installation.id]?.error" class="upgrade-note error" data-testid="upgrade-info-error">
              升级信息暂时无法读取，步骤状态按「暂无」显示。
              <button type="button" class="nx-btn nx-btn--text" @click="loadUpgradeInfo(installation, true)">重试</button>
            </p>
            <dl v-if="upgradeInfo[installation.id]?.facts?.length" class="upgrade-facts" data-testid="upgrade-facts">
              <div v-for="fact in upgradeInfo[installation.id].facts" :key="fact.key" class="upgrade-fact" :data-testid="`upgrade-fact-${fact.key}`">
                <dt>{{ fact.label }}</dt>
                <dd><strong :class="{ none: fact.value == null }">{{ fact.value ?? "暂无" }}</strong><small>{{ fact.note }}</small></dd>
              </div>
            </dl>
            <form class="upgrade-form" @submit.prevent="upgrade(installation)"><label>目标版本<select v-model="targets[installation.id]" required><option value="">请选择已发布版本</option><option v-for="version in publishedVersions(installation.appId)" :key="version.id" :value="version.id">{{ version.version }}</option></select></label><button class="nx-btn nx-btn--default" type="submit" data-testid="upgrade-submit" :disabled="busyId === installation.id || !targets[installation.id]">{{ busyId === installation.id ? "提交中…" : "提交升级" }}</button></form>
          </details>
          <small class="nx-muted installation-id" data-testid="installation-id">安装编号 <code :title="installation.id">{{ installation.id }}</code></small>
        </article></div>
      </section>
    </template>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxDataTable, NxEmpty, NxNoPermission, NxPageHeader, NxStatusTag } from "../../components";
import NxStatus from "../../components/NxStatus.vue";
import { p4 } from "../../api/client";
import { session } from "../../api/session";
import { devNotesEnabled } from "../../utils/devNotes";

const route = useRoute();
const apps = ref([]); const skus = ref([]); const subscriptions = ref([]); const installations = ref([]);
const versionsByApp = reactive({}); const selectedApp = ref(null); const versions = ref([]);
const targets = reactive({}); const loading = ref(true); const error = ref(""); const notice = ref(""); const busyId = ref("");
const section = computed(() => ({ "p4-packages": "packages", "p4-skus": "skus", "p4-subscriptions": "subscriptions", "p4-installations": "installations" })[route.name] || "packages");
const tabs = [{ label: "应用包", to: "/p4/packages" }, { label: "商品与 SKU", to: "/p4/skus" }, { label: "订阅", to: "/p4/subscriptions" }, { label: "我的已开通", to: "/p4/installations" }];
const titles = { packages: ["应用包", "浏览应用包、版本与发布状态。"], skus: ["SKU", "SKU 定义可开通的权益与配额，无价格字段。"], subscriptions: ["开通记录", "查看当前租户已开通的周期、状态与关联安装。无支付。"], installations: ["已开通可用", "无支付、即时开通后的产品记录。可从此发起申请。"] };
const title = computed(() => titles[section.value][0]); const description = computed(() => titles[section.value][1]);
const tableError = computed(() => (error.value ? { title: "星市数据暂时无法加载", description: error.value } : ""));
// O11: one Primary per card — 发起申请 is the card's action; upgrade stays secondary inside its fold.
// Q8 / Q5: 我的已开通 sits on the tenant path 应用目录 → 我的已开通 → 发起申请 (same crumbs as the start page).
const crumbs = computed(() => (section.value === "installations"
  ? [{ label: "应用目录", to: "/p4/market" }, { label: "我的已开通" }]
  : []));
const devNotes = devNotesEnabled();
/** XB4: honest shell — no BE step state yet, so every step is UNKNOWN (never fake-green DONE). */
const UPGRADE_STEP_DEFS = [
  { id: "precheck", label: "预检" },
  { id: "deps", label: "依赖检查" },
  { id: "migrate", label: "结构扩展与迁移" },
  { id: "config", label: "配置" },
  { id: "switch", label: "切换" },
  { id: "verify", label: "验证" },
];
const upgradeSteps = UPGRADE_STEP_DEFS.map((step) => ({ ...step, status: "UNKNOWN" }));

// XB8: per-installation server state, loaded when the 版本升级 fold opens. Until (or unless) it arrives,
// the XB4 defaults stay UNKNOWN — a failed read never turns anything green.
const STEP_STATUSES = new Set(["DONE", "ACTIVE", "FAILED", "UNKNOWN"]);
const upgradeInfo = reactive({});

function stepsFor(installation) {
  const steps = upgradeInfo[installation.id]?.steps;
  return steps?.length ? steps : upgradeSteps;
}

async function loadUpgradeInfo(installation, force = false) {
  const current = upgradeInfo[installation.id];
  if (current && (current.loading || (!force && !current.error))) return;
  upgradeInfo[installation.id] = { loading: true, steps: null, note: "", facts: [], error: false };
  try {
    const [steps, facts] = await Promise.all([p4.upgradeSteps(installation.id), p4.upgradeFacts(installation.id)]);
    upgradeInfo[installation.id] = {
      loading: false,
      error: false,
      steps: (steps?.steps || []).map((step) => ({
        id: step.key,
        label: step.label,
        status: STEP_STATUSES.has(step.status) ? step.status : "UNKNOWN",
      })),
      note: steps?.recorded === false ? steps.note || "" : "",
      facts: Array.isArray(facts?.facts) ? facts.facts : [],
    };
  } catch {
    upgradeInfo[installation.id] = { loading: false, steps: null, note: "", facts: [], error: true };
  }
}

function onUpgradeToggle(event, installation) {
  if (event.target.open) loadUpgradeInfo(installation);
}

function stepStatusLabel(status) {
  if (status === "DONE") return "完成";
  if (status === "ACTIVE") return "进行中";
  if (status === "FAILED") return "失败";
  return "暂无";
}

// The product's JSON is SNAKE_CASE (app_id, version_id, sku_code, updated_at…); this page reads camelCase.
const camelKey = (key) => key.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
function camelRow(row) {
  if (!row || typeof row !== "object") return row;
  const out = { ...row };
  for (const [key, value] of Object.entries(row)) if (key.includes("_") && !(camelKey(key) in out)) out[camelKey(key)] = value;
  return out;
}
const camelRows = (rows) => (Array.isArray(rows) ? rows : []).map(camelRow);

// XB11: per-subscription entitlement summary from the M20 read model (XB10). Best effort — a failure leaves
// the column at 暂无 and never blocks the subscription list.
const entitlementSummaries = reactive({});
async function loadEntitlementSummaries() {
  for (const key of Object.keys(entitlementSummaries)) delete entitlementSummaries[key];
  try {
    for (const row of (await p4.commercialEntitlements()) || []) {
      const id = row.subscription_id ?? row.subscriptionId;
      const summary = row.summary;
      if (!id || !summary) continue;
      entitlementSummaries[id] = {
        enabled: summary.enabled_count ?? summary.enabledCount ?? 0,
        total: summary.total_count ?? summary.totalCount ?? 0,
        projected: summary.projected ?? null,
      };
    }
  } catch {
    // leave the column at 暂无
  }
}
const entitlementFor = (sub) => entitlementSummaries[sub.id] || null;

async function load() {
  loading.value = true; error.value = ""; notice.value = "";
  try {
    const [appRows, skuRows] = await Promise.all([p4.apps(), p4.skus()]);
    apps.value = camelRows(appRows); skus.value = camelRows(skuRows);
    await Promise.all(apps.value.map(async (app) => { versionsByApp[app.id] = camelRows(await p4.versions(app.id)); }));
    if (section.value === "packages" && apps.value.length && !selectedApp.value) await selectApp(apps.value[0]);
    if (session.contextId && ["subscriptions", "installations"].includes(section.value)) {
      const [subs, installs] = await Promise.all([p4.subscriptions(), p4.installations()]);
      subscriptions.value = camelRows(subs); installations.value = camelRows(installs);
      if (section.value === "subscriptions") await loadEntitlementSummaries();
    }
  } catch (err) { error.value = err.message || "星市数据暂时不可用"; }
  finally { loading.value = false; }
}
async function selectApp(app) { selectedApp.value = app; versions.value = versionsByApp[app.id] || camelRows(await p4.versions(app.id)); }
const skusFor = (app) => skus.value.filter((sku) => sku.appId === app.id);
const skuByCode = (code) => skus.value.find((sku) => sku.code === code);
const appForSku = (code) => { const sku = skuByCode(code); return sku ? appById(sku.appId) : null; };
const appById = (id) => apps.value.find((app) => app.id === id);
const publishedVersions = (appId) => (versionsByApp[appId] || []).filter((version) => version.status === "PUBLISHED");
const versionLabel = (id) => Object.values(versionsByApp).flat().find((version) => version.id === id)?.version || id || "暂无";
function installationStatus(sub) { const row = installations.value.find((item) => item.tenantId === sub.tenantId && item.appId === appForSku(sub.skuCode)?.id && item.status === "INSTALLED"); return row ? row.status : "暂无安装"; }
const INSTALL_STATUS = { INSTALLED: ["已开通", "success"], UPGRADING: ["升级中", "info"], FAILED: ["开通失败", "danger"], UNINSTALLED: ["已停用", "neutral"] };
function installStatusDisplay(status) { const [label, tone] = INSTALL_STATUS[String(status || "").toUpperCase()] || [status || "未知", "neutral"]; return { label, tone }; }
function formatDate(value) { return value ? new Date(value).toLocaleString("zh-CN") : "暂无"; }
function formatAttributes(value) { return value && Object.keys(value).length ? JSON.stringify(value) : "暂无属性"; }
async function upgrade(installation) {
  const versionId = targets[installation.id]; if (!versionId) return;
  busyId.value = installation.id; error.value = ""; notice.value = "";
  try { await p4.upgrade(installation.id, versionId); targets[installation.id] = ""; await load(); notice.value = "升级请求已完成。请刷新确认服务端安装状态。"; }
  catch (err) { error.value = err.message || "升级失败"; }
  finally { busyId.value = ""; }
}
watch(() => route.name, load);
onMounted(load);
</script>

<style scoped>
/* Q8: installed products are a flat list of cards (no card-in-card); the status tag sits with the name, the one Primary on the right. */
.installations{display:grid;gap:var(--nx-space-3)}
.installations-count{margin:0;font-size:var(--nx-font-size-sm)}
.installation-title{display:flex;flex-wrap:wrap;align-items:center;gap:var(--nx-space-2)}
.installation-meta code{font-size:var(--nx-font-size-xs);color:var(--nx-text-secondary)}
.installation-identity{display:grid;gap:4px;min-width:0}.installation-identity h3{margin:0;font-size:16px;font-weight:600;line-height:24px}.installation-meta{margin:0;color:var(--nx-text-secondary);font-size:var(--nx-font-size-sm);display:flex;flex-wrap:wrap;gap:6px;align-items:baseline}.installation-meta strong{color:var(--nx-text-primary)}
.upgrade{border-top:1px solid var(--nx-divider);padding-top:var(--nx-space-3)}.upgrade summary{cursor:pointer;color:var(--nx-text-regular);font-size:var(--nx-font-size-sm);font-weight:600;list-style-position:inside}.upgrade[open] summary{margin-bottom:12px}.upgrade summary:hover{color:var(--nx-product)}
.installation-id{font-size:var(--nx-font-size-xs)}.installation-id code{font-size:var(--nx-font-size-xs)}
.p4-tabs{display:flex;gap:8px;overflow:auto;margin:0 0 16px;border-bottom:1px solid var(--nx-divider)}.p4-tabs a{padding:10px 14px;color:var(--nx-text-secondary);text-decoration:none;white-space:nowrap;border-bottom:2px solid transparent}.p4-tabs a.router-link-active{color:var(--nx-product);border-color:var(--nx-product);font-weight:600}
.split-layout{display:grid;grid-template-columns:minmax(260px,.75fr) minmax(0,1.5fr);gap:16px}.nx-card h2{margin-top:0}.selectable-row{cursor:pointer}.selectable-row.selected{color:var(--nx-product)}.selectable-row.selected td{background:var(--nx-product-light)}.row-action{color:var(--nx-text-secondary);white-space:nowrap;text-align:right}.detail-card p{line-height:1.6}.facts{display:grid;grid-template-columns:minmax(140px,.7fr) 2fr;gap:10px;margin:20px 0}.facts dt{color:var(--nx-text-secondary)}.facts dd{margin:0;overflow-wrap:anywhere}.section-heading,.product-heading,.installation-summary{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.section-heading h2,.product-heading h3,.installation-summary h3{margin:0}.product-block{border-top:1px solid var(--nx-divider);padding:20px 0}.product-heading{margin-bottom:14px}.product-heading p{color:var(--nx-text-secondary)}.eyebrow{font-size:12px;color:var(--nx-product);font-weight:600;letter-spacing:.04em}.entitlements{list-style:none;padding:0;margin:0}.entitlements li{display:flex;flex-direction:column;gap:3px;margin:0 0 5px}.installation-list{display:grid;gap:var(--nx-space-3)}.installation-card{display:grid;gap:var(--nx-space-3);padding:var(--nx-space-4) var(--nx-space-5);background:var(--nx-surface);border:1px solid var(--nx-border);border-radius:var(--nx-radius,12px);box-shadow:var(--nx-shadow-card)}.installation-summary code{display:block;margin-top:6px;overflow-wrap:anywhere}.installation-summary .nx-btn--primary{align-self:center;white-space:nowrap}.stepper{display:flex;gap:6px;overflow:auto;margin:18px 0}.stepper .step{flex:1;min-width:90px;text-align:center;padding:8px 6px;border-radius:8px;background:var(--nx-bg-sidebar);font-size:12px;display:flex;flex-direction:column;gap:4px;border:1px solid transparent;color:var(--nx-text-regular)}.stepper .step-label{font-weight:600}.stepper .step-status{color:var(--nx-text-secondary);font-size:11px}.stepper .step[data-status="UNKNOWN"]{background:var(--nx-bg-sidebar);color:var(--nx-text-regular)}.stepper .step[data-status="ACTIVE"]{border-color:var(--nx-product);color:var(--nx-product);background:var(--nx-product-light)}.stepper .step[data-status="FAILED"]{border-color:var(--nx-danger);color:var(--nx-danger);background:var(--nx-danger-light)}.stepper .step[data-status="DONE"]{border-color:var(--nx-success);color:var(--nx-success);background:var(--nx-success-light)}.upgrade-form{display:flex;align-items:flex-end;gap:12px;margin:16px 0}.upgrade-form label{display:flex;flex-direction:column;gap:6px;flex:1}.upgrade-form select{height:var(--nx-control-height,36px);padding:0 10px;border:1px solid var(--nx-border-strong);border-radius:var(--nx-control-radius,6px);background:var(--nx-surface);color:inherit}.notice{padding:12px;border-radius:8px;background:var(--nx-success-light);color:var(--nx-success)}
@media(max-width:800px){.split-layout{grid-template-columns:1fr}.upgrade-form{align-items:stretch;flex-direction:column}}
.upgrade-note{margin:0 0 8px;color:var(--nx-text-secondary);font-size:var(--nx-font-size-xs)}.upgrade-note.error{color:var(--nx-danger)}
.upgrade-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:8px;margin:0 0 4px}
.upgrade-fact{display:grid;gap:4px;padding:10px;border-radius:8px;background:var(--nx-bg-sidebar)}
.upgrade-fact dt{color:var(--nx-text-secondary);font-size:12px}
.upgrade-fact dd{display:grid;gap:2px;margin:0}
.upgrade-fact strong{color:var(--nx-text-primary);font-size:13px}.upgrade-fact strong.none{color:var(--nx-text-secondary);font-weight:500}
.upgrade-fact small{color:var(--nx-text-secondary);font-size:11px;line-height:1.4}
[data-testid="subscription-entitlement"]{white-space:nowrap}.entitlement-count{margin-right:6px}
</style>
