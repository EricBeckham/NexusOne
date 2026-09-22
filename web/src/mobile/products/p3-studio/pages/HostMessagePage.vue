<template>
  <div>
    <div class="m-hero">
      <h2>宿主消息 F014</h2>
      <p>受控通道 login / route / file / close。postMessage 同域契约。不是 APP WebView / 小程序。</p>
    </div>
    <div class="m-card">
      <h3>能力</h3>
      <p class="m-muted" data-testid="host-caps">
        {{ caps.host }} · {{ caps.transport }} · accepted={{ caps.accepted }} · fallback={{ caps.fallback || "none" }}
      </p>
      <p class="m-muted" data-testid="host-other-open">仍开放：{{ caps.otherHostsOpen.join(" / ") }}</p>
    </div>
    <div class="m-card">
      <h3>H5 → 宿主</h3>
      <p class="m-muted">嵌入时发给 parent；独立移动浏览器返回 no_host 并走 web fallback。</p>
      <button class="m-btn primary" type="button" data-testid="host-out-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
        发送 login
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="host-out-route" @click="send('route', { action: 'changed', path: '/m/host' })">
        发送 route
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="host-out-file" @click="send('file', { action: 'open', fileId: 'demo-file-id', fileName: 'note.txt' })">
        发送 file
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="host-out-close" @click="send('close', { action: 'close' })">
        发送 close
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="host-out-denied" @click="sendDenied">
        演示拒绝 eval / token
      </button>
    </div>
    <div class="m-card">
      <h3>最近消息</h3>
      <p class="m-muted" data-testid="host-last-out">
        出站 {{ lastOutText }}
      </p>
      <p class="m-muted" data-testid="host-last-in">
        入站 {{ lastInText }}
      </p>
      <p v-if="denied" class="m-ok" data-testid="host-denied">{{ denied }}</p>
      <p v-if="note" class="m-ok" data-testid="host-note">{{ note }}</p>
    </div>
    <div class="m-card">
      <h3>验收宿主</h3>
      <p class="m-muted">移动浏览器 iframe 父页走同一契约。打开后向本页 postMessage。</p>
      <router-link class="m-btn ghost" to="/m/host-harness" data-testid="host-open-harness">打开 iframe 宿主页</router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { hostCapabilities, postHost } from "../../../host/hostBridge";
import { useHostPort } from "../../../host/useHostPort";

const { log, pullLog } = useHostPort();
const caps = hostCapabilities();
const note = ref("");
const denied = ref("");

const lastOutText = computed(() => formatEntry(log.lastOut));
const lastInText = computed(() => formatEntry(log.lastIn));

function formatEntry(entry) {
  if (!entry) {
    return "（无）";
  }
  return `${entry.channel} ${JSON.stringify(entry.payload)}`;
}

function send(channel, payload) {
  denied.value = "";
  const result = postHost(channel, payload);
  pullLog();
  if (result.ok) {
    note.value = `已 postMessage ${channel}`;
    return;
  }
  if (result.reason === "no_host") {
    note.value = `无宿主，web fallback（${channel}）`;
    return;
  }
  note.value = `拒绝 ${result.reason}`;
}

function sendDenied() {
  const evalDenied = postHost("eval", {});
  const tokenDenied = postHost("login", { action: "done", access_token: "leak" });
  pullLog();
  denied.value = `${evalDenied.reason} / ${tokenDenied.reason}`;
  note.value = "";
}
</script>
