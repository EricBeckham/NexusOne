<template>
  <div class="mp-webview-harness" data-testid="mp-webview-harness">
    <header class="mp-webview-banner">
      <h1>模拟小程序 web-view 父页</h1>
      <p data-testid="mp-webview-honest">
        同一 HostBridgePort 通道 login/route/file/close · 同域 postMessage。
        不是微信 / 钉钉生产验收。APP WebView 仍开放。
      </p>
      <p class="mp-webview-contract" data-testid="mp-webview-contract">
        host={{ contract.host }} · origin={{ contract.originAllowlist.join(",") || "（无）" }}
        · productionAccepted={{ contract.productionAccepted }}
        · wechat={{ contract.vendors.wechat }} · dingtalk={{ contract.vendors.dingtalk }}
      </p>
    </header>
    <div class="mp-phone" data-testid="mp-phone">
      <div class="mp-status" data-testid="mp-status">
        <span>9:41</span>
        <span>模拟小程序</span>
        <span>100%</span>
      </div>
      <div class="mp-nav" data-testid="mp-nav">
        <span class="mp-nav-back" aria-hidden="true">〈</span>
        <span class="mp-nav-title">星枢 H5</span>
        <span class="mp-capsule" data-testid="mp-capsule">··· ○</span>
      </div>
      <iframe
        v-if="childSrcAllowed"
        ref="frame"
        class="mp-webview"
        title="NexusOne H5 web-view"
        data-testid="mp-webview"
        :src="childSrc"
      />
      <p v-else class="mp-webview-denied" data-testid="mp-webview-denied">web-view src 被源白名单拒绝</p>
    </div>
    <section class="mp-parent-console">
      <h2>模拟父页控制台</h2>
      <div class="mp-parent-actions">
        <button type="button" data-testid="mp-send-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
          入站 login
        </button>
        <button type="button" data-testid="mp-send-route" @click="send('route', { action: 'navigate', path: '/m/host' })">
          入站 route
        </button>
        <button type="button" data-testid="mp-send-file" @click="send('file', { action: 'open', fileId: 'demo-file-id' })">
          入站 file
        </button>
        <button type="button" data-testid="mp-send-close" @click="send('close', { action: 'close' })">
          入站 close
        </button>
        <button type="button" data-testid="mp-send-evil" @click="sendDenied">
          入站拒绝样例
        </button>
      </div>
      <p class="mp-parent-status" data-testid="mp-parent-status">{{ status }}</p>
      <ol data-testid="mp-parent-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import { encodeHostMessage, HOST_SOURCE } from "../../../host/hostBridge";
import {
  FOREIGN_WEBVIEW_ORIGINS,
  MP_WEBVIEW_CHILD_SRC,
  decodeMiniProgramMessage,
  describeMpWebViewContract,
  isAllowedWebViewSrc,
  postToWebView,
} from "../../../host/miniprogramWebView";
import "../../../styles/mobile.css";

const frame = ref(null);
const log = ref([]);
const status = ref("等待 H5 login/ready");
const childSrc = MP_WEBVIEW_CHILD_SRC;
const childSrcAllowed = isAllowedWebViewSrc(childSrc);
const contract = describeMpWebViewContract();

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function send(channel, payload) {
  const child = frame.value?.contentWindow;
  const result = postToWebView(child, channel, payload);
  if (result.ok) {
    status.value = `已发送 ${channel}`;
    append(`mp-parent → web-view ${channel} ${JSON.stringify(payload)}`);
    return;
  }
  status.value = `未发送 ${result.reason}`;
  append(`mp-parent 拒绝 ${channel} ${result.reason}`);
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
  const origins = FOREIGN_WEBVIEW_ORIGINS.map((origin) => {
    const decoded = decodeMiniProgramMessage({
      origin,
      data: { source: "nexus-h5", channel: "close", payload: { action: "close" } },
    });
    return `${origin.split("//")[1]}:${decoded.reason}`;
  }).join(" / ");
  status.value = `拒绝样例 ${token.reason} / ${evalCh.reason} / ${origins}`;
  append(`mp-parent 拒绝样例 ${token.reason} / ${evalCh.reason} / ${origins}`);
}

function onMessage(event) {
  const decoded = decodeMiniProgramMessage(event);
  if (!decoded.ok) {
    return;
  }
  if (decoded.envelope.source !== "nexus-h5") {
    return;
  }
  append(`web-view → mp-parent ${decoded.envelope.channel} ${JSON.stringify(decoded.envelope.payload || {})}`);
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
