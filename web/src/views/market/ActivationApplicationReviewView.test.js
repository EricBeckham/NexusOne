/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { useNxToastState } from "../../feedback/nxMessage";

const mocks = vi.hoisted(() => ({
  activationApplications: vi.fn(),
  approveActivation: vi.fn(),
  rejectActivation: vi.fn(),
}));
vi.mock("../../api/client", () => ({ p4: mocks }));

import ActivationApplicationReviewView from "./ActivationApplicationReviewView.vue";

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/activation-applications", component: ActivationApplicationReviewView },
      { path: "/p4/market", component: { template: "<div>market</div>" } },
    ],
  });
  await router.push("/p4/activation-applications");
  const wrapper = mount(ActivationApplicationReviewView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("ActivationApplicationReviewView", () => {
  beforeEach(() => {
    mocks.activationApplications.mockReset();
    mocks.approveActivation.mockReset();
    mocks.rejectActivation.mockReset();
  });

  it("lists pending applications and approves one", async () => {
    mocks.activationApplications.mockResolvedValue([
      {
        id: "a1",
        tenant_id: "11111111-1111-1111-1111-111111111111",
        sku_code: "demo.simple-approval",
        version_id: "22222222-2222-2222-2222-222222222222",
        status: "PENDING",
        created_at: "2026-09-26T01:00:00Z",
      },
    ]);
    mocks.approveActivation.mockResolvedValue({
      id: "a1",
      sku_code: "demo.simple-approval",
      status: "APPROVED",
      resulting_installation_id: "inst-1",
    });
    const wrapper = await mountPage();
    expect(wrapper.text()).toContain("无支付");
    expect(wrapper.get('[data-testid="activation-row-a1"]').text()).toContain("demo.simple-approval");
    await wrapper.get('[data-testid="activation-row-a1"] .text-action').trigger("click");
    await wrapper.get('[data-testid="activation-approve"]').trigger("click");
    await flushPromises();
    expect(mocks.approveActivation).toHaveBeenCalledWith("a1", null);
    expect(useNxToastState().items.at(-1).message).toContain("已通过开通");
  });

  it("shows empty S-shell when no applications", async () => {
    mocks.activationApplications.mockResolvedValue([]);
    const wrapper = await mountPage();
    expect(wrapper.get('[data-testid="nx-data-table-empty"]').text()).toContain("暂无开通申请");
  });
});