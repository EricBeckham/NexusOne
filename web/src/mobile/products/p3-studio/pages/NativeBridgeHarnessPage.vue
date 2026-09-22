<template>
  <div class="native-bridge-harness" :class="platformClass" data-testid="native-bridge-harness">
    <header class="native-bridge-banner">
      <p class="native-bridge-badge" data-testid="native-bridge-badge">原生桥契约桩 · 不是上架 APP</p>
      <h1>APP WebView 原生桥契约桩</h1>
      <p data-testid="native-bridge-honest">
        同一 F014 envelope（login/route/file/close）。iOS
        <code>webkit.messageHandlers.nexusHost</code> / Android
        <code>NexusNativeBridge</code> 形状桩。
        不是上架 IPA/APK，不是 #114 网页 <code>NexusAppWebView</code>，
        不是 #117 模拟小程序，不是 #132 微信钉钉生产验收。产品 G15 未关闭。
      </p>
      <p class="native-bridge-contract" data-testid="native-bridge-contract">
        host={{ contract.host }} · platform={{ contract.platform }}
        · storeReady={{ contract.storeReady }}
        · storePackage={{ contract.storePackage }}
        · nativeBinary={{ contract.nativeBinary }}
        · productionAccepted={{ contract.productionAccepted }}
        · stub={{ contract.stub }}
        · iosProject={{ contract.iosProject }}
        · androidProject={{ contract.androidProject }}
      </p>
      <p class="native-bridge-not-claimed" data-testid="native-bridge-not-claimed">
        不宣称：#114 {{ contract.notClaimed["#114"] }}；
        #117 {{ contract.notClaimed["#117"] }}；
        #132 {{ contract.notClaimed["#132"] }}。
      </p>
      <div class="native-bridge-platform-switch" data-testid="native-bridge-platform-switch">
        <button
          type="button"
          data-testid="native-bridge-pick-ios"
          :aria-pressed="platform === 'ios'"
          @click="pickPlatform('ios')"
        >
          iOS WK 桩
        </button>
        <button
          type="button"
          data-testid="native-bridge-pick-android"
          :aria-pressed="platform === 'android'"
          @click="pickPlatform('android')"
        >
          Android JS 桩
        </button>
      </div>
    </header>
    <div class="native-bridge-phone" data-testid="native-bridge-phone">
      <div class="native-bridge-status" data-testid="native-bridge-status">
        <span>9:41</span>
        <span data-testid="native-bridge-platform-label">{{ platformLabel }}</span>
        <span>stub</span>
      </div>
      <div class="native-bridge-nav" data-testid="native-bridge-nav">
        <span aria-hidden="true">〈</span>
        <span>星枢 H5 · 非上架包</span>
        <span data-testid="native-bridge-handler">{{ handlerLabel }}</span>
      </div>
      <iframe
        v-if="childSrcAllowed"
        :key="childKey"
        ref="frame"
        class="native-bridge-webview"
        title="NexusOne H5 native-bridge stub"
        data-testid="native-bridge-webview"
        :src="childSrc"
        @load="injectBridge"
      />
      <p v-else class="native-bridge-denied" data-testid="native-bridge-denied">web-view src 被源白名单拒绝</p>
    </div>
    <section class="native-bridge-console">
      <h2>原生桥桩控制台</h2>
      <div class="native-bridge-actions">
        <button type="button" data-testid="native-bridge-send-login" @click="send('login', { action: 'request', returnTo: '/m/host' })">
          入站 login
        </button>
        <button type="button" data-testid="native-bridge-send-route" @click="send('route', { action: 'navigate', path: '/m/host' })">
          入站 route
        </button>
        <button type="button" data-testid="native-bridge-send-file" @click="send('file', { action: 'open', fileId: 'demo-file-id' })">
          入站 file
        </button>
        <button type="button" data-testid="native-bridge-send-close" @click="send('close', { action: 'close' })">
          入站 close
        </button>
        <button type="button" data-testid="native-bridge-send-evil" @click="sendDenied">
          入站拒绝样例
        </button>
      </div>
      <p class="native-bridge-status-line" data-testid="native-bridge-parent-status">{{ status }}</p>
      <ol data-testid="native-bridge-parent-log">
        <li v-for="(item, index) in log" :key="index">{{ item }}</li>
      </ol>
      <p class="native-bridge-leftover" data-testid="native-bridge-leftover">
        仍开放：上架 IPA/APK / WKWebView·Android WebView 产品宿主 / 微信钉钉生产验收。产品 G15 未关闭。
      </p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { encodeHostMessage, HOST_SOURCE } from "../../../host/hostBridge";
import {
  attachNativeBridge,
  childSrcForNativeBridge,
  decodeNativeBridgeMessage,
  describeNativeBridgeContract,
  isAllowedNativeBridgeSrc,
  postToNativeBridgeChild,
} from "../../../host/nativeBridgeStub";
import "../../../styles/mobile.css";

const frame = ref(null);
const log = ref([]);
const status = ref("等待注入原生桥桩并握手 login/ready");
const platform = ref("ios");

const childSrc = computed(() => childSrcForNativeBridge());
const childSrcAllowed = computed(() => isAllowedNativeBridgeSrc(childSrc.value));
const childKey = computed(() => `${platform.value}:${childSrc.value}`);
const contract = computed(() => describeNativeBridgeContract(undefined, platform.value));
const platformClass = computed(() => (platform.value === "android" ? "native-bridge-android" : "native-bridge-ios"));
const platformLabel = computed(() => (platform.value === "android" ? "Android JS 桩" : "iOS WK 桩"));
const handlerLabel = computed(() => (
  platform.value === "android" ? "NexusNativeBridge" : "webkit.nexusHost"
));

function append(line) {
  log.value = [line, ...log.value].slice(0, 20);
}

function pickPlatform(next) {
  platform.value = next === "android" ? "android" : "ios";
  status.value = `已切换 ${platformLabel.value}，等待注入并握手`;
  append(`stub 切换 ${platform.value} src=${childSrc.value}`);
}

function injectBridge() {
  const child = frame.value?.contentWindow;
  if (!child) {
    return;
  }
  attachNativeBridge(child, platform.value, (data) => {
    if (!data || data.source !== "nexus-h5") {
      return;
    }
    append(`h5 → ${platform.value}-stub ${data.channel} ${JSON.stringify(data.payload || {})}`);
    if (data.channel === "login" && data.payload?.action === "ready") {
      status.value = "H5 已握手 login/ready（同一 F014 envelope；非上架包）";
    }
  });
  status.value = status.value.startsWith("H5 已握手")
    ? status.value
    : `已注入 ${handlerLabel.value}（原生桥桩，非上架包）`;
}

function send(channel, payload) {
  const child = frame.value?.contentWindow;
  const result = postToNativeBridgeChild(child, channel, payload);
  if (result.ok) {
    status.value = `已发送 ${channel}（source=nexus-host）`;
    append(`${platform.value}-stub → h5 ${channel} ${JSON.stringify(payload)}`);
    return;
  }
  status.value = `未发送 ${result.reason}`;
  append(`${platform.value}-stub 拒绝 ${channel} ${result.reason}`);
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
  status.value = `拒绝样例 ${token.reason} / ${evalCh.reason}`;
  append(`${platform.value}-stub 拒绝样例 ${token.reason} / ${evalCh.reason}`);
}

function onMessage(event) {
  const decoded = decodeNativeBridgeMessage(event);
  if (!decoded.ok) {
    return;
  }
  if (decoded.envelope.source !== "nexus-h5") {
    return;
  }
  append(`h5 → parent ${decoded.envelope.channel} ${JSON.stringify(decoded.envelope.payload || {})}`);
}

onMounted(() => {
  window.addEventListener("message", onMessage);
});

onUnmounted(() => {
  window.removeEventListener("message", onMessage);
});
</script>
