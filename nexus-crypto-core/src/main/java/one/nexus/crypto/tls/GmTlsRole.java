package one.nexus.crypto.tls;

/** Dual-certificate roles for the G17 GM TLS leftover. */
public enum GmTlsRole {
    CA,
    SERVER_SIGN,
    SERVER_ENCRYPT,
    CLIENT
}
