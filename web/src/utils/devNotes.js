/**
 * Developer notes (API paths, source banners, trace shortcuts) stay out of the demo UI by default (O9).
 * Turn them on with VITE_NEXUS_DEV_NOTES=true at build/serve time, or in a dev server with
 * localStorage.setItem("nexus.devNotes", "1") and a reload. VITE_NEXUS_DEV_NOTES=false forces them off.
 */
export function devNotesEnabled(env = import.meta.env || {}, storage = safeStorage()) {
  if (env.VITE_NEXUS_DEV_NOTES === "true") return true;
  if (env.VITE_NEXUS_DEV_NOTES === "false" || !env.DEV) return false;
  try {
    return storage?.getItem("nexus.devNotes") === "1";
  } catch {
    return false;
  }
}

function safeStorage() {
  try {
    return globalThis.localStorage;
  } catch {
    return null;
  }
}