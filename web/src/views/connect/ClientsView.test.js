/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ClientsView from "./ClientsView.vue";
import { p5 } from "../../api/client";

vi.mock("../../api/client", () => ({ p5: { clients: vi.fn(), client: vi.fn(), issueClient: vi.fn() } }));

async function open() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/p5/clients", name: "p5-clients", component: ClientsView },
    { path: "/p5/clients/:id", name: "p5-client", component: ClientsView },
  ] });
  await router.push("/p5/clients");
  const wrapper = mount(ClientsView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("API Client list visual states", () => {
  beforeEach(() => Object.values(p5).forEach((fn) => fn.mockReset()));

  it("uses compact rows and shared status tags without revealing a secret", async () => {
    p5.clients.mockResolvedValue([{ id: "c1", name: "同步客户端", key_prefix: "nk_abc", scopes: ["read"], active: true }, { id: "c2", name: "停用客户端", active: false }]);
    const wrapper = await open();
    expect(wrapper.get("[data-testid='clients-table']").classes()).toContain("nx-table--compact");
    expect(wrapper.findAll("[data-testid='nx-status-tag']").map((tag) => tag.text())).toEqual(["启用", "停用"]);
    expect(wrapper.text()).not.toContain("secret-value");
  });

  it("offers signing as the next step when there are no clients", async () => {
    p5.clients.mockResolvedValue([]);
    const wrapper = await open();
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("暂无 API Client");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("#issue-client");
  });
});
