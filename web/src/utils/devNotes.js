/**
 * Developer notes (API paths, source banners, JSON dumps, trace shortcuts) stay out of the
 * demo UI by default (O9 / S4).
 *
 * Opt-in (either):
 * - Build/serve with VITE_NEXUS_DEV_NOTES=true
 * - On a Vite DEV server only: localStorage.setItem("nexus.devNotes", "1") then reload
 *
 * Force off: VITE_NEXUS_DEV_NOTES=false (wins over localStorage). Production / non-DEV
 * builds never honor localStorage unless the env flag is explicitly true.
 */
export function devNotesEnabled(env = import.meta.env || {}, storage = safeStorage()) {
  const flag = String(env.VITE_NEXUS_DEV_NOTES ?? "").trim().toLowerCase();
  if (flag === "true" || flag === "1") return true;
  if (flag === "false" || flag === "0") return false;

  const mode = String(env.MODE || "").toLowerCase();
  if (mode === "production" || mode === "prod") return false;
  if (env.PROD === true || env.DEV === false) return false;
  if (!env.DEV) return false;

  try {
    return storage?.getItem("nexus.devNotes") === "1";
  } catch {
    return false;
  }
}

/** One-liner for docs / gallery — how to turn notes on. */
export const DEV_NOTES_SWITCH_HINT =
  "默认关闭。开启：VITE_NEXUS_DEV_NOTES=true，或 DEV 下 localStorage nexus.devNotes=1 后刷新。";

function safeStorage() {
  try {
    return globalThis.localStorage;
  } catch {
    return null;
  }
}
