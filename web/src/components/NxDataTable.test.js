/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxDataTable from "./NxDataTable.vue";

describe("NxDataTable", () => {
  it("renders header + body rows with default density on .nx-table (§12.1)", () => {
    const wrapper = mount(NxDataTable, {
      slots: {
        header: "<tr><th>名称</th><th>状态</th></tr>",
        default: "<tr><td>请假</td><td>有效</td></tr>",
      },
    });

    expect(wrapper.attributes("data-density")).toBe("default");
    const table = wrapper.get("[data-testid='nx-data-table']");
    expect(table.classes()).toContain("nx-table");
    expect(table.classes()).not.toContain("nx-table--compact");
    expect(table.text()).toContain("请假");
    expect(wrapper.find("[data-testid='nx-data-table-loading']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='nx-data-table-empty']").exists()).toBe(false);
  });

  it("applies compact and large density classes", () => {
    const compact = mount(NxDataTable, {
      props: { density: "compact" },
      slots: { default: "<tr><td>a</td></tr>" },
    });
    expect(compact.get("[data-testid='nx-data-table']").classes()).toContain("nx-table--compact");
    expect(compact.attributes("data-density")).toBe("compact");

    const large = mount(NxDataTable, {
      props: { density: "large" },
      slots: { default: "<tr><td>a</td></tr>" },
    });
    expect(large.get("[data-testid='nx-data-table']").classes()).toContain("nx-table--large");
  });

  it("shows skeleton rows while loading and hides the table", () => {
    const wrapper = mount(NxDataTable, {
      props: { loading: true, skeletonCount: 2, loadingLabel: "正在加载列表" },
      slots: { default: "<tr><td>hidden</td></tr>" },
    });

    expect(wrapper.get("[data-testid='nx-data-table-loading']").exists()).toBe(true);
    expect(wrapper.findAll("[data-testid='nx-skeleton']")).toHaveLength(2);
    expect(wrapper.text()).toContain("正在加载列表");
    expect(wrapper.find("[data-testid='nx-data-table']").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("hidden");
  });

  it("shows empty state with next-step slot (§12.5)", () => {
    const wrapper = mount(NxDataTable, {
      props: {
        empty: true,
        emptyTitle: "还没有开通权益",
        emptyDescription: "在应用目录开通后，权益会显示在这里。",
      },
      slots: {
        "empty-next": "<a class='primary' href='/p4/market'>前往应用目录</a>",
        default: "<tr><td>hidden</td></tr>",
      },
    });

    expect(wrapper.get("[data-testid='nx-data-table-empty']").text()).toContain("还没有开通权益");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("/p4/market");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("shows error state with retry and prefers error over empty/loading content", async () => {
    const wrapper = mount(NxDataTable, {
      props: {
        error: "目录暂时无法加载",
        traceId: "trace-42",
        empty: true,
        loading: true,
      },
    });

    expect(wrapper.get("[data-testid='nx-data-table-error']").text()).toContain("目录暂时无法加载");
    expect(wrapper.text()).toContain("Trace ID: trace-42");
    expect(wrapper.find("[data-testid='nx-data-table-loading']").exists()).toBe(false);
    expect(wrapper.find("[data-testid='nx-data-table-empty']").exists()).toBe(false);

    await wrapper.get("button").trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
  });

  it("accepts object error title/description", () => {
    const wrapper = mount(NxDataTable, {
      props: { error: { title: "星市数据暂时无法加载", description: "请稍后重试" } },
    });
    expect(wrapper.text()).toContain("星市数据暂时无法加载");
    expect(wrapper.text()).toContain("请稍后重试");
  });

  it("renders an optional footer slot under the table", () => {
    const wrapper = mount(NxDataTable, {
      slots: {
        default: "<tr><td>row</td></tr>",
        footer: "<nav data-testid='page-footer'>共 1 条</nav>",
      },
    });
    expect(wrapper.get("[data-testid='nx-data-table-footer']").text()).toContain("共 1 条");
  });
});
