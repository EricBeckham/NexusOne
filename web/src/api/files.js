/**
 * H5 file client. Always File Service — never MinIO / AIStor admin APIs.
 * Tenant scope is X-Nexus-Context-Id only (ARCH-024). Never X-Tenant-Id.
 */
import { api } from "./client";

export const files = {
  upload: (body) => api("/api/v1/files", { method: "POST", body }),
  metadata: (fileId) => api(`/api/v1/files/${encodeURIComponent(fileId)}`),
  signDownload: (fileId, subject) =>
    api(`/api/v1/files/${encodeURIComponent(fileId)}/signed-url`, {
      method: "POST",
      body: subject ? { subject } : {},
    }),
  remove: (fileId) => api(`/api/v1/files/${encodeURIComponent(fileId)}`, { method: "DELETE" }),
};

export function readAsBase64(file, { signal, onProgress } = {}) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    const abort = () => {
      reader.abort();
      reject(Object.assign(new Error("upload cancelled"), { code: "FILE_CANCELLED" }));
    };
    if (signal) {
      if (signal.aborted) {
        abort();
        return;
      }
      signal.addEventListener("abort", abort, { once: true });
    }
    reader.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 70));
      }
    };
    reader.onerror = () => reject(new Error("failed to read file"));
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.readAsDataURL(file);
  });
}
