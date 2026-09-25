package one.nexus.p2.idp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import one.nexus.p2.domain.Model;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Performs a bounded OIDC discovery + client-credentials check without exposing response bodies. */
public final class OidcClientCredentialsTester implements IdpConnectionTester {
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final ObjectMapper mapper;

    public OidcClientCredentialsTester(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean test(Model.IdpConfiguration provider, byte[] secret) {
        if (provider == null || provider.issuer() == null || provider.clientId() == null
                || secret == null || secret.length == 0) return false;
        try {
            URI issuer = URI.create(provider.issuer());
            if (!safeHttpsPublicOrigin(issuer)) return false;
            URI discoveryUri = URI.create(trimSlash(provider.issuer()) + "/.well-known/openid-configuration");
            JsonNode discovery = jsonRequest(discoveryUri, "GET", null);
            if (discovery == null) return false;
            String discoveredIssuer = discovery.path("issuer").asText(null);
            if (discoveredIssuer == null || !trimSlash(provider.issuer()).equals(trimSlash(discoveredIssuer))) return false;
            String tokenEndpointText = discovery.path("token_endpoint").asText(null);
            if (tokenEndpointText == null || tokenEndpointText.isBlank()) return false;
            URI tokenEndpoint = URI.create(tokenEndpointText);
            if (!sameOrigin(issuer, tokenEndpoint) || !safeHttpsPublicOrigin(tokenEndpoint)) return false;

            byte[] encodedSecret = formUrlEncode(secret);
            byte[] prefix = ("grant_type=client_credentials&client_id=" + form(provider.clientId())
                    + "&client_secret=").getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[prefix.length + encodedSecret.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(encodedSecret, 0, body, prefix.length, encodedSecret.length);
            Arrays.fill(encodedSecret, (byte) 0);
            try {
                JsonNode tokenResponse = jsonRequest(tokenEndpoint, "POST", body);
                return tokenResponse != null && !tokenResponse.path("access_token").asText("").isBlank();
            } finally {
                Arrays.fill(body, (byte) 0);
            }
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }
    }

    private JsonNode jsonRequest(URI uri, String method, byte[] requestBody) throws IOException {
        URLConnection raw = uri.toURL().openConnection();
        if (!(raw instanceof HttpsURLConnection connection)) return null;
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        SSLSocketFactory current = connection.getSSLSocketFactory();
        connection.setSSLSocketFactory(new PublicPeerSslSocketFactory(
                current != null ? current : (SSLSocketFactory) SSLSocketFactory.getDefault()));
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestMethod(method);
        try {
            if (requestBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody);
                }
            }
            int status = connection.getResponseCode();
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) return null;
            try (InputStream input = connection.getInputStream()) {
                byte[] response = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.length > MAX_RESPONSE_BYTES) return null;
                return mapper.readTree(response);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean sameOrigin(URI issuer, URI endpoint) {
        return endpoint.getUserInfo() == null && endpoint.getFragment() == null && endpoint.getQuery() == null
                && "https".equalsIgnoreCase(endpoint.getScheme())
                && issuer.getHost() != null && issuer.getHost().equalsIgnoreCase(endpoint.getHost())
                && effectivePort(issuer) == effectivePort(endpoint);
    }

    private static boolean safeHttpsPublicOrigin(URI uri) {
        if (uri == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isNonPublic(address)) return false;
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    static boolean isNonPublic(InetAddress address) {
        if (address == null) return true;
        byte[] raw = address.getAddress();
        if (raw.length == 16 && (isTeredo(raw) || isLocalNat64(raw))) return true;
        if (raw.length == 16 && embedsIpv4(raw)) {
            try {
                return isNonPublic(InetAddress.getByAddress(embeddedIpv4(raw)));
            } catch (UnknownHostException ex) {
                return true;
            }
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || isCarrierGradeNat(raw) || isUniqueLocal(raw) || isSpecialPurposeIpv4(raw)) return true;
        return false;
    }

    /** Closes the connected socket when its peer is not public. The address is not included in the error. */
    static Socket requirePublicPeer(Socket socket) throws IOException {
        if (isNonPublic(socket.getInetAddress())) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The refusal stands even if closing the socket fails.
            }
            throw new IOException("connection target is not a public address");
        }
        return socket;
    }

    private static boolean isCarrierGradeNat(byte[] address) {
        return address.length == 4 && (address[0] & 0xff) == 100 && (address[1] & 0xc0) == 0x40;
    }

    private static boolean isUniqueLocal(byte[] address) {
        return address.length == 16 && (address[0] & 0xfe) == 0xfc;
    }

    /** 0.0.0.0/8 "this network", 192.0.0.0/24 IETF, 198.18.0.0/15 benchmarking, 240.0.0.0/4 reserved + broadcast. */
    private static boolean isSpecialPurposeIpv4(byte[] address) {
        if (address.length != 4) return false;
        int first = address[0] & 0xff;
        int second = address[1] & 0xff;
        return first == 0
                || (first == 192 && second == 0 && (address[2] & 0xff) == 0)
                || (first == 198 && (second & 0xfe) == 18)
                || first >= 240;
    }

    /** IPv6 forms that carry an IPv4 destination: mapped, compatible, NAT64 (RFC 6052 / 8215) and 6to4. */
    private static boolean embedsIpv4(byte[] address) {
        return isMappedIpv4(address) || isCompatibleIpv4(address) || isNat64(address) || is6to4(address);
    }

    private static byte[] embeddedIpv4(byte[] address) {
        return is6to4(address) ? Arrays.copyOfRange(address, 2, 6) : Arrays.copyOfRange(address, 12, 16);
    }

    private static boolean isMappedIpv4(byte[] address) {
        return leadingZeros(address, 10) && address[10] == (byte) 0xff && address[11] == (byte) 0xff;
    }

    private static boolean isCompatibleIpv4(byte[] address) {
        return leadingZeros(address, 12);
    }

    /** 64:ff9b::/96 well-known NAT64 prefix; the IPv4 destination is the last 32 bits. */
    private static boolean isNat64(byte[] address) {
        if (!isNat64Prefix(address)) return false;
        for (int index = 4; index < 12; index++) {
            if (address[index] != 0) return false;
        }
        return true;
    }

    /** 64:ff9b:1::/48 local-use NAT64 (RFC 8215); the IPv4 offset depends on local prefix length. */
    private static boolean isLocalNat64(byte[] address) {
        return isNat64Prefix(address) && address[4] == 0x00 && address[5] == 0x01;
    }

    private static boolean isNat64Prefix(byte[] address) {
        return address[0] == 0x00 && address[1] == 0x64 && address[2] == (byte) 0xff && address[3] == (byte) 0x9b;
    }

    private static boolean is6to4(byte[] address) {
        return address[0] == 0x20 && address[1] == 0x02;
    }

    /** 2001:0000::/32 tunnels to an obfuscated IPv4 server/client; never a plain public IdP origin. */
    private static boolean isTeredo(byte[] address) {
        return address[0] == 0x20 && address[1] == 0x01 && address[2] == 0x00 && address[3] == 0x00;
    }

    private static boolean leadingZeros(byte[] address, int count) {
        if (address.length != 16) return false;
        for (int index = 0; index < count; index++) {
            if (address[index] != 0) return false;
        }
        return true;
    }

    static final class PublicPeerSslSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        PublicPeerSslSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            requirePublicPeer(socket);
            return requirePublicPeer(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return requirePublicPeer(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return requirePublicPeer(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return requirePublicPeer(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return requirePublicPeer(delegate.createSocket(address, port, localAddress, localPort));
        }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** application/x-www-form-urlencoded of raw UTF-8 bytes. Does not allocate a String of the secret. */
    static byte[] formUrlEncode(byte[] raw) {
        byte[] encoded = new byte[raw.length * 3];
        int length = 0;
        for (byte value : raw) {
            int b = value & 0xff;
            if (isFormUnreserved(b)) {
                encoded[length++] = (byte) b;
            } else if (b == ' ') {
                encoded[length++] = '+';
            } else {
                encoded[length++] = '%';
                encoded[length++] = HEX[b >>> 4];
                encoded[length++] = HEX[b & 0x0f];
            }
        }
        byte[] exact = Arrays.copyOf(encoded, length);
        Arrays.fill(encoded, (byte) 0);
        return exact;
    }

    private static boolean isFormUnreserved(int value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '-' || value == '_' || value == '.' || value == '*';
    }

    private static final byte[] HEX = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private static String trimSlash(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
