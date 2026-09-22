package one.nexus.p3.domain;

import java.util.List;

/**
 * Fixed columns on every physical ledger table (tech framework §19.2) plus
 * {@code owner_id}/{@code department_id} required by M0-05 DataScope.
 */
public final class SystemColumns {
    public static final String ID = "id";
    public static final String TENANT_ID = "tenant_id";
    public static final String OWNER_ID = "owner_id";
    public static final String DEPARTMENT_ID = "department_id";
    public static final String CREATED_AT = "created_at";
    public static final String CREATED_BY = "created_by";
    public static final String UPDATED_AT = "updated_at";
    public static final String UPDATED_BY = "updated_by";
    public static final String DELETED_AT = "deleted_at";
    public static final String VERSION = "version";
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String EXT_DATA = "ext_data";

    public static final List<String> ALL = List.of(
            ID, TENANT_ID, OWNER_ID, DEPARTMENT_ID,
            CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY,
            DELETED_AT, VERSION, SCHEMA_VERSION, EXT_DATA);

    private SystemColumns() {}
}
