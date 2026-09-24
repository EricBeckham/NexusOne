import { p1Nav } from "./p1";
import { p2Nav } from "./p2";
import { p3Nav } from "./p3";
import { p4Nav } from "./p4";
import { p5Nav } from "./p5";

export const NAV_BY_PRODUCT = {
  p1: p1Nav,
  p2: p2Nav,
  p3: p3Nav,
  p4: p4Nav,
  p5: p5Nav,
};

export function navFor(productId) {
  return NAV_BY_PRODUCT[productId] || [];
}

export function menuDepth(items, level = 1) {
  if (!items?.length) {
    return 0;
  }
  let depth = level;
  for (const item of items) {
    if (item.children?.length) {
      depth = Math.max(depth, menuDepth(item.children, level + 1));
    }
  }
  return depth;
}

export function collectLinks(items, into = []) {
  for (const item of items || []) {
    if (item.to) {
      into.push(item.to);
    }
    if (item.children?.length) {
      collectLinks(item.children, into);
    }
  }
  return into;
}
