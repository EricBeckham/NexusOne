/**
 * In-memory window double for HostBridgePort unit tests and assert:h5-host.
 * Not a browser. No Playwright.
 */
export function createFakeWindow({
  embedded = false,
  origin = "http://127.0.0.1:5173",
  search = "",
} = {}) {
  const listeners = [];
  const parentInbox = [];
  const selfInbox = [];
  const parent = {
    postMessage(data, targetOrigin) {
      parentInbox.push({ data, targetOrigin });
    },
  };
  const win = {
    location: {
      origin,
      search: search || (embedded ? "?embedded=true" : ""),
      href: `${origin}/${search || (embedded ? "?embedded=true" : "")}`,
    },
    history: { length: 2 },
    addEventListener(type, fn) {
      if (type === "message") {
        listeners.push(fn);
      }
    },
    removeEventListener(type, fn) {
      const index = listeners.indexOf(fn);
      if (index >= 0) {
        listeners.splice(index, 1);
      }
    },
    postMessage(data, targetOrigin) {
      selfInbox.push({ data, targetOrigin });
      const event = { origin, data, source: win };
      for (const fn of [...listeners]) {
        fn(event);
      }
    },
  };
  win.parent = embedded ? parent : win;
  win.__parentInbox = parentInbox;
  win.__selfInbox = selfInbox;
  win.__listeners = listeners;
  win.__dispatch = (event) => {
    for (const fn of [...listeners]) {
      fn(event);
    }
  };
  return win;
}
