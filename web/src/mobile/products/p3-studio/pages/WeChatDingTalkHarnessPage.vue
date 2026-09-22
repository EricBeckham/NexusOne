<template>
  <div class="wx-dt-harness" :class="vendorClass" data-testid="wx-dt-harness">
    <header class="wx-dt-banner">
      <p class="wx-dt-badge" data-testid="wx-dt-badge">生产宿主契约桩 · 不是生产验收</p>
      <h1>微信 / 钉钉生产宿主契约桩</h1>
      <p data-testid="wx-dt-honest">
        同一 HostBridgePort 通道 login/route/file/close · 同域 postMessage。
        不是微信 / 钉钉生产验收。APP 原生包仍开放。产品 G15 未关闭。
      </p>
      <p class="wx-dt-contract" data-testid="wx-dt-contract">
        host={{ contract.host }} · vendor={{ contract.vendor }}
        · origin={{ contract.originAllowlist.join(",") || "（无）" }}
        · productionAccepted={{ contract.productionAccepted }}
        · stub={{ contract.stub }}
        · wechat.prod={{ contract.vendors.wechat.productionAccepted }}
        · dingtalk.prod={{ contract.vendors.dingtalk.productionAccepted }}
      </p>
      <div class="wx-dt-vendor-switch" data-testid="wx-dt-vendor-switch">
        <button
          type="button"
          data-testid="wx-dt-pick-wechat"
          :aria-pressed="vendor === 'wechat'"
          @click="pickVendor('wechat')"
        >
          微信桩
        </button>
        <button
          type="button"
          data-testid="wx-dt-pick-dingtalk"
          :aria-pressed="vendor === 'dingtalk'"
          @click="pickVendor('dingtalk')"
        >
          钉钉桩
        </button>
      </div>
    </header>
    <div class="wx-dt-phone" data-testid="wx-dt-phone">
      <div class="wx-dt-status" data-testid="wx-dt-status">
        <span>9:41</span>
        <span data-testid="wx-dt-vendor-label">{{ vendorLabel }}</span>
        <span>100%</span>
      </div>
      <div class="wx-dt-nav" data-testid="wx-dt-nav">
        <span class="wx-dt-nav-back" aria-hidden="true">〈</span>
        <span class="wx-dt-nav-title">星枢 H5</span>
        <span class="wx-dt-capsule" data-testid="wx-dt-capsule">{{ vendorCapsule }}</span>
      </div>
      <iframe
        v-if="childSrcAllowed"
        :key="childSrc"
        ref="frame"
        class="wx-dt-webview"
        title="NexusOne H5 vendor production stub"
        data-testid="wx-dt-webview"
        :src="childSrc"
      />
      <p v-else class="wx-dt-denied" data-testid="wx-dt-denied">web-view src 被源白名单拒绝</p>
    </div>
    <section class="wx-dt-console">
      <h2>契约桩控制台</h2>
      <div class="wx-dt-actions">
        <button type="button" data-testid="wx-dt-send-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
          入站 login
        </button>
        <button type="button" data-testid="wx-dt-send-route" @click="send('route', { action: 'navigate', path: '/m/host' })">
          入站 route
        </button>
        <button type="button" data-testid="wx-dt-send-file" @click="send('file', { action: 'open', fileId: 'demo-file-id' })">
          入站 file
        </button>
        <button type="button" data-testid="wx-dt-send-close" @click="send('close', { action: 'close' })">
          入站 close
        </button>
        <button type="button" data-testid="wx-dt-send-evil" @click="sendDenied">
          入站拒绝样例
        </button>
      </div>
      <p class="wx-dt-status-line" data-testid="wx-dt-parent-status">{{ status }}</p>
      <ol data-testid="wx-dt-parent-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
      <p class="wx-dt-leftover" data-testid="wx-dt-leftover">
        仍开放：APP 原生包 / 微信钉钉生产验收。产品 G15 未关闭。
      </p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { encodeHostMessage, HOST_SOURCE } from "../../../host/hostBridge";
import {
  VENDOR_PROD_ORIGINS,
  childSrcForVendor,
  decodeVendorHostMessage,
  describeVendorProdContract,
  isAllowedVendorHostSrc,
  postToVendorHost,
} from "../../../host/wechatDingTalkHost";
import "../../../styles/mobile.css";

const frame = ref(null);
const log = ref([]);
const status = ref("等待 H5 login/ready");
const vendor = ref("wechat");

const childSrc = computed(() => childSrcForVendor(vendor.value));
const childSrcAllowed = computed(() => isAllowedVendorHostSrc(childSrc.value));
const contract = computed(() => describeVendorProdContract(undefined, vendor.value));
const vendorClass = computed(() => (vendor.value === "dingtalk" ? "wx-dt-dingtalk" : "wx-dt-wechat"));
const vendorLabel = computed(() => (vendor.value === "dingtalk" ? "钉钉桩" : "微信桩"));
const vendorCapsule = computed(() => (vendor.value === "dingtalk" ? "·· ·" : "··· ○"));

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function pickVendor(next) {
  vendor.value = next === "dingtalk" ? "dingtalk" : "wechat";
  status.value = `已切换 ${vendorLabel.value}，等待 H5 login/ready`;
  append(`stub 切换 ${vendor.value} src=${childSrc.value}`);
}

function send(channel, payload) {
  const child = frame.value?.contentWindow;
  const result = postToVendorHost(child, channel, payload);
  if (result.ok) {
    status.value = `已发送 ${channel}`;
    append(`${vendor.value}-stub → h5 ${channel} ${JSON.stringify(payload)}`);
    return;
  }
  status.value = `未发送 ${result.reason}`;
  append(`${vendor.value}-stub 拒绝 ${channel} ${result.reason}`);
}

function sendDenied() {
  const token = encodeHostMessage({
    source: HOST_SOURCE,
    channel: "login",
    payload: { action: "done", access_token: "leak" },
  });
  const evalCh = encodeHostMessage({
    source: HOST_SOURCE,
    channel: "eval",
    payload: {},
  });
  const origins = VENDOR_PROD_ORIGINS.map((origin) => {
    const decoded = decodeVendorHostMessage({
      origin,
      data: { source: "nexus-h5", channel: "close", payload: { action: "close" } },
    });
    return `${origin.split("//")[1]}:${decoded.reason}`;
  }).join(" / ");
  status.value = `拒绝样例 ${token.reason} / ${evalCh.reason} / ${origins}`;
  append(`${vendor.value}-stub 拒绝样例 ${token.reason} / ${evalCh.reason} / ${origins}`);
}

function onMessage(event) {
  const decoded = decodeVendorHostMessage(event);
  if (!decoded.ok) {
    return;
  }
  if (decoded.envelope.source !== "nexus-h5") {
    return;
  }
  append(`h5 → ${vendor.value}-stub ${decoded.envelope.channel} ${JSON.stringify(decoded.envelope.payload || {})}`);
  if (decoded.envelope.channel === "login" && decoded.envelope.payload?.action === "ready") {
    status.value = "H5 已握手 login/ready";
  }
}

onMounted(() => {
  window.addEventListener("message", onMessage);
});

onUnmounted(() => {
  window.removeEventListener("message", onMessage);
});
</script>
