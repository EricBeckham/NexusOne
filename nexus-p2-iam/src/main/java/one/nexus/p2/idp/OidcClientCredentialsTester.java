package one.nexus.p2.idp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
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

            String credential = new String(secret, StandardCharsets.UTF_8);
            byte[] body = ("grant_type=client_credentials&client_id=" + form(provider.clientId())
                    + "&client_secret=" + form(credential)).getBytes(StandardCharsets.UTF_8);
            try {
                JsonNode tokenResponse = jsonRequest(tokenEndpoint, "POST", body);
                return tokenResponse != null && !tokenResponse.path("access_token").asText("").isBlank();
            } finally {
                java.util.Arrays.fill(body, (byte) 0);
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
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || isCarrierGradeNat(address.getAddress())) return false;
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isCarrierGradeNat(byte[] address) {
        return address.length == 4 && (address[0] & 0xff) == 100 && (address[1] & 0xc0) == 0x40;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        int end = value.length();
        while (end > 1 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
