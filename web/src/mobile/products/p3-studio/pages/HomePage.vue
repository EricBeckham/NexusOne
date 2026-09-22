<template>
  <div>
    <div class="m-hero">
      <h2>你好，{{ session.displayName || "访客" }}</h2>
      <p>星构 H5 运行入口 · 表单 / 列表 / 待办 / 查询 / 文件 / 通知跳转。桌面台账、表单、待办页未改。</p>
    </div>
    <div class="m-card">
      <h3>会话与租户</h3>
      <p class="m-muted">账号 {{ session.email || "未登录" }}</p>
      <p class="m-muted" data-testid="mobile-context">context {{ session.contextId || "（无）" }}</p>
      <p class="m-muted" data-testid="mobile-login-method">登录方式 {{ session.loginMethod || "（无）" }}</p>
      <p class="m-muted">tenant 仅在会话中，不在 URL。</p>
    </div>
    <div class="m-card">
      <h3>运行入口</h3>
      <p class="m-muted">已发布应用的移动浏览器运行态。设计器仍在桌面。</p>
      <router-link class="m-btn primary" to="/m/p3/forms" data-testid="mobile-open-forms">打开表单</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/p3/lists" data-testid="mobile-open-lists">打开列表</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/p3/todos?box=inbox" data-testid="mobile-open-todos">打开待办</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/p3/query" data-testid="mobile-open-query">打开查询</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/p3/notifications" data-testid="mobile-open-notify">打开通知跳转</router-link>
    </div>
    <div class="m-card">
      <h3>路由验收</h3>
      <p class="m-muted">刷新、深链、返回后应仍保持本页会话。</p>
      <button class="m-btn primary" type="button" data-testid="mobile-open-files" @click="goFiles">
        打开文件页（可带 query）
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="mobile-expire-session" @click="expireAndReturn">
        结束会话并回跳登录
      </button>
    </div>
    <div class="m-card">
      <h3>宿主消息</h3>
      <p class="m-muted" data-testid="mobile-host-caps">
        {{ host.host }} · postMessage · login/route/file/close 已在移动浏览器验收。
      </p>
      <p class="m-muted" data-testid="mobile-host-gap">
        仍开放：{{ host.otherHostsOpen.join(" / ") }}。
      </p>
      <router-link class="m-btn ghost" to="/m/host" data-testid="mobile-open-host">打开宿主消息页</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/host-harness" data-testid="mobile-open-harness">打开 iframe 宿主</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/mp-webview-harness" data-testid="mobile-open-mp-harness">打开模拟小程序 web-view</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/app-webview-harness" data-testid="mobile-open-app-webview">打开 APP WebView 模拟宿主</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/wx-dt-harness" data-testid="mobile-open-wx-dt">打开微信钉钉生产宿主契约桩</router-link>
      <div style="height:8px" />
      <router-link class="m-btn ghost" to="/m/native-bridge-harness" data-testid="mobile-open-native-bridge">打开原生桥契约桩</router-link>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { clearIdentity, session } from "../../../../api/session";
import { hostCapabilities } from "../../../host/hostBridge";

const router = useRouter();
const host = hostCapabilities();

function goFiles() {
  router.push({ path: "/m/p3/files", query: { tab: "upload" } });
}

function expireAndReturn() {
  clearIdentity();
  router.replace({ path: "/m/p3/files", query: { tab: "recent" } });
}
</script>
