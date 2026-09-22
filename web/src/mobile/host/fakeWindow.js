/**
 * In-memory window double for HostBridgePort unit tests, assert:h5-host,
 * the APP WebView web-harness walk, and the native-bridge stub walk.
 * Not a browser. No Playwright. Not a native APP binary or store package.
 */
export function createFakeWindow({
  embedded = false,
  appWebView = false,
  nativeBridge = false,
  origin = "http://127.0.0.1:5173",
  search = "",
} = {}) {
  const listeners = [];
  const parentInbox = [];
  const selfInbox = [];
  const appInbox = [];
  const nativeInbox = [];
  const resolvedSearch = search || (
    embedded || appWebView || nativeBridge
      ? (nativeBridge ? "?embedded=true&host=native-bridge-stub" : "?embedded=true")
      : ""
  );
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
  win.__nativeInbox = nativeInbox;
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
  if (nativeBridge === "android") {
    win.NexusNativeBridge = {
      kind: "android-jsinterface-stub",
      storeReady: false,
      nativeBinary: false,
      storePackage: false,
      postMessage(data) {
        const parsed = typeof data === "string" ? JSON.parse(data) : data;
        nativeInbox.push({ platform: "android", data: parsed, raw: data });
      },
    };
  } else if (nativeBridge) {
    win.webkit = { messageHandlers: {} };
    win.webkit.messageHandlers.nexusHost = {
      kind: "ios-wk-stub",
      storeReady: false,
      nativeBinary: false,
      storePackage: false,
      postMessage(data) {
        nativeInbox.push({ platform: "ios", data });
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
