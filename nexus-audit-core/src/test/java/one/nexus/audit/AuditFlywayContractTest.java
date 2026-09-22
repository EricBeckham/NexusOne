package one.nexus.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AuditFlywayContractTest {
    @Test
    void v501IsAppendOnlyAndOutsideLedgerSchemas() throws IOException {
        Path file = Path.of("src/main/resources/db/migration/V501__audit_hash_chain.sql");
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file).toLowerCase(Locale.ROOT);
        assertTrue(text.contains("platform.audit_record"));
        assertTrue(text.contains("revoke update, delete"));
        assertFalse(text.contains("create table business_data."));
        assertFalse(text.contains("create table l_"));
        assertFalse(text.contains("create table a_"));
    }

    @Test
    void v502AddsExportCategoryAndAppendOnlyArchiveCopy() throws IOException {
        Path file = Path.of("src/main/resources/db/migration/V502__audit_export_and_archive.sql");
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file).toLowerCase(Locale.ROOT);
        assertTrue(text.contains("export"));
        assertTrue(text.contains("platform.audit_record_archive"));
        assertTrue(text.contains("revoke update, delete"));
        assertFalse(text.contains("delete from platform.audit_record"));
        assertFalse(text.contains("create table business_data."));
        assertFalse(text.contains("create table l_"));
        assertFalse(text.contains("create table a_"));
    }

    @Test
    void v503GrantsArchiveDeleteOnlyAndLeavesHotAppendOnly() throws IOException {
        Path file = Path.of("src/main/resources/db/migration/V503__audit_archive_expiry.sql");
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String text = Files.readString(file).toLowerCase(Locale.ROOT);
        assertTrue(text.contains("grant delete on table platform.audit_record_archive"));
        assertFalse(text.contains("delete from platform.audit_record"));
        assertFalse(text.contains("grant delete on table platform.audit_record "));
        assertFalse(text.contains("grant delete on platform.audit_record"));
        assertFalse(text.contains("create table business_data."));
        assertFalse(text.contains("create table l_"));
        assertFalse(text.contains("create table a_"));
    }
}
