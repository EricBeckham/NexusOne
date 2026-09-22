/**
 * @vitest-environment happy-dom
 *
 * G17 leftover: email MFA request contract. Honest SKIP without SMTP.
 */
import { describe, expect, it } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import EmailMfaView from "./EmailMfaView.vue";

async function mountEmail(query = "") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p2/mfa/email", component: EmailMfaView },
      { path: "/p2/mfa/recovery", component: { template: "<div>recovery</div>" } },
      { path: "/p2/mfa/enroll", component: { template: "<div>enroll</div>" } },
    ],
  });
  await router.push(`/p2/mfa/email${query}`);
  await router.isReady();
  const wrapper = mount(EmailMfaView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("email MFA leftover page", () => {
  it("keeps honest SKIP when SMTP is off", async () => {
    const wrapper = await mountEmail();
    expect(wrapper.get("[data-testid=email-mfa-reason]").text()).toContain("不重开 G18");
    await wrapper.get("[data-testid=email-mfa-request]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=email-mfa-skip]").text()).toContain("P2.IAM.SMTP_DISABLED");
    expect(wrapper.get("[data-testid=email-mfa-skip]").text()).toContain("SKIP");
  });

  it("rejects a blank email as an error, not a delivery PASS", async () => {
    const wrapper = await mountEmail();
    await wrapper.get("[data-testid=email-mfa-address]").setValue("");
    await wrapper.get("[data-testid=email-mfa-request]").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid=email-mfa-error]").text()).toContain("请输入管理员邮箱");
  });
});
