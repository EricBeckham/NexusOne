export const products = Object.freeze([
  Object.freeze({ id: "p1", name: "星域", summary: "租户与产品边界", path: "/p1", color: "#246bfd" }),
  Object.freeze({ id: "p2", name: "星权", summary: "身份、组织与权限", path: "/p2", color: "#6d5cf6" }),
  Object.freeze({ id: "p3", name: "星构", summary: "台账、表单与流程", path: "/p3", color: "#2475ff" }),
]);

export function productById(id) {
  return products.find((item) => item.id === id) || products[0];
}
