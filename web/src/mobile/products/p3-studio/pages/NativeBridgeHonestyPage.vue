<template>
  <div class="native-bridge-honesty" :class="platformClass" data-testid="native-bridge-honesty-harness">
    <header class="native-bridge-honesty-banner">
      <p class="native-bridge-honesty-badge" data-testid="native-bridge-honesty-badge">
        原生桥诚实 leftover · 不是上架 IPA/APK
      </p>
      <h1>APP WebView 原生桥诚实 PASS/SKIP</h1>
      <p data-testid="native-bridge-honesty-honest">
        同一 F014 envelope（login/route/file/close）。扩展 #151 原生桥契约桩。
        不是 #114 网页 <code>NexusAppWebView</code>，不是 #117 模拟小程序，
        不是 #132 微信钉钉生产验收，不是 #167 生产缺口诚实桩上架。
        产品 G15 未关闭。
      </p>
      <p class="native-bridge-honesty-contract" data-testid="native-bridge-honesty-contract">
        leftoverKind={{ contract.leftoverKind }}
        · platform={{ contract.platform }}
        · stubContract={{ contract.stubContract }}
        · iosIpa={{ contract.iosIpaProduction }}
        · androidApk={{ contract.androidApkProduction }}
        · storeReady={{ contract.storeReadyStatus }}
        · g15Close={{ contract.g15Close }}
        · productionAccepted={{ contract.productionAccepted }}
        · stub={{ contract.stub }}
      </p>
      <p class="native-bridge-honesty-not-claimed" data-testid="native-bridge-honesty-not-claimed">
        不宣称：#114 {{ contract.notClaimed["#114"] }}；
        #117 {{ contract.notClaimed["#117"] }}；
        #132 {{ contract.notClaimed["#132"] }}；
        #151 {{ contract.notClaimed["#151"] }}；
        #167 {{ contract.notClaimed["#167"] }}。
      </p>
      <div class="native-bridge-honesty-platform-switch" data-testid="native-bridge-honesty-platform-switch">
        <button
          type="button"
          data-testid="native-bridge-honesty-pick-ios"
          :aria-pressed="platform === 'ios'"
          @click="pickPlatform('ios')"
        >
          iOS IPA SKIP
        </button>
        <button
          type="button"
          data-testid="native-bridge-honesty-pick-android"
          :aria-pressed="platform === 'android'"
          @click="pickPlatform('android')"
        >
          Android APK SKIP
        </button>
      </div>
    </header>
    <section class="native-bridge-honesty-console">
      <h2>诚实清单（契约桩，不是上架走通）</h2>
      <ul data-testid="native-bridge-honesty-items">
        <li v-for="item in contract.gapItems" :key="item">{{ item }} = SKIP</li>
        <li>stub_contract = PASS（#151 形状桩仍成立，不是 IPA/APK）</li>
      </ul>
      <div class="native-bridge-honesty-actions">
        <button type="button" data-testid="native-bridge-honesty-probe-origins" @click="probeOrigins">
          验拒绝源
        </button>
        <button type="button" data-testid="native-bridge-honesty-probe-envelope" @click="probeEnvelope">
          验拒绝通道
        </button>
        <button type="button" data-testid="native-bridge-honesty-probe-fake-close" @click="probeFakeClose">
          验假关闭报告
        </button>
      </div>
      <p class="native-bridge-honesty-status" data-testid="native-bridge-honesty-status">{{ status }}</p>
      <ol data-testid="native-bridge-honesty-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
      <p class="native-bridge-honesty-leftover" data-testid="native-bridge-honesty-leftover">
        仍开放：上架 IPA/APK / WKWebView·Android WebView 产品宿主 / 微信钉钉生产。
        产品 G15 未关闭。#114 / #117 / #132 / #151 / #167 leftover 保留。
        docs/README 保留 g15-native-bridge-stub 与 g15-wx-dt-prod-gap-honesty。
      </p>
      <router-link
        class="native-bridge-honesty-link"
        to="/m/native-bridge-harness"
        data-testid="native-bridge-honesty-open-151"
      >
        打开 #151 原生桥契约桩（不重做）
      </router-link>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import {
  describeNativeBridgeHonestyContract,
  honestNativeBridgeReport,
  probeNativeEnvelopeDenied,
  probeNativeOriginsDenied,
  verifyNativeBridgeHonestyReport,
} from "../../../host/nativeBridgeHonesty";
import "../../../styles/mobile.css";

const log = ref([]);
const status = ref("诚实 leftover · 不是上架 IPA/APK");
const platform = ref("ios");

const contract = computed(() => describeNativeBridgeHonestyContract(undefined, platform.value));
const platformClass = computed(() => (
  platform.value === "android" ? "native-bridge-honesty-android" : "native-bridge-honesty-ios"
));

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function pickPlatform(next) {
  platform.value = next === "android" ? "android" : "ios";
  status.value = `已切换 ${platform.value} 诚实清单（IPA/APK 仍 SKIP / G15 NOT_CLAIMED）`;
  append(`honesty stub 切换 ${platform.value}`);
}

function probeOrigins() {
  const results = probeNativeOriginsDenied();
  const summary = results.map((item) => `${item.origin.split("//")[1]}:${item.reason}`).join(" / ");
  const allDenied = results.every((item) => item.ok);
  status.value = allDenied ? `拒绝源 ${summary}` : `源探针未全部拒绝 ${summary}`;
  append(`native origins ${allDenied ? "origin_rejected" : "FAIL"} ${summary}`);
}

function probeEnvelope() {
  const denied = probeNativeEnvelopeDenied();
  status.value = `通道拒绝 token=${denied.tokenReason} eval=${denied.evalReason} harnessDenied=${denied.harnessSrcDenied}`;
  append(`envelope denied token=${denied.tokenRejected} eval=${denied.evalRejected}`);
}

function probeFakeClose() {
  const honest = verifyNativeBridgeHonestyReport(honestNativeBridgeReport());
  const fake = verifyNativeBridgeHonestyReport({ ...honestNativeBridgeReport(), g15_close: "CLOSED" });
  status.value = `诚实报告=${honest.ok} 假关闭=${fake.ok === false ? "rejected" : "FAIL"}`;
  append(`honesty gate honest=${honest.ok} fakeCloseRejected=${fake.ok === false}`);
}
</script>
