package one.nexus.p3.studio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class FormFlywayContractTest {
    @Test
    void v530IsFormMetadataInAllowedWindow() throws IOException {
        Path file = Path.of("src/main/resources/db/migration/V530__form_metadata.sql");
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        String name = file.getFileName().toString();
        int version = Integer.parseInt(name.substring(1, name.indexOf('_')));
        assertTrue(version >= 530 && version <= 549, name + " must be V530-V549");
        String text = Files.readString(file).toLowerCase(Locale.ROOT);
        assertTrue(text.contains("business_meta.form"));
        assertTrue(text.contains("ledger_code"));
        assertTrue(text.contains("field_code"));
        assertFalse(text.contains("create table business_data."));
        assertFalse(text.contains("create table l_"));
        assertFalse(text.contains("create table a_"));
        assertFalse(text.contains("physical_column"));
        assertFalse(text.contains("column_name"));
        assertFalse(text.contains("v500__"));
        assertFalse(text.contains("v501__"));
        assertFalse(text.contains("v510__"));
        assertFalse(text.contains("v520__"));
    }
}
