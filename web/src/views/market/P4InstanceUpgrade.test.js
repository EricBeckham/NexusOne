/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  apps: vi.fn(), skus: vi.fn(), versions: vi.fn(), subscriptions: vi.fn(), installations: vi.fn(),
  appInstanceUpgradeOffers: vi.fn(), upgradeAppInstance: vi.fn(), uninstallAppInstance: vi.fn(),
  products: vi.fn(), setProductAutoUpgrade: vi.fn(), commercialEntitlements: vi.fn(),
  session: { contextId: "ctx-1", tenantId: "tenant-1", platformMode: false, products: ["p4"] },
}));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: mocks.session }));

import P4ManagementView from "./P4ManagementView.vue";

async function page(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/installations", name: "p4-installations", component: P4ManagementView },
      { path: "/p4/packages", name: "p4-packages", component: P4ManagementView },
      { path: "/p4/market", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(P4ManagementView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("PLB8 instance upgrade", () => {
  beforeEach(() => {
    mocks.session.platformMode = false;
    mocks.apps.mockResolvedValue([{ id: "app-1", code: "leave", name: "请假", created_at: "2026-01-01T00:00:00Z" }]);
    mocks.skus.mockResolvedValue([]);
    mocks.versions.mockResolvedValue([{ id: "v1", app_id: "app-1", version: "1.0.0", status: "PUBLISHED" }]);
    mocks.subscriptions.mockResolvedValue([]);
    mocks.installations.mockResolvedValue([]);
    mocks.appInstanceUpgradeOffers.mockResolvedValue([{
      instance_id: "inst-1", app_code: "leave", current_version: "1.0.0",
      target_version_id: "v2", target_version: "1.1.0", minor: true,
    }]);
    mocks.upgradeAppInstance.mockResolvedValue({ instance: {}, role_pack: [{ grants_not_in_pack: ["fn.leave.apply:write:SELF"] }] });
    mocks.uninstallAppInstance.mockResolvedValue({});
    mocks.products.mockResolvedValue([{ id: "prod-1", code: "pl_leave", name: "请假", version: 2, auto_upgrade_minor: false }]);
    mocks.setProductAutoUpgrade.mockResolvedValue({});
  });

  it("shows the upgrade prompt and keeps dropped grants visible after upgrade", async () => {
    const wrapper = await page("/p4/installations");
    expect(wrapper.get("[data-testid='instance-upgrades']").text()).toContain("leave 1.0.0 → 1.1.0");
    expect(wrapper.get("[data-testid='instance-upgrades']").text()).toContain("同一主版本");
    await wrapper.get("[data-testid='instance-upgrade']").trigger("click");
    await flushPromises();
    expect(mocks.upgradeAppInstance).toHaveBeenCalledWith("inst-1", "v2");
    expect(wrapper.text()).toContain("新版本去掉的授权仍保留");
  });

  it("lets a platform operator turn on same-major automatic upgrade", async () => {
    mocks.session.platformMode = true;
    const wrapper = await page("/p4/packages");
    const box = wrapper.get("[data-testid='auto-upgrade-pl_leave']");
    await box.setValue(true);
    await flushPromises();
    expect(mocks.setProductAutoUpgrade).toHaveBeenCalledWith("prod-1", { version: 2, enabled: true });
    expect(wrapper.text()).toContain("已开启小版本自动升级");
  });
});
