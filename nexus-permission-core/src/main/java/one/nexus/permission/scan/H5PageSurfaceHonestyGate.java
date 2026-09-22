package one.nexus.permission.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import one.nexus.permission.surface.H5PageSurfaceCatalog;
import one.nexus.permission.surface.H5PageSurfaceCatalog.Coverage;
import one.nexus.permission.surface.H5PageSurfaceCatalog.Page;

/**
 * Static honesty gate: every Vue {@code /m/*} route is classified as
 * SurfaceGuard-covered or still open.
 *
 * <p>#116 list/detail/form/todo/query pages must keep {@code useH5Surface}.
 * Home / files / notify / login / host / harness stay open and must not
 * claim that leftover. A new {@code /m/*} route without a catalog row
 * fails. Claiming product G2 closed fails.
 *
 * <p>Does not redo #116 H5 {@code /check}, #150 todo HTTP, #144 catalog /
 * {@code tenant_only}, or #137 physical dump. Does not change AuditPort.
 */
public final class H5PageSurfaceHonestyGate {
    public static final String UNCLASSIFIED_ROUTE = "unclassified-m-route";
    public static final String CATALOG_ROUTE_MISSING = "catalog-route-missing";
    public static final String COVERED_MISSING_GUARD = "covered-missing-guard";
    public static final String OPEN_CLAIMS_GUARD = "open-claims-guard";
    public static final String G2_CLOSE_CLAIMED = "g2-close-claimed";
    public static final String OPEN_PAGES_EMPTY = "open-pages-empty";
    public static final String VUE_MISMATCH = "router-vue-mismatch";

    static final Pattern ROUTE_PATH = Pattern.compile("path:\\s*\"(/m[^\"]*)\"");
    static final Pattern VUE_IMPORT = Pattern.compile("pages/([A-Za-z0-9]+\\.vue)");
    static final Pattern CLOSED_CLAIM = Pattern.compile(
            "g2_close\\s*=\\s*CLOSED|closes product G2|product G2 closed|every /m page is on SurfaceGuard",
            Pattern.CASE_INSENSITIVE);

    public record Violation(Path file, String rule, String detail) {}

    public List<Violation> scanProduct(Path repoRoot) throws IOException {
        Path root = requireRepo(repoRoot);
        List<Violation> violations = new ArrayList<>();
        if (H5PageSurfaceCatalog.openPaths().isEmpty()) {
            violations.add(new Violation(
                    root.resolve(H5PageSurfaceCatalog.DOC),
                    OPEN_PAGES_EMPTY,
                    "catalog must keep at least one open /m page; every-page is not closed"));
        }
        if (!"NOT_CLAIMED".equals(H5PageSurfaceCatalog.G2_CLOSE)) {
            violations.add(new Violation(
                    root.resolve(H5PageSurfaceCatalog.DOC),
                    G2_CLOSE_CLAIMED,
                    "H5PageSurfaceCatalog.G2_CLOSE must stay NOT_CLAIMED"));
        }
        Path router = root.resolve(H5PageSurfaceCatalog.ROUTER);
        if (!Files.isRegularFile(router)) {
            violations.add(new Violation(router, CATALOG_ROUTE_MISSING, "web/src/router.js is missing"));
            return List.copyOf(violations);
        }
        Map<String, String> routes = parseRouter(read(router));
        inspectRoutes(root, router, routes, violations);
        inspectDocs(root, violations);
        inspectJsInventory(root, violations);
        return List.copyOf(violations);
    }

    /**
     * Scan a fixture tree. A fake {@code router.js} is compared to the
     * product catalog. A closed-claim file fails even without a router.
     */
    public List<Violation> scan(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("SourceRoot missing: " + sourceRoot);
        }
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<Violation> violations = new ArrayList<>();
        scanClosedClaims(root, violations);
        Path router = root.resolve("router.js");
        if (Files.isRegularFile(router)) {
            Map<String, String> routes = parseRouter(read(router));
            for (String path : routes.keySet()) {
                if (H5PageSurfaceCatalog.PAGES.stream().noneMatch(page -> page.path().equals(path))) {
                    violations.add(new Violation(
                            router,
                            UNCLASSIFIED_ROUTE,
                            "router /m path is not in H5PageSurfaceCatalog: " + path));
                }
            }
        }
        return List.copyOf(violations);
    }

    public static Path findRepoRoot(Path start) {
        return SurfaceGuardBypassGate.findRepoRoot(start);
    }

    static Map<String, String> parseRouter(String text) {
        Map<String, String> routes = new LinkedHashMap<>();
        Matcher matcher = ROUTE_PATH.matcher(text);
        while (matcher.find()) {
            String path = matcher.group(1);
            int end = Math.min(text.length(), matcher.end() + 500);
            String window = text.substring(matcher.start(), end);
            Matcher vue = VUE_IMPORT.matcher(window);
            routes.put(path, vue.find() ? vue.group(1) : "");
        }
        return routes;
    }

    private static Path requireRepo(Path repoRoot) {
        Path root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("nexus-permission-core"))
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException("not a NexusOne repo root: " + root);
        }
        return root;
    }

    private void inspectRoutes(
            Path root, Path router, Map<String, String> routes, List<Violation> violations) {
        for (String path : routes.keySet()) {
            if (H5PageSurfaceCatalog.PAGES.stream().noneMatch(page -> page.path().equals(path))) {
                violations.add(new Violation(
                        router, UNCLASSIFIED_ROUTE, "new /m route must be classified covered or open: " + path));
            }
        }
        for (Page page : H5PageSurfaceCatalog.PAGES) {
            if (!routes.containsKey(page.path())) {
                violations.add(new Violation(
                        router, CATALOG_ROUTE_MISSING, "catalog path missing from router.js: " + page.path()));
                continue;
            }
            String vueInRouter = routes.get(page.path());
            if (!page.vueFile().isEmpty() && !page.vueFile().equals(vueInRouter)) {
                violations.add(new Violation(
                        router,
                        VUE_MISMATCH,
                        page.path() + " expected " + page.vueFile() + " but router imports " + vueInRouter));
            }
            if (page.vueFile().isEmpty()) {
                continue;
            }
            Path vue = root.resolve(H5PageSurfaceCatalog.VUE_DIR).resolve(page.vueFile());
            if (!Files.isRegularFile(vue)) {
                violations.add(new Violation(vue, CATALOG_ROUTE_MISSING, "Vue page missing for " + page.path()));
                continue;
            }
            String content = read(vue);
            boolean guarded = content.contains("useH5Surface") && content.contains("H5_SURFACES");
            if (page.coverage() == Coverage.COVERED && !guarded) {
                violations.add(new Violation(
                        vue,
                        COVERED_MISSING_GUARD,
                        page.path() + " is catalogued COVERED and must keep useH5Surface / H5_SURFACES"));
            }
            if (page.coverage() == Coverage.OPEN && guarded) {
                violations.add(new Violation(
                        vue,
                        OPEN_CLAIMS_GUARD,
                        page.path() + " is catalogued OPEN; reclassify before adding useH5Surface"));
            }
        }
    }

    private void inspectDocs(Path root, List<Violation> violations) {
        Path doc = root.resolve(H5PageSurfaceCatalog.DOC);
        if (!Files.isRegularFile(doc)) {
            violations.add(new Violation(doc, CATALOG_ROUTE_MISSING, "honesty leftover doc is missing"));
            return;
        }
        String text = read(doc);
        if (CLOSED_CLAIM.matcher(text).find()) {
            violations.add(new Violation(doc, G2_CLOSE_CLAIMED, "docs must not claim product G2 closed"));
        }
        if (!text.contains("NOT_CLAIMED") && !text.contains("不宣称产品")) {
            violations.add(new Violation(doc, G2_CLOSE_CLAIMED, "docs must keep an honest NOT_CLAIMED leftover"));
        }
    }

    private void inspectJsInventory(Path root, List<Violation> violations) {
        Path inventory = root.resolve(H5PageSurfaceCatalog.JS_INVENTORY);
        if (!Files.isRegularFile(inventory)) {
            violations.add(new Violation(inventory, CATALOG_ROUTE_MISSING, "JS inventory twin is missing"));
            return;
        }
        String text = read(inventory);
        if (CLOSED_CLAIM.matcher(text).find() || text.contains("g2Close: \"CLOSED\"")) {
            violations.add(new Violation(inventory, G2_CLOSE_CLAIMED, "JS inventory must not claim G2 closed"));
        }
        for (String path : H5PageSurfaceCatalog.coveredPaths()) {
            if (!text.contains("\"" + path + "\"")) {
                violations.add(new Violation(inventory, CATALOG_ROUTE_MISSING, "JS covered list missing " + path));
            }
        }
        for (String path : H5PageSurfaceCatalog.openPaths()) {
            if (!text.contains("\"" + path + "\"")) {
                violations.add(new Violation(inventory, CATALOG_ROUTE_MISSING, "JS open list missing " + path));
            }
        }
    }

    private void scanClosedClaims(Path root, List<Violation> violations) throws IOException {
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".md") || name.endsWith(".js") || name.endsWith(".env") || name.endsWith(".txt"))) {
                    return;
                }
                if (CLOSED_CLAIM.matcher(read(path)).find()) {
                    violations.add(new Violation(path, G2_CLOSE_CLAIMED, "fixture claimed product G2 closed"));
                }
            });
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
