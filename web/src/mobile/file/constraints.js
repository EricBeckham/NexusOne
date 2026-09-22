/** Mirrors nexus-file-core FileProperties / FileService defaults. */
export const FILE_MAX_SIZE_BYTES = 20 * 1024 * 1024;
export const FILE_ALLOWED_MIME = [
  "application/octet-stream",
  "application/pdf",
  "image/png",
  "image/jpeg",
  "text/plain",
  "text/csv",
];
export const FILE_ACCEPT = "image/png,image/jpeg,application/pdf,text/plain,text/csv";
export const RECENT_FILES_KEY = "nexus.g15.recent-files";

const EXT_MIME = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  pdf: "application/pdf",
  txt: "text/plain",
  csv: "text/csv",
};

export function inferMime(file) {
  if (file?.type) {
    return file.type.toLowerCase();
  }
  const name = file?.name || "";
  const ext = name.includes(".") ? name.split(".").pop().toLowerCase() : "";
  return EXT_MIME[ext] || "application/octet-stream";
}

export function validateUploadFile(file) {
  if (!file) {
    return { ok: false, code: "FILE_REQUIRED", message: "请选择文件" };
  }
  if (file.size > FILE_MAX_SIZE_BYTES) {
    return {
      ok: false,
      code: "FILE_TOO_LARGE",
      message: `文件超过大小限制（${Math.round(FILE_MAX_SIZE_BYTES / (1024 * 1024))} MB）`,
    };
  }
  const mime = inferMime(file);
  if (!FILE_ALLOWED_MIME.includes(mime)) {
    return {
      ok: false,
      code: "FILE_TYPE",
      message: `类型不允许：${mime}`,
    };
  }
  return { ok: true, mime };
}

export function formatSize(bytes) {
  if (bytes == null || Number.isNaN(Number(bytes))) {
    return "—";
  }
  const n = Number(bytes);
  if (n < 1024) {
    return `${n} B`;
  }
  if (n < 1024 * 1024) {
    return `${(n / 1024).toFixed(1)} KB`;
  }
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

export function loadRecentFiles(tenantId) {
  try {
    const all = JSON.parse(sessionStorage.getItem(RECENT_FILES_KEY) || "[]");
    if (!tenantId) {
      return all;
    }
    return all.filter((item) => item.tenantId === tenantId);
  } catch {
    return [];
  }
}

export function rememberRecentFile(entry) {
  const next = {
    fileId: entry.fileId,
    fileName: entry.fileName,
    contentType: entry.contentType,
    sizeBytes: entry.sizeBytes,
    tenantId: entry.tenantId || "",
    savedAt: Date.now(),
  };
  const all = loadRecentFiles();
  const merged = [next, ...all.filter((item) => item.fileId !== next.fileId)].slice(0, 20);
  sessionStorage.setItem(RECENT_FILES_KEY, JSON.stringify(merged));
  return merged;
}
