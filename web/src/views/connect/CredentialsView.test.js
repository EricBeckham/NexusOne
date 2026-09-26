/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p5 } from "../../api/client";
import CredentialsView from "./CredentialsView.vue";

vi.mock("../../api/client", () => ({ p5: {
  credentials: vi.fn(),
  credential: vi.fn(),
  createCredential: vi.fn(),
  updateCredential: vi.fn(),
  credentialSecretStatus: vi.fn(),
  writeCredentialSecret: vi.fn(),
  rotateCredentialSecret: vi.fn(),
  revokeCredentialSecret: vi.fn(),
  probeCredentialSecret: vi.fn(),
} }));

const row = {
  id: "cred-1",
  name: "仓库只读",
  provider: "minio",
  status: "ACTIVE",
  last_rotated_at: "2026-09-24T08:00:00Z",
  expires_at: null,
  secret_name: "p5.credential.tenant.cred-1",
  secret: "must-not-render",
};

async function open(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p5/credentials", name: "p5-credentials", component: CredentialsView },
      { path: "/p5/credentials/:id", name: "p5-credential", component: CredentialsView },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(CredentialsView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P5 credential page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    p5.credentials.mockResolvedValue([row]);
    p5.credential.mockResolvedValue(row);
    p5.credentialSecretStatus.mockResolvedValue({ configured: true, version: 2, status: "ACTIVE" });
  });

  it("lists metadata and never renders a secret value", async () => {
    const wrapper = await open("/p5/credentials");
    expect(wrapper.text()).toContain("仓库只读");
    expect(wrapper.text()).toContain("minio");
    expect(wrapper.text()).toContain("p5.credential.tenant.cred-1");
    expect(wrapper.text()).toContain("有效");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).not.toContain("must-not-render");
    expect(wrapper.text()).not.toContain("开放平台");
    expect(wrapper.get("[data-testid='credentials-table']").classes()).toContain("nx-table--compact");
  });

  it("shows 暂无 when the tenant has no credentials", async () => {
    p5.credentials.mockResolvedValue([]);
    const wrapper = await open("/p5/credentials");
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("暂无凭证");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("#create-credential");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("rotates from the detail page and clears the secret field", async () => {
    const wrapper = await open("/p5/credentials/cred-1");
    expect(wrapper.findAll(".actions button").map((button) => button.classes())).toEqual([expect.arrayContaining(["ghost"]), expect.arrayContaining(["danger"])]);
    const input = wrapper.get('input[type="password"]');
    await input.setValue("rotated-secret-value");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(p5.rotateCredentialSecret).toHaveBeenCalledWith("cred-1", "rotated-secret-value");
    expect(p5.writeCredentialSecret).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("已提交轮转");
    expect(wrapper.text()).not.toContain("rotated-secret-value");
    expect(wrapper.get('input[type="password"]').element.value).toBe("");
  });

  it("probes and revokes without echoing a secret", async () => {
    p5.probeCredentialSecret.mockResolvedValue({ status: "SUCCEEDED" });
    p5.revokeCredentialSecret.mockResolvedValue({ configured: false, status: "REVOKED" });
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    const wrapper = await open("/p5/credentials/cred-1");

    await wrapper.findAll("button").find((button) => button.text() === "测试").trigger("click");
    await flushPromises();
    expect(p5.probeCredentialSecret).toHaveBeenCalledWith("cred-1");
    expect(wrapper.text()).toContain("密钥可读");
    expect(wrapper.text()).not.toContain("must-not-render");

    await wrapper.findAll("button").find((button) => button.text() === "吊销").trigger("click");
    await flushPromises();
    expect(p5.revokeCredentialSecret).toHaveBeenCalledWith("cred-1");
  });
});
