/**
 * ZA13: read-only understanding of parallel blocks, mirroring the ZA11 validator's supported shape:
 * a fork (parallel gateway, one in / several out) whose every branch is a straight chain of single-exit
 * nodes reaching the same join (parallel gateway, several in / one out). Shapes the validator rejects
 * produce no block here — the server's errors explain them.
 */
export function parallelBlocks(definition) {
  const nodes = Array.isArray(definition?.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition?.flows) ? definition.flows : [];
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const out = (id) => flows.filter((flow) => flow.source_ref === id).map((flow) => flow.target_ref);
  const incoming = (id) => flows.filter((flow) => flow.target_ref === id).length;
  const isJoin = (id) => byId.get(id)?.type === "parallelGateway" && incoming(id) > 1 && out(id).length === 1;

  const blocks = [];
  for (const fork of nodes) {
    if (fork.type !== "parallelGateway" || incoming(fork.id) > 1 || out(fork.id).length < 2) continue;
    let join = null;
    const branches = [];
    let ok = true;
    for (const first of out(fork.id)) {
      const chain = [];
      const seen = new Set();
      let cursor = first;
      while (cursor && byId.has(cursor) && !isJoin(cursor)) {
        if (seen.has(cursor) || incoming(cursor) > 1 || out(cursor).length !== 1) {
          ok = false;
          break;
        }
        seen.add(cursor);
        chain.push(cursor);
        cursor = out(cursor)[0];
      }
      if (!ok || !cursor || !isJoin(cursor) || (join && join !== cursor)) {
        ok = false;
        break;
      }
      join = cursor;
      branches.push(chain);
    }
    if (!ok || !join || incoming(join) !== out(fork.id).length) continue;
    blocks.push({ fork: fork.id, join, branches, members: new Set([fork.id, join, ...branches.flat()]) });
  }
  return blocks;
}

/** The block a node belongs to (fork, join or a branch node), or null. */
export function blockOf(blocks, id) {
  return blocks.find((block) => block.members.has(id)) || null;
}