package one.nexus.permission.surface;

/**
 * H5 {@code /m/*} list / detail / form / todo cells. Same resource/action
 * pairs as desktop {@link SurfaceGuard} — not a second engine.
 *
 * <p>Path {@code submit-start} stays the G18 walk ({@code process.instance} /
 * {@code start}). H5 form fill still decides {@code form.write} on the page
 * before that walk. Report query/export is the sibling leftover
 * {@code ReportSurfaceCatalog}. Physical dump / raw export is
 * {@code DumpSurfaceCatalog}.
 */
public final class H5SurfaceCatalog {
    public static final String LIST_RESOURCE = "ledger";
    public static final String LIST_READ = "read";
    public static final String LIST_WRITE = "write";

    public static final String FORM_RESOURCE = "form";
    public static final String FORM_READ = "read";
    public static final String FORM_WRITE = "write";

    public static final String TODO_RESOURCE = "process.task";
    public static final String TODO_READ = "read";
    public static final String TODO_APPROVE = "approve";
    public static final String TODO_REJECT = "reject";

    public static final String INSTANCE_RESOURCE = "process.instance";
    public static final String INSTANCE_START = "start";
    public static final String INSTANCE_WITHDRAW = "withdraw";

    private H5SurfaceCatalog() {}
}
