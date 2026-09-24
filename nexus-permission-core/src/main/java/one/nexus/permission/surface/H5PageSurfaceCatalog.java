package one.nexus.permission.surface;

import java.util.List;

/**
 * Honesty inventory: which Vue {@code /m/*} pages already interpret
 * {@link SurfaceGuard} (via {@code useH5Surface} / {@code H5SurfaceGuard})
 * versus which pages stay open.
 *
 * <p>#116 wired list / detail / form / todo (and query) through the same
 * engine. Home, files, notify, login, callback, host, and harness pages
 * are <em>not</em> on that leftover. This catalog does not put them on
 * SurfaceGuard. It does not claim product-wide G2.
 *
 * <p>Does not redo #116 H5 {@code /check}, #150 todo HTTP, #144
 * {@code tenant_only}/catalog, or #137 physical dump. AuditPort same-TX
 * is unchanged. Ops {@code pg_dump} stays the sibling leftover
 * {@link OpsPhysicalDumpCatalog}.
 */
public final class H5PageSurfaceCatalog {
    public enum Coverage {
        COVERED,
        OPEN
    }

    public record Page(String path, Coverage coverage, String vueFile, String cell, String reason) {}

    public static final String G2_CLOSE = "NOT_CLAIMED";
    public static final String BOUNDARY =
            "H5 every page is not on SurfaceGuard; list/detail/form/todo/query are covered (#116); "
                    + "home/files/notify/login/host/harness stay open";
    public static final String ROUTER = "web/src/router.js";
    public static final String VUE_DIR = "web/src/mobile/products/p3-studio/pages";
    public static final String JS_INVENTORY = "web/src/mobile/permission/h5PageInventory.js";
    public static final String DOC = "docs/g2-h5-every-page.md";

    public static final List<Page> PAGES = List.of(
            page("/m", Coverage.OPEN, "", "", "redirect only"),
            page("/m/login", Coverage.OPEN, "LoginPage.vue", "", "public auth; G15 leftover"),
            page("/m/callback", Coverage.OPEN, "CallbackPage.vue", "", "public auth; G15 leftover"),
            page("/m/host", Coverage.OPEN, "HostMessagePage.vue", "", "F014 host; G15 leftover"),
            page(
                    "/m/host-harness",
                    Coverage.OPEN,
                    "HostHarnessPage.vue",
                    "",
                    "acceptance harness; G15 leftover"),
            page(
                    "/m/mp-webview-harness",
                    Coverage.OPEN,
                    "MiniProgramWebViewHarnessPage.vue",
                    "",
                    "simulated 小程序 harness; G15 leftover"),
            page(
                    "/m/app-webview-harness",
                    Coverage.OPEN,
                    "AppWebViewHarnessPage.vue",
                    "",
                    "APP WebView web harness; G15 leftover"),
            page(
                    "/m/wx-dt-harness",
                    Coverage.OPEN,
                    "WeChatDingTalkHarnessPage.vue",
                    "",
                    "WeChat/DingTalk stub; G15 leftover"),
            page(
                    "/m/native-bridge-harness",
                    Coverage.OPEN,
                    "NativeBridgeHarnessPage.vue",
                    "",
                    "native-bridge stub; G15 leftover"),
            page(
                    "/m/wx-dt-prod-gap-harness",
                    Coverage.OPEN,
                    "WeChatDingTalkProdGapHonestyPage.vue",
                    "",
                    "WeChat/DingTalk production-gap honesty harness; G15 leftover"),
            page(
                    "/m/native-bridge-honesty-harness",
                    Coverage.OPEN,
                    "NativeBridgeHonestyPage.vue",
                    "",
                    "native-bridge honesty harness; G15 leftover"),
            page("/m/p3/home", Coverage.OPEN, "HomePage.vue", "", "session home; no SurfaceGuard"),
            page("/m/p3/files", Coverage.OPEN, "FilesPage.vue", "", "file service; no SurfaceGuard"),
            page("/m/p3/files/:id", Coverage.OPEN, "FileDetailPage.vue", "", "file detail; no SurfaceGuard"),
            page(
                    "/m/p3/forms",
                    Coverage.COVERED,
                    "FormsPage.vue",
                    "form/read",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/forms/:id",
                    Coverage.COVERED,
                    "FormFillPage.vue",
                    "form/read + form/write",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/lists",
                    Coverage.COVERED,
                    "ListsPage.vue",
                    "ledger/read",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/lists/:id",
                    Coverage.COVERED,
                    "ListRecordsPage.vue",
                    "ledger/read",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/lists/:id/records/:recordId",
                    Coverage.COVERED,
                    "RecordDetailPage.vue",
                    "ledger/read",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/todos",
                    Coverage.COVERED,
                    "TodosPage.vue",
                    "process.task/read",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/todos/:id",
                    Coverage.COVERED,
                    "TodoDetailPage.vue",
                    "process.task/read + approve/reject + process.instance/withdraw",
                    "#116 H5 SurfaceGuard"),
            page(
                    "/m/p3/query",
                    Coverage.COVERED,
                    "QueryPage.vue",
                    "ledger/read",
                    "#116 H5 SurfaceGuard; export button is #124 ReportSurface"),
            page(
                    "/m/p3/notifications",
                    Coverage.OPEN,
                    "NotifyPage.vue",
                    "",
                    "notify jump; no SurfaceGuard"),
            page(
                    "/m/p4/market",
                    Coverage.OPEN,
                    "MarketPage.vue",
                    "",
                    "P4 catalog shell; catalog permission cell is not defined yet"),
            page(
                    "/m/p5/connectors",
                    Coverage.OPEN,
                    "ConnectPage.vue",
                    "",
                    "P5 self-use integration shell; permission cell is not defined yet"));

    private H5PageSurfaceCatalog() {}

    public static List<String> coveredPaths() {
        return PAGES.stream()
                .filter(page -> page.coverage() == Coverage.COVERED)
                .map(Page::path)
                .toList();
    }

    public static List<String> openPaths() {
        return PAGES.stream()
                .filter(page -> page.coverage() == Coverage.OPEN)
                .map(Page::path)
                .toList();
    }

    private static Page page(String path, Coverage coverage, String vueFile, String cell, String reason) {
        return new Page(path, coverage, vueFile, cell, reason);
    }
}
