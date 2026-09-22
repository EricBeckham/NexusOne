<template>
  <div>
    <div class="m-card">
      <h3>{{ meta.original_name || meta.fileName || fileId }}</h3>
      <p class="m-muted">file_id {{ fileId }}</p>
      <p class="m-muted">类型 {{ meta.content_type || meta.contentType || "—" }}</p>
      <p class="m-muted">大小 {{ formatSize(meta.size_bytes || meta.sizeBytes) }}</p>
      <p class="m-muted">状态 {{ meta.status || "—" }}</p>
      <p class="m-muted">所有者 {{ meta.owner_subject || meta.ownerSubject || "—" }}</p>
    </div>
    <div v-if="previewUrl" class="m-card">
      <img v-if="isImage" :src="previewUrl" alt="预览" style="max-width:100%;border-radius:8px;" />
      <p v-else class="m-muted">该类型在本页提供签名下载，不内嵌预览。</p>
    </div>
    <button class="m-btn primary" type="button" :disabled="busy" data-testid="file-download" @click="onDownload">
      {{ busy ? "签发中…" : "鉴权后下载" }}
    </button>
    <div style="height:8px" />
    <button class="m-btn ghost" type="button" data-testid="file-back-list" @click="router.push('/m/p3/files?tab=recent')">
      返回列表
    </button>
    <p v-if="error" class="m-error" data-testid="file-detail-error">{{ error }}</p>
    <p v-if="signed" class="m-ok" data-testid="file-signed">签名 URL 已签发（二次授权之后）</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { files } from "../../../../api/files";
import { session } from "../../../../api/session";
import { formatSize } from "../../../file/constraints";

const route = useRoute();
const router = useRouter();
const fileId = computed(() => String(route.params.id || ""));
const meta = ref({});
const error = ref("");
const busy = ref(false);
const signed = ref("");
const previewUrl = ref("");

const isImage = computed(() => {
  const mime = meta.value.content_type || meta.value.contentType || "";
  return mime.startsWith("image/");
});

onMounted(load);

async function load() {
  error.value = "";
  try {
    meta.value = await files.metadata(fileId.value);
  } catch (err) {
    error.value = `${err.code || "FILE"} ${err.message}`;
  }
}

async function onDownload() {
  error.value = "";
  busy.value = true;
  try {
    const body = await files.signDownload(fileId.value, session.personId || session.accountId || undefined);
    signed.value = body.url;
    previewUrl.value = body.url;
    if (body.url) {
      window.open(body.url, "_blank", "noopener");
    }
  } catch (err) {
    error.value = `${err.code || "FILE"} ${err.message}`;
  } finally {
    busy.value = false;
  }
}
</script>
