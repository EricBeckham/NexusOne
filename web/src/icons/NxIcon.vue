<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    class="nx-icon"
  >
    <path :d="paths[name] || paths.grid" />
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, default: "grid" },
  size: { type: Number, default: 20 },
});

const paths = {
  home: "m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
  chevronLeft: "m15 18-6-6 6-6",
  chevronRight: "m9 18 6-6-6-6",
  search: "m20 20-4.5-4.5m2-5.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0z",
  building: "M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M2 21h20M8 7h2m-2 4h2m-2 4h2m4-8h6v14M18 12h.01M18 16h.01",
  users: "M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2m6-10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm6-7.5a4 4 0 0 1 0 7.75M20 21v-2a4 4 0 0 0-3-3.87",
  userPlus: "M15 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2m6-10a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm11-5v6m-3-3h6",
  network: "M12 3v5m-7 8v-4h14v4m-7-4v9M3 21h4v-5H3zm14 0h4v-5h-4zM10 3h4v5h-4z",
  gauge: "M20.5 15a9 9 0 1 0-17 0M12 13l4-4m-8 9h8",
  server: "M4 4h16v6H4zm0 10h16v6H4zm3-7h.01M7 17h.01M11 7h6m-6 10h6",
  shield: "M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11zm-3-11 2 2 4-4",
  briefcase: "M3 8h18v12H3zm5 0V5h8v3m-13 5h18m-11 0v2h4v-2",
  key: "M21 2l-2 2m-7.6 7.6a5 5 0 1 1-7.1 7.1 5 5 0 0 1 7.1-7.1zm0 0L17 6l3 3-2 2m-4-4 2 2",
  phone: "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm4 16h2",
  mail: "M3 5h18v14H3zm0 1 9 7 9-7",
  table: "M3 4h18v16H3zm0 5h18M9 4v16m6-16v16",
  file: "M6 2h8l5 5v15H6zm8 0v6h5m-9 4h5m-5 4h5",
  check: "M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
  chart: "M3 3v18h18M8 16l4-5 4 3 5-7",
  archive: "M3 4h18v4H3zm2 4v12h14V8m-9 4h4",
  download: "M12 3v12m-5-5 5 5 5-5M4 20h16",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 12h4",
  workflow: "M5 4h5v5H5zm9 11h5v5h-5zM7.5 9v3a3 3 0 0 0 3 3H14m-2-2 2 2-2 2",
  upload: "M12 16V4m-5 5 5-5 5 5M4 20h16",
  grid: "M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z",
  package: "m12 3 9 5-9 5-9-5zm-9 5v9l9 5 9-5V8m-9 5v9",
  tag: "M20 13l-7 7-10-10V3h7zm-11-5h.01",
  card: "M3 5h18v14H3zm0 5h18m-14 5h4",
  arrowUp: "M12 19V5m-6 6 6-6 6 6M4 21h16",
  gift: "M3 8h18v4H3zm2 4v9h14v-9m-7-4v13M12 8H7a2 2 0 1 1 2-2c1 0 3 2 3 2zm0 0h5a2 2 0 1 0-2-2c-1 0-3 2-3 2z",
  store: "M3 10v11h18V10M2 3h20l-2 7H4zm6 18v-7h8v7M2 10a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0",
  plug: "M9 7V3m6 4V3M6 7h12v4a6 6 0 0 1-12 0zm6 10v4",
  code: "m8 8-5 4 5 4m8-8 5 4-5 4m-3-11-2 14",
  webhook: "M18 8a3 3 0 1 0-3-3m0 14a3 3 0 1 0 3-3M6 15a3 3 0 1 0 0-6m3-2 5-2m-5 13 5 2m-5-7h6",
  clock: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zm0-16v6l4 2",
  layers: "m12 3 9 5-9 5-9-5zm-9 9 9 5 9-5m-18 5 9 5 9-5",
};
</script>
