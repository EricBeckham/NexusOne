/**
 * @vitest-environment happy-dom
 *
 * G17 leftover: in-app recovery codes after TOTP leftover.
 * Not a Keycloak write, not a second MFA stack, not a G17 close.
 */
import { describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import RecoveryCodesView from "./RecoveryCodesView.vue";

async function mountRecovery() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p2/mfa/recovery", component: RecoveryCodesView },
      { path: "/p2/mfa/enroll", component: { template: "<div>enroll</div>" } },
      { path: "/p2/mfa/email", component: { template: "<div>email</div>" } },
    ],
  });
  await router.push("/p2/mfa/recovery");
  await router.isReady();
  const wrapper = mount(RecoveryCodesView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("recovery codes leftover page", () => {
  it("issues codes then format / wrong / success / already-used", async () => {
    const wrapper = await mountRecovery();
    expect(wrapper.get("[data-testid=recovery-reason]").text()).toContain("不重开 G18");
    expect(wrapper.get("[data-testid=recovery-reason]").text()).toContain("不写回 Keycloak");

    await wrapper.get("[data-testid=recovery-issue]").trigger("click");
    await flushPromises();
    const first = wrapper.get("[data-testid=recovery-list]").find("li").text().trim();
    expect(first).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);

    await wrapper.get("[data-testid=recovery-code]").setValue("12");
    await wrapper.get("[data-testid=recovery-consume]").trigger("click");
    await vi.waitFor(() => {
      expect(wrapper.get("[data-testid=recovery-error]").text()).toContain("8 位");
    });

    await wrapper.get("[data-testid=recovery-code]").setValue("ZZZZ-ZZZZ");
    await wrapper.get("[data-testid=recovery-consume]").trigger("click");
    await vi.waitFor(() => {
      expect(wrapper.get("[data-testid=recovery-error]").text()).toContain("不正确");
    });

    await wrapper.get("[data-testid=recovery-code]").setValue(first);
    await wrapper.get("[data-testid=recovery-consume]").trigger("click");
    await vi.waitFor(() => {
      expect(wrapper.get("[data-testid=recovery-success]").text()).toContain("已核销");
      expect(wrapper.get("[data-testid=recovery-success]").text()).toContain("没有把恢复码写入 Keycloak");
    });

    await wrapper.get("[data-testid=recovery-code]").setValue(first);
    await wrapper.get("[data-testid=recovery-consume]").trigger("click");
    await vi.waitFor(() => {
      expect(wrapper.get("[data-testid=recovery-error]").text()).toContain("已经核销");
    });
  });
});
