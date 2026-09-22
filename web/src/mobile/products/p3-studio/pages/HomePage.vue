<template>
  <div>
    <div class="m-hero">
      <h2>你好，{{ session.displayName || "访客" }}</h2>
      <p>星构 H5 · 登录回跳 / 文件 / 路由。桌面台账、表单、待办页未改。</p>
    </div>
    <div class="m-card">
      <h3>会话与租户</h3>
      <p class="m-muted">账号 {{ session.email || "未登录" }}</p>
      <p class="m-muted" data-testid="mobile-context">context {{ session.contextId || "（无）" }}</p>
      <p class="m-muted">tenant 仅在会话中，不在 URL。</p>
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
      <p class="m-muted" data-testid="mobile-host-gap">
        {{ host.host }} · 受控通道 login/route/file/close 未在本切片验收（无 APP WebView / 小程序）。
      </p>
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
