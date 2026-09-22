<template>
  <div class="host-harness app-webview-harness" data-testid="app-webview-harness">
    <header class="host-harness-bar">
      <p class="app-webview-badge" data-testid="app-webview-badge">网页模拟 · 非原生 APP 包</p>
      <h1>F014 APP WebView 模拟宿主</h1>
      <p>
        注入 <code>window.NexusAppWebView</code>，通道 login/route/file/close，envelope 与移动浏览器 iframe 宿主相同
        （<code>nexus-h5</code> ↔ <code>nexus-host</code>）。不是 iOS/Android 原生 WebView，也不是小程序。
      </p>
      <div class="host-harness-actions">
        <button type="button" data-testid="app-webview-send-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
          入站 login
        </button>
        <button type="button" data-testid="app-webview-send-route" @click="send('route', { action: 'navigate', path: '/m/host' })">
          入站 route
        </button>
        <button type="button" data-testid="app-webview-send-file" @click="send('file', { action: 'open', fileId: 'demo-file-id' })">
          入站 file
        </button>
        <button type="button" data-testid="app-webview-send-close" @click="send('close', { action: 'close' })">
          入站 close
        </button>
        <button type="button" data-testid="app-webview-send-evil" @click="sendEvil">
          入站拒绝样例
        </button>
      </div>
      <p class="host-harness-status" data-testid="app-webview-status">{{ status }}</p>
    </header>
    <iframe
      ref="frame"
      class="host-harness-frame"
      title="NexusOne H5 in simulated APP WebView"
      data-testid="app-webview-iframe"
      src="/m/host?embedded=true"
      @load="injectBridge"
    />
    <section class="host-harness-log">
      <h2>同一 envelope 日志</h2>
      <ol data-testid="app-webview-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
      <p class="host-harness-leftover" data-testid="app-webview-leftover">
        仍开放：原生 APP WebView 二进制 / 小程序 web-view。产品 G15 未关闭。
      </p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import { attachAppWebViewBridge } from "../../../host/appWebViewHost";
import { encodeHostMessage, HOST_SOURCE, postToFrame } from "../../../host/hostBridge";
import "../../../styles/mobile.css";

const frame = ref(null);
const log = ref([]);
const status = ref("等待注入 NexusAppWebView 并握手 login/ready");

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function injectBridge() {
  const child = frame.value?.contentWindow;
  if (!child) {
    return;
  }
  attachAppWebViewBridge(child, (data) => {
    if (!data || data.source !== "nexus-h5") {
      return;
    }
    append(`h5 → app-webview ${data.channel} ${JSON.stringify(data.payload || {})}`);
    if (data.channel === "login" && data.payload?.action === "ready") {
      status.value = "H5 已握手 login/ready（同一 F014 envelope）";
    }
  });
  status.value = status.value.startsWith("H5 已握手")
    ? status.value
    : "已注入 NexusAppWebView（网页模拟，非原生包）";
}

function send(channel, payload) {
  const child = frame.value?.contentWindow;
  const result = postToFrame(child, channel, payload);
  if (result.ok) {
    status.value = `已发送 ${channel}（source=nexus-host）`;
    append(`app-webview → h5 ${channel} ${JSON.stringify(payload)}`);
    return;
  }
  status.value = `未发送 ${result.reason}`;
  append(`app-webview 拒绝 ${channel} ${result.reason}`);
}

function sendEvil() {
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
  status.value = `拒绝样例 ${token.reason} / ${evalCh.reason}`;
  append(`app-webview 拒绝样例 ${token.reason} / ${evalCh.reason}`);
}

function onMessage(event) {
  if (event.origin !== window.location.origin) {
    return;
  }
  const data = event.data;
  if (!data || data.source !== "nexus-h5") {
    return;
  }
  append(`h5 → parent ${data.channel} ${JSON.stringify(data.payload || {})}`);
  if (data.channel === "login" && data.payload?.action === "ready") {
    status.value = "H5 已握手 login/ready（同一 F014 envelope）";
  }
}

onMounted(() => {
  window.addEventListener("message", onMessage);
});

onUnmounted(() => {
  window.removeEventListener("message", onMessage);
});
</script>
