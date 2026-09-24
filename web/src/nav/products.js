export const PRODUCTS = [
  { id: "p1", name: "星域", en: "Nexus Tenant", mark: "域", home: "/p1" },
  { id: "p2", name: "星权", en: "Nexus IAM", mark: "权", home: "/p2" },
  { id: "p3", name: "星构", en: "Nexus Studio", mark: "构", home: "/p3" },
  { id: "p4", name: "星市", en: "Nexus Market", mark: "市", home: "/p4" },
  { id: "p5", name: "星联", en: "Nexus Connect", mark: "联", home: "/p5" },
];

export function productById(id) {
  return PRODUCTS.find((item) => item.id === id) || null;
}
