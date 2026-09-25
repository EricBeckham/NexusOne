<template>
  <main class="p4-management">
    <NxPageHeader eyebrow="P4 · 星市" :title="title" :description="description">
      <template #actions><router-link class="ghost" to="/p4/market">应用目录</router-link><button class="ghost" type="button" :disabled="loading" @click="load">刷新</button></template>
    </NxPageHeader>

    <nav class="p4-tabs" aria-label="星市管理分区">
      <router-link v-for="tab in tabs" :key="tab.to" :to="tab.to" :aria-current="route.path === tab.to ? 'page' : undefined">{{ tab.label }}</router-link>
    </nav>
    <NxErrorState v-if="error" :description="error" retry-label="重试" @retry="load" />
    <p v-else-if="loading" class="nx-muted">正在读取星市数据…</p>

    <template v-else-if="section === 'packages'">
      <div class="split-layout">
        <section class="nx-card">
          <h2>应用包目录</h2>
          <NxEmpty v-if="!apps.length" title="暂无应用包" description="平台创建并发布应用后，会在此显示真实应用包与版本。" />
          <button v-for="app in apps" :key="app.id" class="list-item" :class="{ selected: selectedApp?.id === app.id }" type="button" @click="selectApp(app)">
            <span><strong>{{ app.name }}</strong><small>{{ app.code }} · {{ app.id }}</small></span><span>查看版本 →</span>
          </button>
        </section>
        <section class="nx-card detail-card">
          <template v-if="selectedApp">
            <div class="section-heading"><div><span class="nx-muted">应用包 · {{ selectedApp.code }}</span><h2>{{ selectedApp.name }}</h2></div></div>
            <p>{{ selectedApp.summary || "暂无简介" }}</p>
            <dl class="facts"><dt>创建时间</dt><dd>{{ formatDate(selectedApp.createdAt) }}</dd><dt>包内容 / 依赖 / 权限声明 / 资源声明</dt><dd>当前读模型未提供，暂无数据</dd></dl>
            <h3>版本与发布记录</h3>
            <NxEmpty v-if="!versions.length" title="暂无版本" description="此应用尚无版本记录。" compact />
            <div v-for="version in versions" :key="version.id" class="version-row">
              <div><strong>{{ version.version }}</strong><small>{{ version.id }}</small><p>{{ version.releaseNotes || "暂无发布说明" }}</p></div>
              <NxStatusTag :status="version.status" :label="version.status" />
            </div>
          </template>
          <NxEmpty v-else title="选择一个应用包" description="应用包详情和版本记录将显示在这里。" />
        </section>
      </div>
    </template>

    <template v-else-if="section === 'skus'">
      <section class="nx-card">
        <div class="section-heading"><div><h2>Product / SKU</h2><p class="nx-muted">Product（应用）与 SKU 分开展示；权益定义来自 SKU 读模型。</p></div><span class="nx-muted">{{ apps.length }} 个 Product · {{ skus.length }} 个 SKU</span></div>
        <NxEmpty v-if="!apps.length" title="暂无 Product" description="当前没有可读的应用产品。" />
        <article v-for="app in apps" :key="app.id" class="product-block">
          <div class="product-heading"><div><span class="eyebrow">PRODUCT · {{ app.code }}</span><h3>{{ app.name }}</h3><p>{{ app.summary || "暂无简介" }}</p></div><code>{{ app.id }}</code></div>
          <NxEmpty v-if="!skusFor(app).length" title="暂无 SKU" description="该 Product 暂无 SKU 定义。" compact />
          <table v-else class="nx-table"><thead><tr><th>SKU</th><th>权益</th><th>配额 / 属性</th></tr></thead><tbody>
            <tr v-for="sku in skusFor(app)" :key="sku.code"><td><strong>{{ sku.name || sku.code }}</strong><div class="nx-muted">{{ sku.code }}</div></td><td><span v-if="!Object.keys(sku.entitlements || {}).length" class="nx-muted">暂无权益定义</span><ul v-else class="entitlements"><li v-for="(entitlement, name) in sku.entitlements" :key="name"><code>{{ name }}</code><span>{{ entitlement.enabled ? "启用" : "停用" }}</span></li></ul></td><td><span v-if="!Object.keys(sku.entitlements || {}).length" class="nx-muted">暂无</span><ul v-else class="entitlements"><li v-for="(entitlement, name) in sku.entitlements" :key="name"><span>{{ name }}</span><span>{{ entitlement.quota ?? "未设置" }} · {{ formatAttributes(entitlement.attributes) }}</span></li></ul></td></tr>
          </tbody></table>
        </article>
      </section>
    </template>

    <template v-else-if="section === 'subscriptions'">
      <NxNoPermission v-if="!session.contextId" title="需要租户上下文" description="订阅数据按当前服务端租户上下文读取。请先登录并切换租户。" />
      <section v-else class="nx-card">
        <div class="section-heading"><div><h2>订阅</h2><p class="nx-muted">当前租户的订阅及其对应安装状态。</p></div><span class="nx-muted">{{ subscriptions.length }} 条记录</span></div>
        <NxEmpty v-if="!subscriptions.length" title="暂无订阅" description="当前租户还没有订阅记录。" />
        <div v-else class="table-scroll"><table class="nx-table"><thead><tr><th>租户</th><th>Product</th><th>SKU</th><th>状态</th><th>开始</th><th>到期</th><th>权益状态</th><th>安装状态</th></tr></thead><tbody>
          <tr v-for="sub in subscriptions" :key="sub.id"><td><code>{{ sub.tenantId }}</code></td><td>{{ appForSku(sub.skuCode)?.name || appForSku(sub.skuCode)?.code || "暂无" }}</td><td>{{ skuByCode(sub.skuCode)?.name || sub.skuCode }}</td><td><NxStatusTag :status="sub.status" :label="sub.status" /></td><td>{{ formatDate(sub.startedAt) }}</td><td>{{ formatDate(sub.endedAt) }}</td><td><span class="nx-muted">暂无投影读模型</span></td><td>{{ installationStatus(sub) }}</td></tr>
        </tbody></table></div>
      </section>
    </template>

    <template v-else>
      <NxNoPermission v-if="!session.contextId" title="需要租户上下文" description="安装记录按当前服务端租户上下文读取。请先登录并切换租户。" />
      <section v-else class="nx-card">
        <div class="section-heading count-only" data-testid="installations-count"><span class="nx-muted">{{ installations.length }} 条记录</span></div>
        <NxEmpty v-if="!installations.length" title="还没有已开通的产品" description="从应用目录订阅开通后，产品会显示在这里，并可直接发起申请。"><template #action><router-link class="primary" to="/p4/market">前往应用目录</router-link></template></NxEmpty>
        <div v-else class="installation-list"><article v-for="installation in installations" :key="installation.id" class="installation-card">
          <div class="installation-summary">
            <div class="installation-identity">
              <span class="eyebrow">{{ appById(installation.appId)?.code || "应用" }}</span>
              <h3>{{ appById(installation.appId)?.name || "未命名应用" }}</h3>
              <p class="installation-meta">当前版本 <strong>{{ versionLabel(installation.versionId) }}</strong><span aria-hidden="true">·</span>更新于 {{ formatDate(installation.updatedAt) }}</p>
            </div>
            <NxStatusTag :status="installation.status" :label="installStatusDisplay(installation.status).label" :tone="installStatusDisplay(installation.status).tone" data-testid="installation-status" />
            <router-link v-if="installation.status === 'INSTALLED'" class="primary" :to="`/p4/installations/${installation.id}/start`" data-testid="start-application">发起申请</router-link>
          </div>
          <details class="upgrade" data-testid="installation-upgrade">
            <summary>版本升级</summary>
            <div class="stepper" aria-label="升级步骤"><span v-for="step in upgradeSteps" :key="step">{{ step }}</span></div>
            <div class="upgrade-facts"><p><span>兼容范围</span><strong>暂无数据</strong></p><p><span>Schema 变更</span><strong>暂无数据</strong></p><p><span>数据迁移</span><strong>暂无数据</strong></p><p><span>回滚能力</span><strong>服务端未提供独立回滚说明</strong></p></div>
            <form class="upgrade-form" @submit.prevent="upgrade(installation)"><label>目标版本<select v-model="targets[installation.id]" required><option value="">请选择已发布版本</option><option v-for="version in publishedVersions(installation.appId)" :key="version.id" :value="version.id">{{ version.version }}</option></select></label><button class="nx-btn nx-btn--default" type="submit" data-testid="upgrade-submit" :disabled="busyId === installation.id || !targets[installation.id]">{{ busyId === installation.id ? "提交中…" : "提交升级" }}</button></form>
          </details>
          <small class="nx-muted installation-id">安装编号 <code>{{ installation.id }}</code></small>
        </article></div>
      </section>
    </template>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { NxEmpty, NxErrorState, NxNoPermission, NxPageHeader, NxStatusTag } from "../../components";
import { p4 } from "../../api/client";
import { session } from "../../api/session";

const route = useRoute();
const apps = ref([]); const skus = ref([]); const subscriptions = ref([]); const installations = ref([]);
const versionsByApp = reactive({}); const selectedApp = ref(null); const versions = ref([]);
const targets = reactive({}); const loading = ref(true); const error = ref(""); const notice = ref(""); const busyId = ref("");
const section = computed(() => ({ "p4-packages": "packages", "p4-skus": "skus", "p4-subscriptions": "subscriptions", "p4-installations": "installations" })[route.name] || "packages");
const tabs = [{ label: "应用包", to: "/p4/packages" }, { label: "商品与 SKU", to: "/p4/skus" }, { label: "订阅", to: "/p4/subscriptions" }, { label: "我的已开通", to: "/p4/installations" }];
const titles = { packages: ["应用包", "浏览应用包、版本与发布状态；未提供的包内容或声明保持明确空态。"], skus: ["商品与 SKU", "Product 对应应用，SKU 单独维护权益与配额定义。"], subscriptions: ["订阅", "查看当前租户的订阅周期、状态与关联安装。"], installations: ["我的已开通", "无支付、即时开通后的产品记录。升级仍提交到已发布版本。"] };
const title = computed(() => titles[section.value][0]); const description = computed(() => titles[section.value][1]);
// O11: one Primary per card — 发起申请 is the card's action; upgrade stays secondary inside its fold.
const upgradeSteps = ["预检", "依赖检查", "结构扩展与迁移", "配置", "切换", "验证"];

// The product's JSON is SNAKE_CASE (app_id, version_id, sku_code, updated_at…); this page reads camelCase.
const camelKey = (key) => key.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
function camelRow(row) {
  if (!row || typeof row !== "object") return row;
  const out = { ...row };
  for (const [key, value] of Object.entries(row)) if (key.includes("_") && !(camelKey(key) in out)) out[camelKey(key)] = value;
  return out;
}
const camelRows = (rows) => (Array.isArray(rows) ? rows : []).map(camelRow);

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
.installation-identity{display:grid;gap:2px;min-width:0}.installation-identity h3{margin:0}.installation-meta{margin:0;color:var(--nx-text-secondary);font-size:var(--nx-font-size-sm);display:flex;flex-wrap:wrap;gap:6px;align-items:baseline}.installation-meta strong{color:var(--nx-text-primary)}
.upgrade{border-top:1px solid var(--nx-divider);padding-top:8px}.upgrade summary{cursor:pointer;color:var(--nx-text-regular);font-size:var(--nx-font-size-sm);font-weight:600;list-style-position:inside}.upgrade[open] summary{margin-bottom:12px}.upgrade summary:hover{color:var(--nx-product)}
.installation-id{font-size:var(--nx-font-size-xs)}.installation-id code{font-size:var(--nx-font-size-xs)}
.p4-tabs{display:flex;gap:8px;overflow:auto;margin:0 0 16px;border-bottom:1px solid var(--nx-divider)}.p4-tabs a{padding:10px 14px;color:var(--nx-text-secondary);text-decoration:none;white-space:nowrap;border-bottom:2px solid transparent}.p4-tabs a.router-link-active{color:var(--nx-product);border-color:var(--nx-product);font-weight:600}
.split-layout{display:grid;grid-template-columns:minmax(260px,.75fr) minmax(0,1.5fr);gap:16px}.nx-card h2{margin-top:0}.list-item{display:flex;width:100%;justify-content:space-between;gap:12px;align-items:center;text-align:left;background:transparent;color:inherit;border:0;border-top:1px solid var(--nx-divider);padding:14px 4px;cursor:pointer}.list-item span:first-child{display:flex;flex-direction:column;gap:4px}.list-item small,.version-row small{color:var(--nx-text-secondary);overflow-wrap:anywhere}.list-item.selected{color:var(--nx-product)}.detail-card p{line-height:1.6}.facts{display:grid;grid-template-columns:minmax(140px,.7fr) 2fr;gap:10px;margin:20px 0}.facts dt{color:var(--nx-text-secondary)}.facts dd{margin:0;overflow-wrap:anywhere}.version-row{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;border-top:1px solid var(--nx-divider);padding:14px 0}.version-row p{margin:5px 0 0;color:var(--nx-text-secondary)}.section-heading,.product-heading,.installation-summary{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.section-heading h2,.product-heading h3,.installation-summary h3{margin:0}.product-block{border-top:1px solid var(--nx-divider);padding:20px 0}.product-heading{margin-bottom:14px}.product-heading p{color:var(--nx-text-secondary)}.eyebrow{font-size:12px;color:var(--nx-product);font-weight:600;letter-spacing:.04em}.entitlements{list-style:none;padding:0;margin:0}.entitlements li{display:flex;flex-direction:column;gap:3px;margin:0 0 5px}.table-scroll{overflow:auto}.installation-list{display:grid;gap:14px}.installation-card{border:1px solid var(--nx-divider);border-radius:var(--nx-radius,12px);padding:18px}.installation-summary code{display:block;margin-top:6px;overflow-wrap:anywhere}.installation-summary .primary{display:inline-flex;align-items:center;align-self:center;min-height:36px;white-space:nowrap}.stepper{display:flex;gap:6px;overflow:auto;margin:18px 0}.stepper span{flex:1;min-width:90px;text-align:center;padding:8px 6px;border-radius:8px;background:var(--nx-bg-sidebar);font-size:12px}.upgrade-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:8px}.upgrade-facts p{display:flex;flex-direction:column;gap:5px;margin:0;padding:10px;background:var(--nx-bg-sidebar);border-radius:8px}.upgrade-facts span,.upgrade-facts strong{font-size:13px}.upgrade-facts span{color:var(--nx-text-secondary)}.upgrade-form{display:flex;align-items:flex-end;gap:12px;margin:16px 0}.upgrade-form label{display:flex;flex-direction:column;gap:6px;flex:1}.upgrade-form select{height:var(--nx-control-height,36px);padding:0 10px;border:1px solid var(--nx-border-strong);border-radius:var(--nx-control-radius,6px);background:var(--nx-surface);color:inherit}.notice{padding:12px;border-radius:8px;background:var(--nx-success-light);color:var(--nx-success)}
@media(max-width:800px){.split-layout{grid-template-columns:1fr}.upgrade-form{align-items:stretch;flex-direction:column}}
</style>
