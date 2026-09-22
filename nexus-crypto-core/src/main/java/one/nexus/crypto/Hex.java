package one.nexus.crypto;

import java.util.Locale;

public final class Hex {
    private Hex() {}

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte value : data) {
            out.append(String.format("%02x", value));
        }
        return out.toString();
    }

    public static byte[] decode(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex is required");
        }
        String clean = hex.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if ((clean.length() & 1) != 0) {
            throw new IllegalArgumentException("hex length must be even");
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(clean.charAt(i * 2), 16);
            int lo = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
