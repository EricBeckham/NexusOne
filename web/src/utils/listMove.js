/** Returns a new array with `item` inserted at `index` (clamped). */
export function insertAt(list, index, item) {
  const copy = list.slice();
  copy.splice(Math.max(0, Math.min(index, copy.length)), 0, item);
  return copy;
}

/**
 * Moves the item at `from` so it lands in the gap `to` (0..length, the drop indicator position).
 * Dropping into the gap directly before or after the item itself is a no-op (returns `list`).
 */
export function moveToGap(list, from, to) {
  if (from < 0 || from >= list.length) return list;
  const target = to > from ? to - 1 : to;
  if (target === from) return list;
  const copy = list.slice();
  const [item] = copy.splice(from, 1);
  copy.splice(Math.max(0, Math.min(target, copy.length)), 0, item);
  return copy;
}
