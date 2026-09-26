import { describe, expect, it } from "vitest";
import { blockOf, parallelBlocks } from "./parallelScope";

const node = (id, type = "userTask") => ({ id, type, name: id });
const flow = (source, target) => ({ id: `${source}_${target}`, source_ref: source, target_ref: target });

describe("parallel block reading (ZA13)", () => {
  it("finds a fork/join block with its branches, including an empty and a longer branch", () => {
    const definition = {
      nodes: [node("s", "startEvent"), node("fork", "parallelGateway"), node("a1"), node("a2"), node("join", "parallelGateway"), node("e", "endEvent")],
      flows: [flow("s", "fork"), flow("fork", "a1"), flow("a1", "a2"), flow("a2", "join"), flow("fork", "join"), flow("join", "e")],
    };
    const [block] = parallelBlocks(definition);
    expect(block.fork).toBe("fork");
    expect(block.join).toBe("join");
    expect(block.branches).toEqual([["a1", "a2"], []]);
    expect(blockOf([block], "a2")).toBe(block);
    expect(blockOf([block], "s")).toBeNull();
  });

  it("returns no block for shapes the validator rejects", () => {
    const unjoined = {
      nodes: [node("fork", "parallelGateway"), node("a"), node("b"), node("e1", "endEvent"), node("e2", "endEvent")],
      flows: [flow("fork", "a"), flow("fork", "b"), flow("a", "e1"), flow("b", "e2")],
    };
    const nested = {
      nodes: [node("fork", "parallelGateway"), node("x", "exclusiveGateway"), node("a"), node("b"), node("c"), node("join", "parallelGateway")],
      flows: [flow("fork", "x"), flow("x", "a"), flow("x", "b"), flow("a", "join"), flow("b", "join"), flow("fork", "c"), flow("c", "join")],
    };
    const extraInput = {
      nodes: [node("x", "exclusiveGateway"), node("fork", "parallelGateway"), node("a"), node("b"), node("join", "parallelGateway")],
      flows: [flow("x", "fork"), flow("x", "join"), flow("fork", "a"), flow("fork", "b"), flow("a", "join"), flow("b", "join")],
    };
    for (const definition of [unjoined, nested, extraInput]) expect(parallelBlocks(definition)).toEqual([]);
    expect(parallelBlocks(null)).toEqual([]);
  });
});