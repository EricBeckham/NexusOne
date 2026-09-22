/**
 * In-memory window double for HostBridgePort unit tests, assert:h5-host,
 * and the APP WebView web-harness walk. Not a browser. No Playwright.
 * Not a native APP binary.
 */
export function createFakeWindow({
  embedded = false,
  appWebView = false,
  origin = "http://127.0.0.1:5173",
  search = "",
} = {}) {
  const listeners = [];
  const parentInbox = [];
  const selfInbox = [];
  const appInbox = [];
  const resolvedSearch = search || (embedded || appWebView ? "?embedded=true" : "");
  const parent = {
    postMessage(data, targetOrigin) {
      parentInbox.push({ data, targetOrigin });
    },
  };
  const win = {
    location: {
      origin,
      search: resolvedSearch,
      href: `${origin}/${resolvedSearch}`,
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
  win.__appWebViewInbox = appInbox;
  win.__listeners = listeners;
  if (appWebView) {
    win.NexusAppWebView = {
      kind: "app-webview-sim",
      nativeBinary: false,
      postMessage(data, targetOrigin) {
        appInbox.push({ data, targetOrigin });
      },
    };
  }
  win.__dispatch = (event) => {
    for (const fn of [...listeners]) {
      fn(event);
    }
  };
  return win;
}
