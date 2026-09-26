/**
 * ZA2: thin edge + grid snap for free-canvas field placement.
 * Pure functions only — does not own drag/drop UI or guide lines.
 * Units match fieldLayout grid cells (integers ≥ 0).
 */

import { normalizeLayout } from "./fieldLayout";

export const DEFAULT_SNAP = Object.freeze({
  gridX: 1,
  gridY: 1,
  threshold: 1,
});

function toInt(value, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.trunc(n);
}

function clampNonNeg(n) {
  return Math.max(0, n);
}

/**
 * Round `{x,y}` onto a grid. Step < 1 is treated as 1.
 * Does not change w/h.
 */
export function snapToGrid(point, grid = DEFAULT_SNAP) {
  const rawX = Number(point?.x);
  const rawY = Number(point?.y);
  const x = Number.isFinite(rawX) ? rawX : 0;
  const y = Number.isFinite(rawY) ? rawY : 0;
  const stepX = Math.max(1, toInt(grid?.gridX, DEFAULT_SNAP.gridX));
  const stepY = Math.max(1, toInt(grid?.gridY, DEFAULT_SNAP.gridY));
  return {
    x: clampNonNeg(Math.round(x / stepX) * stepX),
    y: clampNonNeg(Math.round(y / stepY) * stepY),
  };
}

/**
 * Edges of a layout box as absolute cell coordinates.
 * left=x, right=x+w, top=y, bottom=y+h.
 */
export function boxEdges(layout) {
  const box = normalizeLayout(layout);
  return {
    left: box.x,
    right: box.x + box.w,
    top: box.y,
    bottom: box.y + box.h,
  };
}

/**
 * Snap moving box origin `{x,y}` to sibling edges when within threshold.
 * Compares left/right of moving box to left/right of siblings, and top/bottom likewise.
 * Returns `{ x, y, hit }` where hit lists matched axes (`left`|`right`|`top`|`bottom`) or [].
 * Outside threshold → original (integer) coords, hit=[].
 */
export function snapToEdges(point, size, siblings = [], threshold = DEFAULT_SNAP.threshold) {
  const thr = Math.max(0, toInt(threshold, DEFAULT_SNAP.threshold));
  let x = clampNonNeg(toInt(point?.x, 0));
  let y = clampNonNeg(toInt(point?.y, 0));
  const w = Math.max(1, toInt(size?.w, 4));
  const h = Math.max(1, toInt(size?.h, 1));
  const hit = [];

  const movingLeft = x;
  const movingRight = x + w;
  const movingTop = y;
  const movingBottom = y + h;

  let bestDx = thr + 1;
  let snappedX = x;
  let xHit = null;
  let bestDy = thr + 1;
  let snappedY = y;
  let yHit = null;

  for (const sibling of siblings) {
    if (!sibling) continue;
    const edge = boxEdges(sibling.layout || sibling);

    for (const target of [edge.left, edge.right]) {
      const dx = Math.abs(movingLeft - target);
      if (dx <= thr && dx < bestDx) {
        bestDx = dx;
        snappedX = clampNonNeg(target);
        xHit = "left";
      }
    }
    for (const target of [edge.left, edge.right]) {
      const origin = target - w;
      const dx = Math.abs(movingRight - target);
      if (dx <= thr && dx < bestDx) {
        bestDx = dx;
        snappedX = clampNonNeg(origin);
        xHit = "right";
      }
    }
    for (const target of [edge.top, edge.bottom]) {
      const dy = Math.abs(movingTop - target);
      if (dy <= thr && dy < bestDy) {
        bestDy = dy;
        snappedY = clampNonNeg(target);
        yHit = "top";
      }
    }
    for (const target of [edge.top, edge.bottom]) {
      const origin = target - h;
      const dy = Math.abs(movingBottom - target);
      if (dy <= thr && dy < bestDy) {
        bestDy = dy;
        snappedY = clampNonNeg(origin);
        yHit = "bottom";
      }
    }
  }

  if (xHit) {
    x = snappedX;
    hit.push(xHit);
  }
  if (yHit) {
    y = snappedY;
    hit.push(yHit);
  }
  return { x, y, hit };
}

/**
 * Edge snap first, then grid snap. Grid runs on the edge-adjusted point.
 */
export function snapLayout(point, size, siblings = [], options = {}) {
  const thr = options.threshold ?? DEFAULT_SNAP.threshold;
  const grid = {
    gridX: options.gridX ?? DEFAULT_SNAP.gridX,
    gridY: options.gridY ?? DEFAULT_SNAP.gridY,
  };
  const edged = snapToEdges(point, size, siblings, thr);
  const gridded = snapToGrid({ x: edged.x, y: edged.y }, grid);
  return {
    x: gridded.x,
    y: gridded.y,
    hit: edged.hit,
  };
}
