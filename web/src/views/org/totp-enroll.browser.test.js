/**
 * @vitest-environment happy-dom
 *
 * G17 leftover: in-app TOTP enrollment UI / copy / error paths.
 * Not a Keycloak write, not a second MFA stack, not a G17 close.
 */
import { describe, expect, it } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { generate } from "../../auth/totp";
import TotpEnrollView from "./TotpEnrollView.vue";

const SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

async function mountEnroll(query = "") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p2/mfa/enroll", component: TotpEnrollView },
      { path: "/login", component: { template: "<div>login</div>" } },
    ],
  });
  await router.push(`/p2/mfa/enroll${query}`);
  await router.isReady();
  const wrapper = mount(TotpEnrollView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("TOTP enrollment page", () => {
  it("shows MFA_REQUIRED copy then format / wrong-code / success / already-enrolled", async () => {
    const wrapper = await mountEnroll(`?reason=mfa_required&secret=${SECRET}`);
    expect(wrapper.get("[data-testid=enroll-mfa-reason]").text()).toContain("P2.IAM.MFA_REQUIRED");
    expect(wrapper.get("[data-testid=enroll-mfa-reason]").text()).toContain("不重开 G18");

    await wrapper.get("[data-testid=totp-start]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=totp-secret]").text()).toContain("GEZD");
    expect(wrapper.get("[data-testid=totp-otpauth]").text()).toContain("otpauth://totp/");

    await wrapper.get("[data-testid=totp-code]").setValue("12");
    await wrapper.get("[data-testid=totp-confirm]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=totp-error]").text()).toContain("6 位数字");

    await wrapper.get("[data-testid=totp-code]").setValue("000000");
    await wrapper.get("[data-testid=totp-confirm]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=totp-error]").text()).toContain("不正确或已过期");

    await wrapper.get("[data-testid=totp-code]").setValue(generate(SECRET));
    await wrapper.get("[data-testid=totp-confirm]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=totp-success]").text()).toContain("本页确认成功");
    expect(wrapper.get("[data-testid=totp-success]").text()).toContain("没有签发恢复码");

    await wrapper.get("[data-testid=totp-code]").setValue(generate(SECRET));
    await wrapper.get("[data-testid=totp-confirm]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=totp-error]").text()).toContain("已在本页完成注册");
  });
});
