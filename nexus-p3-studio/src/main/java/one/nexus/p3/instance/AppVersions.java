package one.nexus.p3.instance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MAJOR.MINOR.PATCH. A non-matching string is not comparable. */
public final class AppVersions {
    private static final Pattern SEMVER = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private AppVersions() {}

    public static boolean valid(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    /** True when {@code platform} is a newer MAJOR.MINOR.PATCH than {@code base}. */
    public static boolean newer(String platform, String base) {
        int[] left = parse(platform);
        int[] right = parse(base);
        if (left == null || right == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) {
                return left[i] > right[i];
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = SEMVER.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        return new int[] {
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        };
    }
}
