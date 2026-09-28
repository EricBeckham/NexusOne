package one.nexus.bootstrap.demo;

/** PLX2. Startup order for the new-model demo seeds. Lower runs first. */
public final class Plx2DemoSeedOrder {
    /** Tenant row, then PLA8 organization, then PLB9 catalog and subscription. */
    public static final int PATH = 1000;
    /**
     * PLC9 portal menu and home modules. That runner is not on this revision; when it lands it must use this
     * value so the menu is written after the leave application is subscribed.
     */
    public static final int PORTAL = 1200;

    private Plx2DemoSeedOrder() {}
}
