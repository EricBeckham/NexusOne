import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.UUID;

public final class ContractTest {
    private static int assertions;
    private static void check(boolean ok) { assertions++; if (!ok) throw new AssertionError("contract failed at assertion " + assertions); }
    private static void denied(Throwing action) throws Exception {
        assertions++;
        try { action.run(); } catch (SecurityException expected) { return; }
        throw new AssertionError("expected SecurityException");
    }
    private interface Throwing { void run() throws Exception; }
    private static final class SpyStorage implements Contract.Storage {
        int signs;
        public void put(String b, String k, byte[] x, String c, long p) {}
        public byte[] get(String b, String k) { return new byte[0]; }
        public String presignGet(String b, String k) { signs++; return "https://signed.example/" + k; }
        public void delete(String b, String k) {}
    }
    public static void main(String[] args) throws Exception {
        SpyStorage spy = new SpyStorage();
        Contract.FileService service = new Contract.FileService(spy, "test", Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
        String key = service.key("alpha", "ledger", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        check(key.equals("test/alpha/ledger/2026/09/00000000-0000-0000-0000-000000000001"));
        denied(() -> service.key("../beta", "ledger", UUID.randomUUID()));
        Contract.FileRef file = new Contract.FileRef("alpha", "test-bucket", key, "user1", "record1");
        denied(() -> service.signDownload("beta", "user1", file, (s, r) -> true));
        denied(() -> service.signDownload("alpha", "user2", file, (s, r) -> true));
        denied(() -> service.signDownload("alpha", "user1", file, (s, r) -> false));
        Contract.FileRef badPrefix = new Contract.FileRef("alpha", "test-bucket", "test/beta/ledger/2026/09/x", "user1", "record1");
        denied(() -> service.signDownload("alpha", "user1", badPrefix, (s, r) -> true));
        check(spy.signs == 0);
        check(service.signDownload("alpha", "user1", file, (s, r) -> true).contains(key));
        check(spy.signs == 1);
        System.out.println("PASS " + assertions + " local tenancy/signing assertions");

        String endpoint = System.getenv("S3_ENDPOINT");
        String access = System.getenv("S3_ACCESS_KEY");
        String secret = System.getenv("S3_SECRET_KEY");
        String bucket = System.getenv("S3_TEST_BUCKET");
        if (endpoint == null || access == null || secret == null || bucket == null) {
            System.out.println("SKIP live S3: set S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY, S3_TEST_BUCKET");
            return;
        }
        Contract.Storage storage = new Contract.MinioStorage(endpoint, access, secret);
        byte[] small = "tenant alpha contract".getBytes("UTF-8");
        byte[] large = new byte[11 * 1024 * 1024];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i % 251);
        String smallKey = service.key("alpha", "p006", UUID.randomUUID());
        String largeKey = service.key("alpha", "p006", UUID.randomUUID());
        try {
            storage.put(bucket, smallKey, small, "text/plain", -1);
            check(Arrays.equals(small, storage.get(bucket, smallKey)));
            storage.put(bucket, largeKey, large, "application/octet-stream", 5 * 1024 * 1024);
            check(Arrays.equals(large, storage.get(bucket, largeKey)));
            String signed = storage.presignGet(bucket, smallKey);
            check(signed.contains("X-Amz-Signature="));
            try (InputStream stream = new URL(signed).openStream()) {
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024]; int n;
                while ((n = stream.read(buffer)) != -1) received.write(buffer, 0, n);
                check(Arrays.equals(small, received.toByteArray()));
            }
            System.out.println("PASS " + assertions + " assertions including live upload/download/multipart/presign");
        } finally {
            storage.delete(bucket, smallKey);
            storage.delete(bucket, largeKey);
        }
    }
}
