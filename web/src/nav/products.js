export const PRODUCTS = [
  { id: "p1", name: "星域", en: "Nexus Tenant", icon: "building", home: "/p1" },
  { id: "p2", name: "星权", en: "Nexus IAM", icon: "shield", home: "/p2" },
  { id: "p3", name: "星构", en: "Nexus Studio", icon: "layers", home: "/p3" },
  { id: "p4", name: "星市", en: "Nexus Market", icon: "store", home: "/p4" },
  { id: "p5", name: "星联", en: "Nexus Connect", icon: "plug", home: "/p5" },
];

export function productById(id) {
  return PRODUCTS.find((item) => item.id === id) || null;
}
