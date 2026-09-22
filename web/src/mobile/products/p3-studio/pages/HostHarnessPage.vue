<template>
  <div class="host-harness" data-testid="host-harness">
    <header class="host-harness-bar">
      <h1>F014 宿主（移动浏览器 iframe）</h1>
      <p>source=nexus-host · 通道 login/route/file/close · 同域 postMessage。不是 APP WebView / 小程序。</p>
      <div class="host-harness-actions">
        <button type="button" data-testid="harness-send-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
          入站 login
        </button>
        <button type="button" data-testid="harness-send-route" @click="send('route', { action: 'navigate', path: '/m/host' })">
          入站 route
        </button>
        <button type="button" data-testid="harness-send-file" @click="send('file', { action: 'open', fileId: 'demo-file-id' })">
          入站 file
        </button>
        <button type="button" data-testid="harness-send-close" @click="send('close', { action: 'close' })">
          入站 close
        </button>
        <button type="button" data-testid="harness-send-evil" @click="sendEvil">
          入站拒绝样例
        </button>
      </div>
      <p class="host-harness-status" data-testid="harness-status">{{ status }}</p>
    </header>
    <iframe
      ref="frame"
      class="host-harness-frame"
      title="NexusOne H5"
      data-testid="host-iframe"
      src="/m/host?embedded=true"
    />
    <section class="host-harness-log">
      <h2>消息日志</h2>
      <ol data-testid="harness-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import { encodeHostMessage, HOST_SOURCE, postToFrame } from "../../../host/hostBridge";
import "../../../styles/mobile.css";

const frame = ref(null);
const log = ref([]);
const status = ref("等待 H5 login/ready");

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function send(channel, payload) {
  const child = frame.value?.contentWindow;
  const result = postToFrame(child, channel, payload);
  if (result.ok) {
    status.value = `已发送 ${channel}`;
    append(`host → h5 ${channel} ${JSON.stringify(payload)}`);
    return;
  }
  status.value = `未发送 ${result.reason}`;
  append(`host 拒绝 ${channel} ${result.reason}`);
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
  append(`host 拒绝样例 ${token.reason} / ${evalCh.reason}`);
}

function onMessage(event) {
  if (event.origin !== window.location.origin) {
    return;
  }
  const data = event.data;
  if (!data || data.source !== "nexus-h5") {
    return;
  }
  append(`h5 → host ${data.channel} ${JSON.stringify(data.payload || {})}`);
  if (data.channel === "login" && data.payload?.action === "ready") {
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
