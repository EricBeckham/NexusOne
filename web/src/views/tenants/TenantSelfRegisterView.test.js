/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ applyTenantRegistration: vi.fn() }));
vi.mock("../../api/client", () => ({ p1: mocks }));

import TenantSelfRegisterView from "./TenantSelfRegisterView.vue";

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/register", component: TenantSelfRegisterView },
      { path: "/login", component: { template: "<div>login</div>" } },
    ],
  });
  await router.push("/register");
  return mount(TenantSelfRegisterView, { global: { plugins: [router] } });
}

describe("TenantSelfRegisterView", () => {
  beforeEach(() => {
    mocks.applyTenantRegistration.mockReset();
  });

  it("validates and submits org + admin contact without payment copy", async () => {
    mocks.applyTenantRegistration.mockResolvedValue({ id: "r1", status: "PENDING" });
    const wrapper = await mountPage();
    expect(wrapper.text()).toContain("无支付");
    expect(wrapper.text()).toContain("租户自助注册");
    await wrapper.get('[data-testid="register-submit"]').trigger("submit");
    await flushPromises();
    expect(mocks.applyTenantRegistration).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="register-org-name"]').setValue("示例科技");
    await wrapper.get('[data-testid="register-admin-name"]').setValue("张三");
    await wrapper.get('[data-testid="register-admin-email"]').setValue("zhang@example.com");
    await wrapper.get('[data-testid="register-form"]').trigger("submit");
    await flushPromises();
    expect(mocks.applyTenantRegistration).toHaveBeenCalledWith({
      org_name: "示例科技",
      admin_display_name: "张三",
      admin_email: "zhang@example.com",
      contact_note: null,
    });
    expect(wrapper.get('[data-testid="register-result"]').text()).toContain("开通申请已提交");
  });

  it("shows S-shell error when apply fails", async () => {
    mocks.applyTenantRegistration.mockRejectedValue(Object.assign(new Error("邮箱已有待审订申请"), { traceId: "t-1" }));
    const wrapper = await mountPage();
    await wrapper.get('[data-testid="register-org-name"]').setValue("示例科技");
    await wrapper.get('[data-testid="register-admin-name"]').setValue("张三");
    await wrapper.get('[data-testid="register-admin-email"]').setValue("zhang@example.com");
    await wrapper.get('[data-testid="register-form"]').trigger("submit");
    await flushPromises();
    expect(wrapper.get('[data-testid="register-error"]').text()).toContain("邮箱已有待审订申请");
  });
});