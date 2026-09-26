import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const components = [
  "NxPageHeader.vue",
  "NxKpiRow.vue",
  "NxPanelCard.vue",
  "NxChartPlaceholder.vue",
  "NxHealthList.vue",
  "NxActivityTimeline.vue",
  "NxTodoList.vue",
  "NxEmpty.vue",
  "NxErrorState.vue",
  "NxNoPermission.vue",
  "NxStatCard.vue",
  "NxStatusTag.vue",
  "NxSkeleton.vue",
  "NxListFooter.vue",
  "NxToastHost.vue",
];

describe("W1 component design tokens", () => {
  it.each(components)("does not hard-code a color in %s", (file) => {
    const source = readFileSync(new URL(file, import.meta.url), "utf8");
    expect(source).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(|hsla?\(/i);
  });
});
