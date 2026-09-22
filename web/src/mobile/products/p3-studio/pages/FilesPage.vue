<template>
  <div>
    <div class="m-tabs">
      <button type="button" :class="{ active: tab === 'upload' }" data-testid="files-tab-upload" @click="setTab('upload')">
        上传
      </button>
      <button type="button" :class="{ active: tab === 'recent' }" data-testid="files-tab-recent" @click="setTab('recent')">
        最近
      </button>
    </div>
    <div v-if="tab === 'upload'" class="m-card">
      <h3>H5 文件上传</h3>
      <p class="m-muted">走 File Service。鉴权、20MB、白名单 MIME。不直连 MinIO。</p>
      <div class="m-field">
        <label>业务类型</label>
        <input v-model="businessType" data-testid="file-business-type" />
      </div>
      <div class="m-field">
        <label>选择文件</label>
        <input
          ref="picker"
          type="file"
          :accept="FILE_ACCEPT"
          data-testid="file-input"
          @change="onPick"
        />
      </div>
      <p v-if="pickedName" class="m-muted">{{ pickedName }} · {{ formatSize(pickedSize) }} · {{ pickedMime }}</p>
      <div v-if="progress > 0" class="m-progress" data-testid="file-progress">
        <span :style="{ width: progress + '%' }" />
      </div>
      <button class="m-btn primary" type="button" :disabled="busy || !picked" data-testid="file-upload" @click="onUpload">
        {{ busy ? "上传中…" : "上传" }}
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" :disabled="!busy" data-testid="file-cancel" @click="onCancel">
        取消
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="file-demo-oversize" @click="demoOversize">
        演示超限 21MB
      </button>
      <div style="height:8px" />
      <button class="m-btn ghost" type="button" data-testid="file-demo-mime" @click="demoMime">
        演示非法类型
      </button>
    </div>
    <div v-else class="m-card">
      <h3>最近文件</h3>
      <p v-if="!recents.length" class="m-muted">当前租户会话还没有本机记录的 file_id。</p>
      <router-link
        v-for="item in recents"
        :key="item.fileId"
        class="m-file-row"
        :to="`/m/p3/files/${item.fileId}`"
      >
        <span>
          <strong>{{ item.fileName }}</strong>
          <div class="m-muted">{{ item.contentType }} · {{ formatSize(item.sizeBytes) }}</div>
        </span>
        <span class="m-muted">详情</span>
      </router-link>
      <div class="m-field" style="margin-top:12px;">
        <label>按 file_id 深链</label>
        <input v-model="lookupId" placeholder="uuid" data-testid="file-lookup-id" />
      </div>
      <button class="m-btn ghost" type="button" data-testid="file-lookup" @click="openLookup">打开</button>
    </div>
    <p v-if="error" class="m-error" data-testid="file-error">{{ error }}</p>
    <p v-if="ok" class="m-ok" data-testid="file-ok">{{ ok }}</p>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { files, readAsBase64 } from "../../../../api/files";
import { session } from "../../../../api/session";
import {
  FILE_ACCEPT,
  FILE_MAX_SIZE_BYTES,
  formatSize,
  inferMime,
  loadRecentFiles,
  rememberRecentFile,
  validateUploadFile,
} from "../../../file/constraints";

const route = useRoute();
const router = useRouter();
const tab = computed(() => (route.query.tab === "recent" ? "recent" : "upload"));
const businessType = ref("ledger");
const picker = ref(null);
const picked = ref(null);
const pickedName = ref("");
const pickedSize = ref(0);
const pickedMime = ref("");
const busy = ref(false);
const progress = ref(0);
const error = ref("");
const ok = ref("");
const lookupId = ref("");
const abortRef = ref(null);
const recents = computed(() => loadRecentFiles(session.tenantId));

function setTab(next) {
  router.replace({ path: "/m/p3/files", query: { tab: next } });
}

function onPick(event) {
  error.value = "";
  ok.value = "";
  const file = event.target.files?.[0];
  const check = validateUploadFile(file);
  if (!check.ok) {
    picked.value = null;
    pickedName.value = file?.name || "";
    pickedSize.value = file?.size || 0;
    pickedMime.value = inferMime(file);
    error.value = check.message;
    return;
  }
  picked.value = file;
  pickedName.value = file.name;
  pickedSize.value = file.size;
  pickedMime.value = check.mime;
}

async function onUpload() {
  error.value = "";
  ok.value = "";
  const check = validateUploadFile(picked.value);
  if (!check.ok) {
    error.value = check.message;
    return;
  }
  if (!session.accessToken) {
    error.value = "未登录，无法上传";
    return;
  }
  busy.value = true;
  progress.value = 5;
  const controller = new AbortController();
  abortRef.value = controller;
  try {
    const contentBase64 = await readAsBase64(picked.value, {
      signal: controller.signal,
      onProgress: (n) => {
        progress.value = n;
      },
    });
    progress.value = 80;
    const body = await files.upload({
      business_type: businessType.value || "ledger",
      file_name: picked.value.name,
      content_type: check.mime,
      content_base64: contentBase64,
      resource_id: "h5-mobile",
    });
    progress.value = 100;
    const fileId = body.file_id || body.fileId;
    rememberRecentFile({
      fileId,
      fileName: picked.value.name,
      contentType: check.mime,
      sizeBytes: picked.value.size,
      tenantId: body.tenant_id || session.tenantId,
    });
    ok.value = `已上传 ${fileId}`;
    await router.push(`/m/p3/files/${fileId}`);
  } catch (err) {
    if (err.code === "FILE_CANCELLED") {
      error.value = "已取消";
    } else {
      error.value = `${err.code || "FILE"} ${err.message}`;
    }
  } finally {
    busy.value = false;
    abortRef.value = null;
  }
}

function onCancel() {
  abortRef.value?.abort();
}

function demoOversize() {
  const check = validateUploadFile({
    name: "huge.txt",
    size: FILE_MAX_SIZE_BYTES + 1,
    type: "text/plain",
  });
  error.value = check.message;
  ok.value = "";
}

function demoMime() {
  const check = validateUploadFile({
    name: "payload.exe",
    size: 12,
    type: "application/x-msdownload",
  });
  error.value = check.message;
  ok.value = "";
}

function openLookup() {
  const id = lookupId.value.trim();
  if (!id) {
    error.value = "需要 file_id";
    return;
  }
  router.push(`/m/p3/files/${id}`);
}
</script>
