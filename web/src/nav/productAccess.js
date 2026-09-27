/**
 * PV3: the products a person may enter, as told by GET /api/v1/auth/products (PV2).
 * `allowed` null / not an array means "unknown" (older server, not loaded yet): show everything and let each
 * product's own server check refuse. An empty array means "no product".
 */
import { PRODUCTS, productById } from "./products";

export function visibleProducts(allowed) {
  return Array.isArray(allowed) ? PRODUCTS.filter((item) => allowed.includes(item.id)) : PRODUCTS;
}

export function productAllowed(allowed, productId) {
  return !Array.isArray(allowed) || allowed.includes(productId);
}

/**
 * Navigation decision for a desktop route once the person is signed in.
 * - "/" with exactly one product → go straight to that product's home (a member lands in the P3 workbench);
 * - a product route the person may not enter → the no-access page;
 * - everything else (and unknown lists) → allowed.
 */
export async function productGuard(to, ensureProducts) {
  if (to.meta?.public || to.meta?.harness || to.meta?.mobile) return true;
  const productId = to.meta?.product;
  if (!productId && to.path !== "/") return true;
  const allowed = await ensureProducts();
  if (!Array.isArray(allowed)) return true;
  if (to.path === "/") {
    const only = allowed.length === 1 ? productById(allowed[0]) : null;
    return only ? { path: only.home, replace: true } : true;
  }
  if (productAllowed(allowed, productId)) return true;
  return { path: "/no-access", query: { product: productId } };
}
