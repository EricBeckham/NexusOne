import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class PocTest {
    private static int checks;
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
    private static void denied(Runnable action) {
        checks++;
        try { action.run(); } catch (SecurityException expected) { return; }
        throw new AssertionError("expected SecurityException");
    }
    public static void main(String[] args) {
        Poc.TableRegistry registry = new Poc.TableRegistry();
        registry.register("ledger_entry", Poc.Scope.TENANT, "id", "tenant_id", "title", "deleted_at");
        registry.register("platform_metric", Poc.Scope.PLATFORM, "id", "count");
        final Poc.Executor executor = new Poc.Executor(registry, new LinkedHashSet<String>(Arrays.asList("ops.summary")));
        final Poc.TenantContext alpha = new Poc.TenantContext("alpha");
        final List<String> audit = new ArrayList<String>();
        Poc.PlatformContextIssuer issuer = new Poc.PlatformContextIssuer(Collections.singleton("ops.summary"), audit::add);

        Poc.RenderedSql read = executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("id", "title"), map("deleted_at", null)), alpha);
        equal("SELECT \"id\", \"title\" FROM \"ledger_entry\" WHERE \"deleted_at\" IS NULL AND \"tenant_id\" = ?", read.sql);
        equal(Arrays.asList("alpha"), read.parameters);
        equal(true, read.plan.tenantRequired);
        equal("TenantContext", read.plan.tenantSource);

        Poc.RenderedSql inserted = executor.compile(Poc.Query.insert("ledger_entry", map("id", "1", "title", "hi")), alpha);
        equal("INSERT INTO \"ledger_entry\" (\"id\", \"title\", \"tenant_id\") VALUES (?, ?, ?)", inserted.sql);
        equal(Arrays.asList("1", "hi", "alpha"), inserted.parameters);
        Poc.RenderedSql updated = executor.compile(Poc.Query.update("ledger_entry", map("title", "new"), map("id", "1")), alpha);
        equal("UPDATE \"ledger_entry\" SET \"title\" = ? WHERE \"id\" = ? AND \"tenant_id\" = ?", updated.sql);
        equal(Arrays.asList("new", "1", "alpha"), updated.parameters);
        Poc.RenderedSql deleted = executor.compile(Poc.Query.delete("ledger_entry", map("id", "1")), alpha);
        equal("DELETE FROM \"ledger_entry\" WHERE \"id\" = ? AND \"tenant_id\" = ?", deleted.sql);

        denied(() -> executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("id"), map()), null));
        denied(() -> executor.compile(Poc.Query.insert("ledger_entry", map("id", "1", "tenant_id", "beta")), alpha));
        denied(() -> executor.compile(Poc.Query.update("ledger_entry", map("title", "x"), map("id", "1", "tenant_id", "beta")), alpha));
        denied(() -> executor.compile(Poc.Query.delete("ledger_entry", map("id", "1", "tenant_id", "beta")), alpha));
        denied(() -> executor.compile(Poc.Query.update("ledger_entry", map("tenant_id", "beta"), map("id", "1")), alpha));
        denied(() -> executor.compile(Poc.Query.select("platform_metric", Arrays.asList("id"), map()), alpha));
        denied(() -> executor.compile(Poc.Query.select("unknown_table", Arrays.asList("id"), map()), alpha));
        denied(() -> executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("bad_column"), map()), alpha));
        denied(() -> executor.compile(Poc.Query.select("ledger_entry;drop table ledger_entry", Arrays.asList("id"), map()), alpha));
        denied(() -> issuer.issue("ops.other", "admin", "report", "t-1"));
        denied(() -> issuer.issue("ops.summary", "", "report", "t-1"));

        Poc.PlatformContext platform = issuer.issue("ops.summary", "admin", "operations report", "t-1");
        equal(1, audit.size());
        Poc.RenderedSql summary = executor.compile(Poc.Query.select("ledger_entry", Arrays.asList("id"), map()), platform);
        equal("SELECT \"id\" FROM \"ledger_entry\"", summary.sql);
        equal(true, summary.plan.platformContext);
        equal("SELECT \"id\" FROM \"platform_metric\"", executor.compile(Poc.Query.select("platform_metric", Arrays.asList("id"), map()), platform).sql);
        Poc.NativeSqlPort nativePort = new Poc.NativeSqlPort(executor, Collections.singletonMap("infra.count-ledger", "ledger_entry"));
        Poc.RenderedSql nativeCount = nativePort.compileCount("infra.count-ledger", alpha);
        equal("SELECT count(*) FROM \"ledger_entry\" WHERE \"tenant_id\" = ?", nativeCount.sql);
        equal(Arrays.asList("alpha"), nativeCount.parameters);
        equal(true, nativeCount.plan.tenantRequired);
        denied(() -> nativePort.compileCount("business.raw-sql", alpha));
        denied(() -> nativePort.compileCount("infra.count-ledger", null));
        equal("SELECT count(*) FROM \"ledger_entry\"", nativePort.compileCount("infra.count-ledger", platform).sql);
        System.out.println("PASS " + checks + " isolation assertions");
    }
}
