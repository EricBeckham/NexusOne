export function realCount(value) {
  return Array.isArray(value) ? value.length : null;
}

export function countTree(nodes) {
  if (!Array.isArray(nodes)) {
    return null;
  }
  let total = 0;
  for (const node of nodes) {
    total += 1;
    if (Array.isArray(node?.children) && node.children.length) {
      const nested = countTree(node.children);
      if (nested === null) {
        return null;
      }
      total += nested;
    }
  }
  return total;
}

export function recordName(value) {
  const name = value?.name || value?.display_name || value?.displayName;
  return typeof name === "string" && name.trim() ? name : null;
}
