<template>
  <div class="wx-dt-prod-gap" :class="vendorClass" data-testid="wx-dt-prod-gap-harness">
    <header class="wx-dt-prod-gap-banner">
      <p class="wx-dt-prod-gap-badge" data-testid="wx-dt-prod-gap-badge">
        生产小程序验收缺口诚实 · 不是生产验收
      </p>
      <h1>微信 / 钉钉生产小程序验收缺口</h1>
      <p data-testid="wx-dt-prod-gap-honest">
        同一 F014 envelope（login/route/file/close）。不是 #117 模拟 web-view，
        不是 #132 生产宿主桩验收，不是 #151 原生桥上架。产品 G15 未关闭。
      </p>
      <p class="wx-dt-prod-gap-contract" data-testid="wx-dt-prod-gap-contract">
        leftoverKind={{ contract.leftoverKind }}
        · vendor={{ contract.vendor }}
        · productionAccepted={{ contract.productionAccepted }}
        · g15Close={{ contract.g15Close }}
        · wechat={{ contract.wechatMiniProgramProduction }}
        · dingtalk={{ contract.dingtalkMiniProgramProduction }}
        · vendorJsapiLoaded={{ contract.vendorJsapiLoaded }}
        · stub={{ contract.stub }}
      </p>
      <p class="wx-dt-prod-gap-not-claimed" data-testid="wx-dt-prod-gap-not-claimed">
        不宣称：#117 {{ contract.notClaimed["#117"] }}；
        #132 {{ contract.notClaimed["#132"] }}；
        #151 {{ contract.notClaimed["#151"] }}。
      </p>
      <div class="wx-dt-prod-gap-vendor-switch" data-testid="wx-dt-prod-gap-vendor-switch">
        <button
          type="button"
          data-testid="wx-dt-prod-gap-pick-wechat"
          :aria-pressed="vendor === 'wechat'"
          @click="pickVendor('wechat')"
        >
          微信缺口
        </button>
        <button
          type="button"
          data-testid="wx-dt-prod-gap-pick-dingtalk"
          :aria-pressed="vendor === 'dingtalk'"
          @click="pickVendor('dingtalk')"
        >
          钉钉缺口
        </button>
      </div>
    </header>
    <section class="wx-dt-prod-gap-console">
      <h2>诚实清单（契约桩，不是生产走通）</h2>
      <ul data-testid="wx-dt-prod-gap-items">
        <li v-for="item in contract.gapItems" :key="item">{{ item }} = SKIP</li>
      </ul>
      <div class="wx-dt-prod-gap-actions">
        <button type="button" data-testid="wx-dt-prod-gap-probe-origins" @click="probeOrigins">
          验拒绝源
        </button>
        <button type="button" data-testid="wx-dt-prod-gap-probe-envelope" @click="probeEnvelope">
          验拒绝通道
        </button>
        <button type="button" data-testid="wx-dt-prod-gap-probe-fake-close" @click="probeFakeClose">
          验假关闭报告
        </button>
      </div>
      <p class="wx-dt-prod-gap-status" data-testid="wx-dt-prod-gap-status">{{ status }}</p>
      <ol data-testid="wx-dt-prod-gap-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
      <p class="wx-dt-prod-gap-leftover" data-testid="wx-dt-prod-gap-leftover">
        仍开放：微信 / 钉钉生产小程序 / APP 上架原生包。产品 G15 未关闭。
        #117 / #132 / #151 leftover 保留。docs/README 保留
        g17-gm-tls-nginx-handshake 与 g17-vue-otp-writeback。
      </p>
      <router-link class="wx-dt-prod-gap-link" to="/m/wx-dt-harness" data-testid="wx-dt-prod-gap-open-132">
        打开 #132 生产宿主契约桩（不重做）
      </router-link>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import {
  describeProdGapContract,
  honestProdGapReport,
  probeSameEnvelopeDenied,
  probeVendorOriginsDenied,
  verifyProdGapReport,
} from "../../../host/wechatDingTalkProdGapHonesty";
import "../../../styles/mobile.css";

const log = ref([]);
const status = ref("诚实缺口契约桩 · 不是生产验收");
const vendor = ref("wechat");

const contract = computed(() => describeProdGapContract(undefined, vendor.value));
const vendorClass = computed(() => (vendor.value === "dingtalk" ? "wx-dt-prod-gap-dingtalk" : "wx-dt-prod-gap-wechat"));

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function pickVendor(next) {
  vendor.value = next === "dingtalk" ? "dingtalk" : "wechat";
  status.value = `已切换 ${vendor.value} 缺口清单（仍 SKIP / NOT_CLAIMED）`;
  append(`gap stub 切换 ${vendor.value}`);
}

function probeOrigins() {
  const results = probeVendorOriginsDenied();
  const summary = results.map((item) => `${item.origin.split("//")[1]}:${item.reason}`).join(" / ");
  const allDenied = results.every((item) => item.ok);
  status.value = allDenied ? `拒绝源 ${summary}` : `源探针未全部拒绝 ${summary}`;
  append(`vendor origins ${allDenied ? "origin_rejected" : "FAIL"} ${summary}`);
}

function probeEnvelope() {
  const denied = probeSameEnvelopeDenied();
  status.value = `通道拒绝 token=${denied.tokenReason} eval=${denied.evalReason} vendorSrcDenied=${denied.wechatSrcDenied && denied.dingtalkSrcDenied}`;
  append(`envelope denied token=${denied.tokenRejected} eval=${denied.evalRejected}`);
}

function probeFakeClose() {
  const honest = verifyProdGapReport(honestProdGapReport());
  const fake = verifyProdGapReport({ ...honestProdGapReport(), g15_close: "CLOSED" });
  status.value = `诚实报告=${honest.ok} 假关闭=${fake.ok === false ? "rejected" : "FAIL"}`;
  append(`honesty gate honest=${honest.ok} fakeCloseRejected=${fake.ok === false}`);
}
</script>
