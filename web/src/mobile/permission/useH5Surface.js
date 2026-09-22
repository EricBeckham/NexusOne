import { ref } from "vue";
import { perm } from "../../api/client";
import { session } from "../../api/session";
import { decide, formatDeny } from "./surfaceGuard";

export function useH5Surface() {
  const decision = ref(null);
  const error = ref("");

  async function requireCell(cell) {
    error.value = "";
    if (!session.contextId) {
      throw Object.assign(new Error("TenantContext is required"), {
        status: 403,
        code: "P2.PERM.0009",
      });
    }
    const next = await decide(perm.check, cell.resource, cell.action);
    decision.value = next;
    return next;
  }

  function capture(err) {
    error.value = formatDeny(err);
  }

  return { decision, error, requireCell, capture };
}
