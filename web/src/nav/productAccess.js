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

/** D6: a tenant member is the person who can enter only P3. They land on the portal, not the studio. */
export function memberPortalHome(allowed) {
  return Array.isArray(allowed) && allowed.length === 1 && allowed[0] === "p3";
}

/**
 * Navigation decision for a desktop route once the person is signed in.
 * - "/" with only P3 → the member portal (D6). Any other single product still goes straight to that product's home;
 * - a product route the person may not enter → the no-access page;
 * - everything else (and unknown lists) → allowed.
 */
export async function productGuard(to, ensureProducts) {
  if (to.meta?.public || to.meta?.harness || to.meta?.mobile) return true;
  const productId = to.meta?.product;
  if (!productId && to.path !== "/") return true;
  const allowed = await ensureProducts();
  if (to.meta?.platformProcess && (!Array.isArray(allowed) || !allowed.includes('p1') || !allowed.includes('p4'))) {
    return { path: '/no-access', query: { product: 'p1' } };
  }
  if (!Array.isArray(allowed)) return true;
  if (to.path === "/") {
    if (memberPortalHome(allowed)) return { path: "/portal", replace: true };
    const only = allowed.length === 1 ? productById(allowed[0]) : null;
    return only ? { path: only.home, replace: true } : true;
  }
  if (productAllowed(allowed, productId)) return true;
  return { path: "/no-access", query: { product: productId } };
}
